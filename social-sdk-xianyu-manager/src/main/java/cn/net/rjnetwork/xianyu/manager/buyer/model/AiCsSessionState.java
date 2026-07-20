package cn.net.rjnetwork.xianyu.manager.buyer.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * AI 客服会话议价状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_cs_session_state")
public class AiCsSessionState extends BaseEntity {

    private Long sessionId;
    private Integer bargainRound;    // 本轮议价计数（每次买家议价回复时递增）
    private Double originalPrice;    // 询问时商品原价
    private Double lowestOffer;      // 买家最低出价（仅本轮最低价）
    private Double currentOffer;     // 当前 AI 报价（本轮最新）
    private Boolean dealClosed;      // 是否成交/丢单
    private String closedReason;     // DEAL / LOST / TIMEOUT / MANUAL
}
