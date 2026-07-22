package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;

/**
 * 商品级虚拟发货配置请求
 * <p>配置商品的发货方式（CARD/ACCOUNT/LINK/FILE）和发货内容模板。
 * 模板支持变量占位符：${cardCode} ${cardPassword} ${link} ${extractCode} ${fileName} ${itemTitle} ${orderId}</p>
 */
@Data
public class VirtualShipProductConfigRequest {
    /** 本地商品 id */
    private Long productId;
    /** 商品类型：VIRTUAL / PHYSICAL */
    private String goodsType;
    /** 发货类型：CARD / ACCOUNT / LINK / FILE */
    private String deliverType;
    /**
     * 发货内容模板：
     * - CARD/ACCOUNT: 支持 ${cardCode} ${cardPassword}，留空走默认"卡号/密码"格式
     * - LINK: 直接作为发货内容，支持 ${itemTitle} 等通用占位符
     * - FILE: 本地文件路径，上传网盘后用 ${link} ${extractCode} ${fileName} 渲染
     */
    private String deliverContentTemplate;
}
