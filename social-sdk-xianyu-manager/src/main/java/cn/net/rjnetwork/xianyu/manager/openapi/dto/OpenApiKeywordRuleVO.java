package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiKeywordRuleVO {

    private Long id;
    private Long accountId;
    private String ruleName;
    private String replyType;      // KEYWORD / AI / AUTO
    private String keyword;
    private String matchType;      // CONTAINS / EXACT / STARTS_WITH
    private String replyText;
    private Boolean enabled;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
