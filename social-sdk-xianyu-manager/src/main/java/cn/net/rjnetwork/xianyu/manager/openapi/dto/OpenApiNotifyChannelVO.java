^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiNotifyChannelVO {

    private Long id;
    private String type;      // EMAIL / WEBHOOK / SMS
    private String name;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 注意：configJson 含 SMTP 密码 / Webhook secret 密文，已脱敏排除
}
