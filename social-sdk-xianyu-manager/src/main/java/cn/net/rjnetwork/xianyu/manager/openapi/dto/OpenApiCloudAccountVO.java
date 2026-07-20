^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiCloudAccountVO {

    private Long id;
    private Long accountId;
    private String provider;
    private LocalDateTime tokenExpiresAt;
    private String uid;
    private Long totalSpace;
    private Long usedSpace;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 注意：accessToken / refreshToken 为网盘 OAuth 凭据，已脱敏排除
}
