package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OpenAppResponse {

    private Long id;

    private String appName;

    private String appKey;

    /** 明文 appSecret，仅创建时返回一次；读取时恒为 null */
    private String appSecret;

    private String status;

    private List<Long> boundAccountIds;

    private Integer rateLimitPerMinute;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}
