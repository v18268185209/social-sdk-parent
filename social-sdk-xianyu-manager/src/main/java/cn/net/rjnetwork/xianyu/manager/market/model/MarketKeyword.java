package cn.net.rjnetwork.xianyu.manager.market.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 市场情报追踪关键词（独立于监控任务，轻量管理）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("market_keyword")
public class MarketKeyword extends BaseEntity {

    private String keyword;

    /** ACTIVE / PAUSED */
    private String status;

    /** 抓取间隔（分钟） */
    private Integer crawlIntervalMinutes;

    /** 上次抓取时间 */
    private LocalDateTime lastCrawlAt;

    /** 上次抓取到的商品数 */
    private Integer lastCrawlResultCount;
}
