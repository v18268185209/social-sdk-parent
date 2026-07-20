^package cn.net.rjnetwork.xianyu.manager.ai.dto;

import lombok.Data;

@Data
public class AiProviderRequest {
    private String name;
    private String apiBaseUrl;
    private String apiKey;
    private String providerType;
    private Boolean enabled;
    private String remark;
}
