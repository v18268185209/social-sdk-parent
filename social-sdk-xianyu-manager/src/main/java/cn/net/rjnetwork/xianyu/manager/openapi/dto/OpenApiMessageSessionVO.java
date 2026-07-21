package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外消息会话视图对象：会话级摘要，不含单条消息内容。
 */
@Data
public class OpenApiMessageSessionVO {

    private Long accountId;

    private String sessionId;

    private String lastSenderName;

    private String lastContent;

    private String lastDirection;

    private Boolean lastAutoReply;

    private LocalDateTime lastMessageTime;

    private Long messageCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
