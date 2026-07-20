package cn.net.rjnetwork.xianyu.manager.rule.engine;

import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuKeywordRule;

import java.util.List;

/**
 * 关键词规则引擎
 * 根据消息内容匹配规则，返回第一条命中的回复
 */
public class KeywordRuleEngine {

    /**
     * 匹配规则
     */
    public static String match(List<XianyuKeywordRule> rules, String message) {
        if (rules == null || rules.isEmpty() || message == null) {
            return null;
        }

        // 按优先级排序
        rules.sort((a, b) -> {
            int pa = a.getPriority() != null ? a.getPriority() : 999;
            int pb = b.getPriority() != null ? b.getPriority() : 999;
            return Integer.compare(pa, pb);
        });

        for (XianyuKeywordRule rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }
            if (matches(rule.getMatchType(), rule.getKeyword(), message)) {
                return rule.getReplyText();
            }
        }
        return null;
    }

    /**
     * 测试规则是否命中
     */
    public static boolean testRule(String matchType, String keyword, String message) {
        return matches(matchType, keyword, message);
    }

    private static boolean matches(String matchType, String keyword, String message) {
        if (keyword == null || keyword.isBlank()) return false;
        switch (matchType) {
            case "EXACT":
                return keyword.equalsIgnoreCase(message);
            case "STARTS_WITH":
                return message.startsWith(keyword);
            case "CONTAINS":
            default:
                return message.contains(keyword);
        }
    }
}
