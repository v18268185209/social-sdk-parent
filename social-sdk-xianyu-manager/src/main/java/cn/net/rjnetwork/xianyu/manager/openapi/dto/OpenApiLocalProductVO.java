package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外本地商品视图对象：排除 deliverContentTemplate（敏感字段）。
 */
@Data
public class OpenApiLocalProductVO {

    private Long id;

    private Long accountId;

    private String title;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String categoryId;

    private String description;

    private String images;

    private String videos;

    private String goodsType;

    private String deliverType;

    private String imageUrl;

    /** 草稿 / 待发布 / 发布中 / 发布失败 */
    private String status;

    private String publishError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
