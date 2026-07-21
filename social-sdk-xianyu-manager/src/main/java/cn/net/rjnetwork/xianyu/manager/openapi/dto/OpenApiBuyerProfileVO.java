package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外买家画像视图对象：暴露买家统计与画像数据，不含原始消息内容。
 */
@Data
public class OpenApiBuyerProfileVO {

    private Long id;

    private String buyerId;

    private Long firstAccountId;

    private String nickname;

    private String avatar;

    private Long totalSessions;

    private Long totalMessages;

    private Long totalOrders;

    private Double totalSpent;

    private Long bargainCount;

    private Integer avgResponseSeconds;

    private Double credibilityScore;

    private String tags;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
