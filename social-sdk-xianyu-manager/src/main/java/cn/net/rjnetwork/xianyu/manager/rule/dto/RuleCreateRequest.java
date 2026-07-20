^package cn.net.rjnetwork.xianyu.manager.rule.dto;

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
}
