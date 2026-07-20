^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAutoReplyConfigVO {

    private Long id;
    private Long accountId;
    private Boolean aiEnabled;
    private Long aiModelId;
    private String aiSystemPrompt;
    private Double aiTemperature;
    private Boolean autoReplyEnabled;
    private String welcomeMessage;
    private String fallbackReply;
    private Integer idleTimeoutMinutes;
    private String idleReply;
    private Boolean offlineReplyEnabled;
    private String offlineReply;
    private Boolean notifyOnNewMessage;
    private Boolean includeChatHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
