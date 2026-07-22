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

    /** 商品类型：PHYSICAL / VIRTUAL */
    private String goodsType;

    /** 发货类型：CARD / ACCOUNT / LINK / FILE（虚拟商品用） */
    private String deliverType;

    /** 发货内容模板（虚拟商品用） */
    private String deliverContentTemplate;

    private String detailUrl;
    private String imageUrl; // 主图 URL（商品列表返回的首图）
    private Integer viewCount;
    private Integer favoriteCount;

    /** 列表接口原始 cardData JSON，保留闲鱼未显式建模字段 */
    private String rawData;

    /** auctionType，如 b */
    private String auctionType;

    /** 闲鱼原始 itemStatus 数字/字符串 */
    private String itemStatusRaw;

    /** 邮寄/自提信息，如 包邮 / 仅自提 / 包邮或自提 */
    private String postInfo;

    /** 列表 detailParams.imageInfos 原始 JSON 字符串 */
    private String imageInfos;

    private Integer picWidth;
    private Integer picHeight;
    private Boolean hasVideo;
}
