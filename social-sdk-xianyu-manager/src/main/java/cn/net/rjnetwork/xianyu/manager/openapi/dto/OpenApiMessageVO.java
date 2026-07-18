package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外消息视图对象。content 为消息正文（核心数据），原样透出。
 */
@Data
public class OpenApiMessageVO {

    private Long id;

    private Long accountId;

    private String sessionId;

    private String msgId;

    private String senderId;

    private String senderName;

    private String content;

    private String msgType;

    private String direction;

    private Boolean autoReply;

    private LocalDateTime messageTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
