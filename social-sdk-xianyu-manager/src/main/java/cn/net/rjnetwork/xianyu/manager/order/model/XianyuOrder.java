package cn.net.rjnetwork.xianyu.manager.order.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_order")
public class XianyuOrder extends BaseEntity {

    private Long accountId;
    private String type; // SOLD, BOUGHT
    private String orderId;
    private String itemTitle;
    private String counterpartyName;
    private BigDecimal amount;
    private String status; // PENDING, PAID, SHIPPED, COMPLETED, REFUNDING
    private String trackingNo;
    private java.time.LocalDateTime orderTime;

    /** 商品类型：PHYSICAL / VIRTUAL */
    private String goodsType;

    /** 是否需要虚拟发货 */
    private Boolean requireVirtualShip;

    /** 虚拟发货时间 */
    private java.time.LocalDateTime virtualShippedAt;

    /** 预计自动确认收货时间 */
    private java.time.LocalDateTime autoReceiptAt;

    /** 发货内容快照 */
    private String deliverContent;
}
