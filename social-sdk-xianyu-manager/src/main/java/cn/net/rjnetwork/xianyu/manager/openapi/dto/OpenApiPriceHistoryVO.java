package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外价格历史视图对象：市场采集到的单品价格时序。
 */
@Data
public class OpenApiPriceHistoryVO {

    private Long id;

    private String keyword;

    private String itemId;

    private String itemTitle;

    private Double price;

    private String currency;

    private String sellerId;

    private String sellerNickname;

    private Integer sellerCreditScore;

    private String itemCondition;

    private String location;

    private LocalDateTime listingTime;

    private Long snapshotId;

    private LocalDateTime snapshotTime;

    private LocalDateTime createdAt;
}
