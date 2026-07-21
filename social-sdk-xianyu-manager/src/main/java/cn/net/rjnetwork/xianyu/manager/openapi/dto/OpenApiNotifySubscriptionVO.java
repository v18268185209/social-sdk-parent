package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外通知订阅规则视图对象：场景 -> 通道 + 接收范围。
 */
@Data
public class OpenApiNotifySubscriptionVO {

    private Long id;

    private String scenario;

    private Long channelId;

    private String recipientScope;

    private String recipients;

    private String accountScope;

    private String accountIds;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
