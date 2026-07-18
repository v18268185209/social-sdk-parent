package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductCreateRequest {
    private Long accountId;
    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String categoryId;
    private String images;
    private String description;
}
