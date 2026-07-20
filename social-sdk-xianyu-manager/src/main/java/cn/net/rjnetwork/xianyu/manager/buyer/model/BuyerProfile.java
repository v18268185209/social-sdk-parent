package cn.net.rjnetwork.xianyu.manager.buyer.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * 买家画像 — 跨会话聚合
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("buyer_profile")
public class BuyerProfile extends BaseEntity {

    private String buyerId;          // 买家 union_id / userId（唯一）
    private Long firstAccountId;     // 首次交互账号
    private String nickname;
    private String avatar;
    private Long totalSessions;      // 总会话数
    private Long totalMessages;      // 总消息数
    private Long totalOrders;        // 成交数
    private Double totalSpent;       // 累计成交金额
    private Long bargainCount;       // 议价总次数
    private Integer avgResponseSeconds; // 买家平均响应时长
    private Double credibilityScore; // 可信度 0-100
    private String tags;             // JSON 标签
    private String notes;            // 运营备注
}
