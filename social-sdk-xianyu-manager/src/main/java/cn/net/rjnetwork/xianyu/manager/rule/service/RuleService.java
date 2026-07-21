package cn.net.rjnetwork.xianyu.manager.rule.service;

import cn.net.rjnetwork.xianyu.manager.ai.service.AiChatService;
import cn.net.rjnetwork.xianyu.manager.product.service.PolishService;
import cn.net.rjnetwork.xianyu.manager.reply.service.AutoReplyLogService;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleCreateRequest;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleTestRequest;
import cn.net.rjnetwork.xianyu.manager.rule.engine.KeywordRuleEngine;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.AutoReplyConfigMapper;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.RuleMapper;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuAutoReplyConfig;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuKeywordRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuleService {

    private final RuleMapper ruleMapper;
    private final AutoReplyConfigMapper autoReplyConfigMapper;
    private final AiChatService aiChatService;
    private final AutoReplyLogService logService;
    private final PolishService polishService;
    // 内存缓存：accountId -> list of rules
    private final Map<Long, List<XianyuKeywordRule>> ruleCache = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    public RuleService(RuleMapper ruleMapper, AutoReplyConfigMapper autoReplyConfigMapper, AiChatService aiChatService, AutoReplyLogService logService, PolishService polishService) {
        this.ruleMapper = ruleMapper;
        this.autoReplyConfigMapper = autoReplyConfigMapper;
        this.aiChatService = aiChatService;
        this.logService = logService;
        this.polishService = polishService;
    }

    public List<XianyuKeywordRule> listRules(Long accountId) {
        LambdaQueryWrapper<XianyuKeywordRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuKeywordRule::getAccountId, accountId)
               .eq(XianyuKeywordRule::getEnabled, true)
               .orderByAsc(XianyuKeywordRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }

    public List<XianyuKeywordRule> getAllEnabledRules() {
        LambdaQueryWrapper<XianyuKeywordRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuKeywordRule::getEnabled, true)
               .orderByAsc(XianyuKeywordRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }

    @Cacheable(value = "rules", key = "#accountId")
    public List<XianyuKeywordRule> getCachedRules(Long accountId) {
        return listRules(accountId);
    }

    @CacheEvict(value = "rules", key = "#accountId")
    @Transactional
    public XianyuKeywordRule create(RuleCreateRequest request) {
        XianyuKeywordRule rule = new XianyuKeywordRule();
        rule.setAccountId(request.getAccountId());
        rule.setRuleName(request.getRuleName());
        rule.setReplyType(request.getReplyType() != null ? request.getReplyType() : "KEYWORD");
        rule.setKeyword(request.getKeyword());
        rule.setMatchType(request.getMatchType() != null ? request.getMatchType() : "CONTAINS");
        rule.setReplyText(request.getReplyText());
        rule.setEnabled(true);
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 100);
        rule.setAction(request.getAction());
        rule.setActionTargetItemId(request.getActionTargetItemId());
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        // 清除缓存
        ruleCache.remove(request.getAccountId());
        return rule;
    }

    @Transactional
    public XianyuKeywordRule update(Long id, RuleCreateRequest request) {
        XianyuKeywordRule rule = ruleMapper.selectById(id);
        if (rule == null) throw new IllegalArgumentException("Rule not found");
        if (request.getRuleName() != null) rule.setRuleName(request.getRuleName());
        if (request.getReplyType() != null) rule.setReplyType(request.getReplyType());
        if (request.getKeyword() != null) rule.setKeyword(request.getKeyword());
        if (request.getMatchType() != null) rule.setMatchType(request.getMatchType());
        if (request.getReplyText() != null) rule.setReplyText(request.getReplyText());
        if (request.getPriority() != null) rule.setPriority(request.getPriority());
        if (request.getAction() != null) rule.setAction(request.getAction());
        if (request.getActionTargetItemId() != null) rule.setActionTargetItemId(request.getActionTargetItemId());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return rule;
    }

    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        XianyuKeywordRule rule = ruleMapper.selectById(id);
        if (rule == null) throw new IllegalArgumentException("Rule not found");
        rule.setEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
    }

    @Transactional
    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    /**
     * 自动回复：三层匹配逻辑 — keyword → AI → auto
     * 优先级：关键字匹配 > AI 接管 > 兜底自动回复
     */
    public String autoReply(Long accountId, String message) {
        // 1. 关键字词匹配（最高优先级）
        List<XianyuKeywordRule> rules = ruleCache.computeIfAbsent(accountId, k -> listRules(k));
        if (rules == null || rules.isEmpty()) {
            rules = listRules(accountId);
            ruleCache.put(accountId, rules);
        }

        // 先过关键字规则
        for (XianyuKeywordRule rule : rules) {
            if (!"KEYWORD".equals(rule.getReplyType())) continue;
            if (KeywordRuleEngine.testRule(rule.getMatchType(), rule.getKeyword(), message)) {
                logService.log(accountId, rule.getId(), rule.getRuleName(), "KEYWORD", rule.getKeyword(), message, rule.getReplyText(), true);
                // 触发动作：POLISH（擦亮）/ SUPER_POLISH（超级擦亮），异步执行不阻塞回复
                triggerAction(accountId, rule);
                return rule.getReplyText();
            }
        }

        // 2. AI 接管（次优先级）
        XianyuAutoReplyConfig config = getAutoReplyConfig(accountId);
        if (config != null && Boolean.TRUE.equals(config.getAiEnabled())) {
            String aiReply = callAiReply(config, message);
            if (aiReply != null && !aiReply.isEmpty()) {
                logService.log(accountId, null, "AI_REPLY", "AI", null, message, aiReply, true);
                return aiReply;
            }
        }

        // 3. 兜底自动回复（最低优先级）
        if (config != null && Boolean.TRUE.equals(config.getAutoReplyEnabled())
                && config.getFallbackReply() != null && !config.getFallbackReply().isEmpty()) {
            logService.log(accountId, null, "AUTO_FALLBACK", "AUTO", null, message, config.getFallbackReply(), true);
            return config.getFallbackReply();
        }

        // 记录未匹配
        logService.log(accountId, null, null, null, null, message, null, false);
        return null;
    }

    /**
     * 触发规则动作：POLISH（擦亮）/ SUPER_POLISH（超级擦亮）。
     * <p>异步执行，不阻塞回复链路；动作失败只记日志，不影响主回复。</p>
     * <p>actionTargetItemId 为 null 时，跳过动作（防止误擦全架商品），
     * 调用方应在规则配置时显式指定目标 itemId。</p>
     */
    private void triggerAction(Long accountId, XianyuKeywordRule rule) {
        String action = rule.getAction();
        if (action == null || action.isBlank()) return;
        String itemId = rule.getActionTargetItemId();
        if (itemId == null || itemId.isBlank()) {
            log.warn("[RULE] action {} 触发但未配 actionTargetItemId，跳过 (accountId={}, ruleId={})",
                    action, accountId, rule.getId());
            return;
        }
        // 异步执行：擦亮耗时（超级擦亮可达数分钟），不阻塞买家回复
        new Thread(() -> {
            try {
                if ("SUPER_POLISH".equals(action)) {
                    polishService.superPolish(accountId, itemId, 3);
                } else if ("POLISH".equals(action)) {
                    polishService.polish(accountId, itemId);
                } else {
                    log.warn("[RULE] 未知 action {}，跳过 (ruleId={})", action, rule.getId());
                }
            } catch (Exception e) {
                log.warn("[RULE] action {} 执行失败 (accountId={}, itemId={}): {}",
                        action, accountId, itemId, e.getMessage());
            }
        }, "rule-action-" + rule.getId()).start();
    }

    /**
     * 调用 AI 生成回复 — 对接 AiChatService
     * 通过 config.aiModelId 找到模型和厂商，构造 systemPrompt 后发起调用
     */
    private String callAiReply(XianyuAutoReplyConfig config, String message) {
        if (config == null || config.getAiModelId() == null) {
            return null;
        }
        try {
            String systemPrompt = (config.getAiSystemPrompt() != null && !config.getAiSystemPrompt().isBlank())
                    ? config.getAiSystemPrompt()
                    : "你是一个友好、专业的闲鱼卖家客服，请用简洁亲切的语气回复买家。";
            String reply = aiChatService.chat(config.getAiModelId(), systemPrompt, message);
            return (reply != null && !reply.isBlank()) ? reply.trim() : null;
        } catch (Exception e) {
            log.warn("[RuleService] AI reply failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取或创建账号的自动回复配置
     */
    public XianyuAutoReplyConfig getAutoReplyConfig(Long accountId) {
        LambdaQueryWrapper<XianyuAutoReplyConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuAutoReplyConfig::getAccountId, accountId);
        XianyuAutoReplyConfig config = autoReplyConfigMapper.selectOne(wrapper);
        if (config == null) {
            config = new XianyuAutoReplyConfig();
            config.setAccountId(accountId);
            config.setAiEnabled(false);
            config.setAutoReplyEnabled(false);
            config.setAiTemperature(0.7);
            config.setIdleTimeoutMinutes(30);
            config.setNotifyOnNewMessage(true);
            config.setIncludeChatHistory(true);
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            autoReplyConfigMapper.insert(config);
        }
        return config;
    }

    /**
     * 保存自动回复配置
     */
    @Transactional
    public XianyuAutoReplyConfig saveAutoReplyConfig(Long accountId, XianyuAutoReplyConfig formData) {
        LambdaQueryWrapper<XianyuAutoReplyConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuAutoReplyConfig::getAccountId, accountId);
        XianyuAutoReplyConfig existing = autoReplyConfigMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新已有配置
            if (formData.getAiEnabled() != null) existing.setAiEnabled(formData.getAiEnabled());
            if (formData.getAiModelId() != null) existing.setAiModelId(formData.getAiModelId());
            if (formData.getAiSystemPrompt() != null) existing.setAiSystemPrompt(formData.getAiSystemPrompt());
            if (formData.getAiTemperature() != null) existing.setAiTemperature(formData.getAiTemperature());
            if (formData.getAutoReplyEnabled() != null) existing.setAutoReplyEnabled(formData.getAutoReplyEnabled());
            if (formData.getWelcomeMessage() != null) existing.setWelcomeMessage(formData.getWelcomeMessage());
            if (formData.getFallbackReply() != null) existing.setFallbackReply(formData.getFallbackReply());
            if (formData.getIdleTimeoutMinutes() != null) existing.setIdleTimeoutMinutes(formData.getIdleTimeoutMinutes());
            if (formData.getIdleReply() != null) existing.setIdleReply(formData.getIdleReply());
            if (formData.getOfflineReplyEnabled() != null) existing.setOfflineReplyEnabled(formData.getOfflineReplyEnabled());
            if (formData.getOfflineReply() != null) existing.setOfflineReply(formData.getOfflineReply());
            if (formData.getNotifyOnNewMessage() != null) existing.setNotifyOnNewMessage(formData.getNotifyOnNewMessage());
            if (formData.getIncludeChatHistory() != null) existing.setIncludeChatHistory(formData.getIncludeChatHistory());
            existing.setUpdatedAt(LocalDateTime.now());
            autoReplyConfigMapper.updateById(existing);
            return existing;
        } else {
            // 新建配置
            formData.setAccountId(accountId);
            formData.setCreatedAt(LocalDateTime.now());
            formData.setUpdatedAt(LocalDateTime.now());
            autoReplyConfigMapper.insert(formData);
            return formData;
        }
    }

    /**
     * 测试规则匹配
     */
    public boolean testMatch(RuleTestRequest request) {
        return KeywordRuleEngine.testRule(
                request.getMatchType() != null ? request.getMatchType() : "CONTAINS",
                request.getKeyword(),
                request.getMessage()
        );
    }
}
