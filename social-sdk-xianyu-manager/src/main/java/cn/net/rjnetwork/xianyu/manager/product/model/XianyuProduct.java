package cn.net.rjnetwork.xianyu.manager.product.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_product")
public class XianyuProduct extends BaseEntity {

    private Long accountId;
    private String itemId;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String status; // ON_SALE, OFF_SALE, DRAFT
    private String categoryId;
    private String images; // JSON array of image URLs
    private String description;
    private String videos; // JSON array of video URLs
    private String detailUrl;
    private Integer viewCount;
    private Integer favoriteCount;
}
