package cn.net.rjnetwork.xianyu.manager.market.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDate;

/**
 * 市场每日聚合统计
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("market_daily_stat")
public class MarketDailyStat extends BaseEntity {

    private String keyword;
    private LocalDate statDate;
    private Double minPrice;
    private Double maxPrice;
    private Double avgPrice;
    private Double medianPrice;
    private Double p25Price;
    private Double p75Price;
    private Integer volume;
    private Integer totalListings;
    private Integer sampledCount;
}
