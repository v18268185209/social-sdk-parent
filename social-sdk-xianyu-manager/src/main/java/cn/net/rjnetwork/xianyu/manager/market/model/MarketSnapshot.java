package cn.net.rjnetwork.xianyu.manager.market.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 市场搜索快照
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("market_snapshot")
public class MarketSnapshot extends BaseEntity {

    private Long taskId;
    private String keyword;
    private Long accountId;
    private Integer totalResults;
    private String rawData;
    private LocalDateTime snapshotTime;
}
