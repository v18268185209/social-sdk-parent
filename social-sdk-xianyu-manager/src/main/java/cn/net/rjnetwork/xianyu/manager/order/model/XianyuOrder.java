package cn.net.rjnetwork.xianyu.manager.order.model;

import com.baomidou.mybatisplus.annotation.TableField;
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
    /** 闲鱼商品 ID，用于精准关联本地商品 */
    private String itemId;
    private String itemTitle;
    /** 对手方昵称：SOLD=买家，BOUGHT=卖家 */
    private String counterpartyName;
    /** 买家用户 ID（SOLD 订单优先填充） */
    private String buyerId;
    /** 卖家用户 ID（BOUGHT 订单优先填充） */
    private String sellerId;
    /** 闲鱼订单详情链接 */
    private String orderDetailUrl;
    /** 原始订单 JSON 快照，便于后续补字段/排查接口结构变化 */
    private String rawData;
    private BigDecimal amount;
    private String status; // PENDING, PAID, SHIPPED, COMPLETED, REFUNDING, REFUNDED, CLOSED
    /** 闲鱼原始状态枚举 (tradeStatusEnum)，用于调试和回查 */
    private String tradeStatusEnum;
    /** 是否为卖家订单（bought 返回有 seller 标记，sold API 无此字段） */
    private Boolean isSeller;
    private String trackingNo;
    private java.time.LocalDateTime orderTime;

    /** 商品类型：PHYSICAL / VIRTUAL */
    private String goodsType;

    /** 关联本地商品 id（订单同步时按 item_id 反查回填） */
    private Long productId;

    /** 是否需要虚拟发货 */
    private Boolean requireVirtualShip;

    /** 虚拟发货时间 */
    private java.time.LocalDateTime virtualShippedAt;

    /** 预计自动确认收货时间 */
    private java.time.LocalDateTime autoReceiptAt;

    /** 发货内容快照 */
    private String deliverContent;

    /** 虚拟发货任务状态（列表展示用，非订单表字段） */
    @TableField(exist = false)
    private String virtualShipTaskStatus;

    /** 虚拟发货失败原因（列表展示用，非订单表字段） */
    @TableField(exist = false)
    private String virtualShipTaskError;

    /** 虚拟发货计划执行时间（列表展示用，非订单表字段） */
    @TableField(exist = false)
    private java.time.LocalDateTime virtualShipExecuteAt;
}
