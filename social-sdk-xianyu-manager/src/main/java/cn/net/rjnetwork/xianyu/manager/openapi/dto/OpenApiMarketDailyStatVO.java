package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外市场每日统计视图对象：关键词维度每日价格聚合。
 */
@Data
public class OpenApiMarketDailyStatVO {

    private Long id;

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

    private LocalDateTime createdAt;
}
