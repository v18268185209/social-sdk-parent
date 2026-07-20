package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiCsSessionVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiCsKnowledgeVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiCsPolicyVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsSessionMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsKnowledgeMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsPolicyMapper;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsSession;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsKnowledge;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

@RestController
@RequestMapping("/openapi/v1/ai/cs")
public class OpenApiAiCsController {

    private final AiCsSessionMapper sessionMapper;
    private final AiCsKnowledgeMapper knowledgeMapper;
    private final AiCsPolicyMapper policyMapper;
    private final OpenAppService openAppService;

    public OpenApiAiCsController(AiCsSessionMapper sessionMapper, AiCsKnowledgeMapper knowledgeMapper,
                                 AiCsPolicyMapper policyMapper, OpenAppService openAppService) {
        this.sessionMapper = sessionMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.policyMapper = policyMapper;
        this.openAppService = openAppService;
    }

    // ---------- 会话 ----------
    @GetMapping("/sessions")
    public OpenApiResponse<List<OpenApiAiCsSessionVO>> listSessions(@RequestParam(required = false) Long accountId) {
        return OpenApiResponse.ok(query(sessionMapper, accountId, OpenApiAiCsSessionVO::new, AiCsSession::getAccountId));
    }

    @GetMapping("/sessions/{id}")
    public OpenApiResponse<OpenApiAiCsSessionVO> getSession(@PathVariable Long id) {
        return OpenApiResponse.ok(detail(sessionMapper, id, OpenApiAiCsSessionVO::new, AiCsSession::getAccountId, "会话不存在"));
    }

    // ---------- 知识库 ----------
    @GetMapping("/knowledge")
    public OpenApiResponse<List<OpenApiAiCsKnowledgeVO>> listKnowledge(@RequestParam(required = false) Long accountId) {
        return OpenApiResponse.ok(query(knowledgeMapper, accountId, OpenApiAiCsKnowledgeVO::new, AiCsKnowledge::getAccountId));
    }

    @GetMapping("/knowledge/{id}")
    public OpenApiResponse<OpenApiAiCsKnowledgeVO> getKnowledge(@PathVariable Long id) {
        return OpenApiResponse.ok(detail(knowledgeMapper, id, OpenApiAiCsKnowledgeVO::new, AiCsKnowledge::getAccountId, "知识条目不存在"));
    }

    // ---------- 策略 ----------
    @GetMapping("/policies")
    public OpenApiResponse<List<OpenApiAiCsPolicyVO>> listPolicies(@RequestParam(required = false) Long accountId) {
        return OpenApiResponse.ok(query(policyMapper, accountId, OpenApiAiCsPolicyVO::new, AiCsPolicy::getAccountId));
    }

    @GetMapping("/policies/{id}")
    public OpenApiResponse<OpenApiAiCsPolicyVO> getPolicy(@PathVariable Long id) {
        return OpenApiResponse.ok(detail(policyMapper, id, OpenApiAiCsPolicyVO::new, AiCsPolicy::getAccountId, "策略不存在"));
    }

    // ---------- 通用工具 ----------
    private <E, V> List<V> query(com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper, Long accountId,
                                 java.util.function.Supplier<V> voCtor, Function<E, Long> accountIdFn) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        return mapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || accountIdFn.apply(e) == null || bound.contains(accountIdFn.apply(e)))
                .filter(e -> accountId == null || (accountIdFn.apply(e) != null && accountIdFn.apply(e).equals(accountId)))
                .map(e -> {
                    V vo = voCtor.get();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
    }

    private <E, V> V detail(com.baomidou.mybatisplus.core.mapper.BaseMapper<E> mapper, Long id,
                           java.util.function.Supplier<V> voCtor, Function<E, Long> accountIdFn, String notFound) {
        OpenApp app = OpenApiContext.getOpenApp();
        E e = mapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, notFound);
        Long aid = accountIdFn.apply(e);
        if (aid != null) openAppService.assertAccountAccessible(app, aid);
        V vo = voCtor.get();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
