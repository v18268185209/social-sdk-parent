package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外卖家画像视图对象：市场模块采集的非自有账号画像。
 */
@Data
public class OpenApiSellerProfileVO {

    private Long id;

    private String userId;

    private String nickname;

    private String avatar;

    private String shopLevel;

    private Integer creditScore;

    private Integer followers;

    private Integer following;

    private Integer soldCount;

    private Integer onSaleCount;

    private String introduction;

    private String ipLocation;

    private LocalDateTime lastActiveAt;

    private LocalDateTime profileSyncedAt;

    private LocalDateTime createdAt;
}
