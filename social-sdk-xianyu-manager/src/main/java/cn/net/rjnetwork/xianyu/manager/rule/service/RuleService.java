package cn.net.rjnetwork.xianyu.manager.rule.service;

import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleCreateRequest;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleTestRequest;
import cn.net.rjnetwork.xianyu.manager.rule.engine.KeywordRuleEngine;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.AutoReplyConfigMapper;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.RuleMapper;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuAutoReplyConfig;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuKeywordRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    // 内存缓存：accountId -> list of rules
    private final Map<Long, List<XianyuKeywordRule>> ruleCache = new ConcurrentHashMap<>();

    public RuleService(RuleMapper ruleMapper, AutoReplyConfigMapper autoReplyConfigMapper) {
        this.ruleMapper = ruleMapper;
        this.autoReplyConfigMapper = autoReplyConfigMapper;
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
                return rule.getReplyText();
            }
        }

        // 2. AI 接管（次优先级）
        XianyuAutoReplyConfig config = getAutoReplyConfig(accountId);
        if (config != null && Boolean.TRUE.equals(config.getAiEnabled())) {
            String aiReply = callAiReply(config, message);
            if (aiReply != null && !aiReply.isEmpty()) {
                return aiReply;
            }
        }

        // 3. 兜底自动回复（最低优先级）
        if (config != null && Boolean.TRUE.equals(config.getAutoReplyEnabled())) {
            return config.getFallbackReply();
        }

        return null;
    }

    /**
     * 调用 AI 生成回复（预留实现，当前返回 null 不影响其他层级）
     */
    private String callAiReply(XianyuAutoReplyConfig config, String message) {
        if (config == null || config.getAiApiKey() == null || config.getAiApiKey().isEmpty()) {
            return null;
        }
        // TODO: 实现真实 AI 调用（OpenAI / Claude / 自定义 API）
        // 临时返回 null 让后续层级接管
        return null;
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
            if (formData.getAiProvider() != null) existing.setAiProvider(formData.getAiProvider());
            if (formData.getAiApiKey() != null) existing.setAiApiKey(formData.getAiApiKey());
            if (formData.getAiApiUrl() != null) existing.setAiApiUrl(formData.getAiApiUrl());
            if (formData.getAiModel() != null) existing.setAiModel(formData.getAiModel());
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
