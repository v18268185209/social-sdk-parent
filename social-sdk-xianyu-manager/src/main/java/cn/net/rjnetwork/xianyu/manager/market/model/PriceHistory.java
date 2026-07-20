package cn.net.rjnetwork.xianyu.manager.market.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 价格历史记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("price_history")
public class PriceHistory extends BaseEntity {

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
}
