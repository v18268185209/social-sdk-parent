package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外监控结果视图对象：匹配到的闲鱼商品快照。
 */
@Data
public class OpenApiMonitorResultVO {

    private Long id;

    private Long taskId;

    private String itemId;

    private String itemTitle;

    private Double price;

    private String imageUrl;

    private String sellerNickname;

    private Integer sellerCreditScore;

    private String itemUrl;

    private Double aiScore;

    private String aiReason;

    private String matchedKeywords;

    private Boolean notified;

    private Long snapshotId;

    private LocalDateTime createdAt;
}
