package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiNotifyMessageVO {

    private Long id;
    private Long accountId;   // 关联的闲鱼账号（可空）
    private String scenario;
    private String title;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
