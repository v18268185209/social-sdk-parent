package cn.net.rjnetwork.xianyu.manager.product.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 本地商品：自建商品，尚未上架闲鱼。
 * 发布成功后物理删除；不长期滞留。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("local_product")
public class LocalProduct extends BaseEntity {

    /** 关联闲鱼账号，发布时按此账号上架 */
    private Long accountId;

    private String title;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private String categoryId;
    private String description;
    private String images;   // JSON array of image URLs
    private String videos;   // JSON array of video URLs

    /** 商品类型：PHYSICAL / VIRTUAL */
    private String goodsType;

    /** 发货类型：CARD / ACCOUNT / LINK / FILE（虚拟商品用） */
    private String deliverType;

    /** 发货内容模板（虚拟商品用） */
    private String deliverContentTemplate;

    /** 商品主图封面（首图 URL，列表展示用） */
    private String imageUrl;

    /** 草稿 / 待发布 / 发布中 / 发布失败 */
    private String status;

    /** 发布失败原因（供前端回显；成功/未发布留空） */
    private String publishError;
}
