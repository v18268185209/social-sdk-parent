package cn.net.rjnetwork.xianyu.manager.rule.dto;

import lombok.Data;

@Data
public class RuleCreateRequest {
    private Long accountId;
    private String ruleName;
    private String replyType = "KEYWORD"; // KEYWORD, AI, AUTO
    private String keyword;
    private String matchType;
    private String replyText;
    private Integer priority;
    /** 触发动作：POLISH（擦亮）/ SUPER_POLISH（超级擦亮）/ null（仅回复） */
    private String action;
    /** 动作目标商品 itemId（null 时取会话最近一笔在架商品） */
    private String actionTargetItemId;
}
