package cn.net.rjnetwork.xianyu.manager.ai.dto;

import lombok.Data;

@Data
public class AiModelRequest {
    private Long providerId;
    private String modelName;
    private String displayName;
    private String modelType;
    private String capabilities;
    private Double defaultTemperature;
    private Integer defaultMaxTokens;
    private Boolean enabled;
    private String remark;
}
