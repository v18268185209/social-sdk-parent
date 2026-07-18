package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外订单视图对象（脱敏）：排除 deliverContent（实际虚拟发货内容，可能含卡密等敏感信息）。
 */
@Data
public class OpenApiOrderVO {

    private Long id;

    private Long accountId;

    private String type;

    private String orderId;

    private String itemTitle;

    private String counterpartyName;

    private BigDecimal amount;

    private String status;

    private String trackingNo;

    private LocalDateTime orderTime;

    /** 商品类型：PHYSICAL / VIRTUAL */
    private String goodsType;

    /** 是否需要虚拟发货 */
    private Boolean requireVirtualShip;

    private LocalDateTime virtualShippedAt;

    private LocalDateTime autoReceiptAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
