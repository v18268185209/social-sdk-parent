^package cn.net.rjnetwork.xianyu.manager.cs.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsKnowledge;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsMessage;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsPolicy;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsSession;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsKnowledgeMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsMessageMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsPolicyMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsSessionMapper;
import cn.net.rjnetwork.xianyu.manager.cs.engine.AiCsEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 客服管理 API（会话、消息、知识库、策略配置）
 */
@RestController
@RequestMapping("/api/ai/cs")
public class AiCsController {

    private final AiCsEngine csEngine;
    private final AiCsSessionMapper sessionMapper;
    private final AiCsMessageMapper messageMapper;
    private final AiCsKnowledgeMapper knowledgeMapper;
    private final AiCsPolicyMapper policyMapper;

    public AiCsController(AiCsEngine csEngine, AiCsSessionMapper sessionMapper,
                          AiCsMessageMapper messageMapper, AiCsKnowledgeMapper knowledgeMapper,
                          AiCsPolicyMapper policyMapper) {
        this.csEngine = csEngine;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.policyMapper = policyMapper;
    }

    // ==================== 消息处理入口 ====================

    /**
     * 处理买家消息（核心入口，由平台消息回调触发）
     * POST /api/ai/cs/message
     * {
     *   "accountId": 1,
     *   "buyerId": "buyer_123",
     *   "buyerNickname": "买家昵称",
     *   "content": "能便宜点吗？",
     *   "modelId": 1
     * }
     */
    @PostMapping("/message")
    public ApiResponse<Map<String, Object>> handleMessage(@RequestBody Map<String, Object> request) {
        Long accountId = Long.valueOf(request.get("accountId").toString());
        String buyerId = (String) request.get("buyerId");
        String buyerNickname = (String) request.get("buyerNickname");
        String content = (String) request.get("content");
        Long modelId = request.get("modelId") != null ? Long.valueOf(request.get("modelId").toString()) : null;

        AiCsMessage reply = csEngine.handleIncomingMessage(accountId, buyerId, buyerNickname, content, modelId);
        return ApiResponse.ok(Map.of(
                "reply", reply.getContent(),
                "intent", reply.getIntent(),
                "aiGenerated", reply.getAiGenerated(),
                "sentBy", reply.getSentBy()
        ));
    }

    // ==================== 会话管理 ====================

    @GetMapping("/sessions")
    public ApiResponse<Page<AiCsSession>> listSessions(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Page<AiCsSession> p = new Page<>(page, size);
        LambdaQueryWrapper<AiCsSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCsSession::getAccountId, accountId);
        if (status != null && !status.isBlank()) wrapper.eq(AiCsSession::getStatus, status);
        wrapper.orderByDesc(AiCsSession::getLastMessageAt);
        return ApiResponse.ok(sessionMapper.selectPage(p, wrapper));
    }

    @PutMapping("/sessions/{id}/close")
    public ApiResponse<Void> closeSession(@PathVariable Long id) {
        AiCsSession session = sessionMapper.selectById(id);
        if (session == null) return ApiResponse.fail("NOT_FOUND", "Session not found");
        session.setStatus("CLOSED");
        sessionMapper.updateById(session);
        return ApiResponse.ok(null);
    }

    @PutMapping("/sessions/{id}/block")
    public ApiResponse<Void> blockSession(@PathVariable Long id) {
        AiCsSession session = sessionMapper.selectById(id);
        if (session == null) return ApiResponse.fail("NOT_FOUND", "Session not found");
        session.setStatus("BLOCKED");
        sessionMapper.updateById(session);
        return ApiResponse.ok(null);
    }

    // ==================== 消息记录 ====================

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<AiCsMessage>> listMessages(@PathVariable Long sessionId) {
        LambdaQueryWrapper<AiCsMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiCsMessage::getSessionId, sessionId).orderByAsc(AiCsMessage::getCreatedAt);
        return ApiResponse.ok(messageMapper.selectList(wrapper));
    }

    /**
     * 运营手动发送/覆盖回复
     * POST /api/ai/cs/sessions/{sessionId}/reply
     * { "content": "好的，给你降 20" }
     */
    @PostMapping("/sessions/{sessionId}/reply")
    public ApiResponse<AiCsMessage> sendReply(@PathVariable Long sessionId, @RequestBody Map<String, String> request) {
        AiCsMessage msg = new AiCsMessage();
        msg.setSessionId(sessionId);
        msg.setDirection("OUTGOING");
        msg.setContent(request.get("content"));
        msg.setAiGenerated(false);
        msg.setSentBy("HUMAN");
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);

        AiCsSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setLastMessageAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        return ApiResponse.ok(msg);
    }

    // ==================== 知识库 ====================

    @GetMapping("/knowledge")
    public ApiResponse<Page<AiCsKnowledge>> listKnowledge(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String category) {
        Page<AiCsKnowledge> p = new Page<>(page, size);
        LambdaQueryWrapper<AiCsKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNull(AiCsKnowledge::getAccountId).or().eq(AiCsKnowledge::getAccountId, accountId));
        if (category != null && !category.isBlank()) wrapper.eq(AiCsKnowledge::getCategory, category);
        wrapper.orderByAsc(AiCsKnowledge::getPriority);
        return ApiResponse.ok(knowledgeMapper.selectPage(p, wrapper));
    }

    @PostMapping("/knowledge")
    public ApiResponse<AiCsKnowledge> createKnowledge(@RequestBody AiCsKnowledge knowledge) {
        knowledge.setCreatedAt(LocalDateTime.now());
        knowledgeMapper.insert(knowledge);
        return ApiResponse.ok(knowledge);
    }

    @PutMapping("/knowledge/{id}")
    public ApiResponse<AiCsKnowledge> updateKnowledge(@PathVariable Long id, @RequestBody AiCsKnowledge knowledge) {
        knowledge.setId(id);
        knowledgeMapper.updateById(knowledge);
        return ApiResponse.ok(knowledge);
    }

    @DeleteMapping("/knowledge/{id}")
    public ApiResponse<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    // ==================== 策略配置 ====================

    @GetMapping("/policy")
    public ApiResponse<AiCsPolicy> getPolicy(@RequestParam Long accountId) {
        return ApiResponse.ok(policyMapper.selectOne(
                new LambdaQueryWrapper<AiCsPolicy>().eq(AiCsPolicy::getAccountId, accountId)));
    }

    @PostMapping("/policy")
    public ApiResponse<AiCsPolicy> savePolicy(@RequestBody AiCsPolicy policy) {
        AiCsPolicy existing = policyMapper.selectOne(
                new LambdaQueryWrapper<AiCsPolicy>().eq(AiCsPolicy::getAccountId, policy.getAccountId()));
        if (existing != null) {
            policy.setId(existing.getId());
            policyMapper.updateById(policy);
        } else {
            policy.setCreatedAt(LocalDateTime.now());
            policyMapper.insert(policy);
        }
        return ApiResponse.ok(policy);
    }
}
