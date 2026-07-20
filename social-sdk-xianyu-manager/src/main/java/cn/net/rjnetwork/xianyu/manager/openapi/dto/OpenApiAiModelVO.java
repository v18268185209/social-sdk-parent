^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAiModelVO {

    private Long id;
    private Long providerId;
    private String modelName;
    private String displayName;
    private String modelType;
    private String capabilities;
    private Double defaultTemperature;
    private Integer defaultMaxTokens;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
