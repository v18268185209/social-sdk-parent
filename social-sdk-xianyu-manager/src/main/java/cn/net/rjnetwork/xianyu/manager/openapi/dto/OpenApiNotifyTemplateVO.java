package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiNotifyTemplateVO {

    private Long id;
    private String scenario;  // 见 NotifyScenario
    private String titleTpl;
    private String bodyTpl;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
