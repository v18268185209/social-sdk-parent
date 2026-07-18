package cn.net.rjnetwork.xianyu.manager.rule.service;

import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleCreateRequest;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleTestRequest;
import cn.net.rjnetwork.xianyu.manager.rule.engine.KeywordRuleEngine;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.RuleMapper;
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
    // 内存缓存：accountId -> list of rules
    private final Map<Long, List<XianyuKeywordRule>> ruleCache = new ConcurrentHashMap<>();

    public RuleService(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
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
     * 自动回复：根据消息内容匹配规则并返回回复文本
     */
    public String autoReply(Long accountId, String message) {
        List<XianyuKeywordRule> rules = ruleCache.computeIfAbsent(accountId, k -> listRules(k));
        if (rules == null || rules.isEmpty()) {
            rules = listRules(accountId);
            ruleCache.put(accountId, rules);
        }
        return KeywordRuleEngine.match(rules, message);
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
