^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外商品视图对象（脱敏）：排除 deliverContentTemplate（虚拟发货内容模板，属运营敏感配置）。
 */
@Data
public class OpenApiProductVO {

    private Long id;

    private Long accountId;

    private String itemId;

    private String title;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String status;

    private String categoryId;

    private String images;

    private String description;

    private String videos;

    /** 商品类型：PHYSICAL / VIRTUAL */
    private String goodsType;

    /** 发货类型：CARD / ACCOUNT / LINK / FILE（虚拟商品用） */
    private String deliverType;

    private String detailUrl;

    private String imageUrl;

    private Integer viewCount;

    private Integer favoriteCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
