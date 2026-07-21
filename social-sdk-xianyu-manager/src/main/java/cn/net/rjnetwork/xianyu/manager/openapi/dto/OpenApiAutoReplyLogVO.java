package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外自动回复日志视图对象：触发关键词 / AI 回复 / 自动回复的历史记录。
 */
@Data
public class OpenApiAutoReplyLogVO {

    private Long id;

    private Long accountId;

    private Long ruleId;

    private String ruleName;

    private String replyType;

    private String keyword;

    private String buyerMessage;

    private String replyText;

    private Boolean matched;

    private LocalDateTime createdAt;
}
