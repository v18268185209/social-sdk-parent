package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外 AI 厂商配置视图对象：脱敏排除 apiKey。
 */
@Data
public class OpenApiAiProviderVO {

    private Long id;

    private String name;

    private String apiBaseUrl;

    private String providerType;

    private Boolean enabled;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
