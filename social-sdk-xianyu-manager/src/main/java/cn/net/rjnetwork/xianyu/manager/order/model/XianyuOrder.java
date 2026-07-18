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
}
