package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外账号视图对象（脱敏）：绝不暴露 cookieHeader / cookiesJson / lastError 等敏感字段。
 */
@Data
public class OpenApiAccountVO {

    private Long id;

    private String accountName;

    private String userId;

    private String displayName;

    private String status;

    private String remark;

    private String avatar;

    private String ipLocation;

    private Integer followers;

    private Integer following;

    private Integer soldCount;

    private Integer purchaseCount;

    private Integer collectionCount;

    private Integer onSaleCount;

    private String shopLevel;

    private Integer creditScore;

    private Integer reviewNum;

    private LocalDateTime lastLoginAt;

    private LocalDateTime profileSyncedAt;
}
