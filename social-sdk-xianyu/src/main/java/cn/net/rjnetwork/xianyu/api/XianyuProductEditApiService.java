package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼商品编辑与完整上下架 API 服务
 * 封装商品编辑、完整上下架、批量操作、价格调整等 MTOP 接口调用
 *
 * <p>所有业务参数通过 data JSON 传递，底层 XianyuMtopApiClient 自动计算 sign、预热 token、
 * 设置 Referer/Origin，无需手动构造 URL 和签名。</p>
 */
public class XianyuProductEditApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuProductEditApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 商品编辑 ====================

    /** 编辑商品基本信息 — mtop.taobao.idlehome.item.edit */
    public JsonNode editProduct(String itemId, String title, String description,
                                String price, String originalPrice,
                                String categoryId, String location) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        data.put("price", price != null ? price : "");
        data.put("originalPrice", originalPrice != null ? originalPrice : "");
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("location", location != null ? location : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.edit", toJson(data));
    }

    /** 编辑商品详情图 — mtop.taobao.idlehome.item.detail.edit */
    public JsonNode editProductDetails(String itemId, String images) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("images", images != null ? images : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.detail.edit", toJson(data));
    }

    // ==================== 完整上下架 ====================

    /** 商品上架 — mtop.taobao.idlehome.item.status.update */
    public JsonNode shelfOn(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("status", "onsale");
        return apiClient.callMtop("mtop.taobao.idlehome.item.status.update", toJson(data));
    }

    /** 商品下架 — mtop.taobao.idlehome.item.status.update */
    public JsonNode shelfOff(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("status", "offshelf");
        return apiClient.callMtop("mtop.taobao.idlehome.item.status.update", toJson(data));
    }

    /** 批量上架商品 — mtop.taobao.idlehome.item.batch.status.update */
    public JsonNode batchShelfOn(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        data.put("status", "onsale");
        return apiClient.callMtop("mtop.taobao.idlehome.item.batch.status.update", toJson(data));
    }

    /** 批量下架商品 — mtop.taobao.idlehome.item.batch.status.update */
    public JsonNode batchShelfOff(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        data.put("status", "offshelf");
        return apiClient.callMtop("mtop.taobao.idlehome.item.batch.status.update", toJson(data));
    }

    // ==================== 价格调整 ====================

    /** 调整商品价格 — mtop.taobao.idlehome.item.price.update */
    public JsonNode updatePrice(String itemId, String price) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("price", price != null ? price : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.price.update", toJson(data));
    }

    /** 调整商品原价 — mtop.taobao.idlehome.item.originalprice.update */
    public JsonNode updateOriginalPrice(String itemId, String originalPrice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("originalPrice", originalPrice != null ? originalPrice : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.originalprice.update", toJson(data));
    }

    // ==================== 库存管理 ====================

    /** 调整商品库存 — mtop.taobao.idlehome.item.stock.update */
    public JsonNode updateStock(String itemId, String stock) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("stock", stock != null ? stock : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.stock.update", toJson(data));
    }

    // ==================== 商品分类 ====================

    /** 获取可用分类列表 — mtop.taobao.idlecategory.list */
    public JsonNode getCategoryList(String parentId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parentId", parentId != null ? parentId : "0");
        return apiClient.callMtop("mtop.taobao.idlecategory.list", toJson(data));
    }

    /** AI 智能推荐分类 — mtop.taobao.idlecategory.recommend */
    public JsonNode recommendCategory(String title, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        return apiClient.callMtop("mtop.taobao.idlecategory.recommend", toJson(data));
    }

    // ==================== 商品删除 ====================

    /** 删除商品 — mtop.taobao.idlehome.item.delete */
    public JsonNode deleteProduct(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.delete", toJson(data));
    }

    /** 批量删除商品 — mtop.taobao.idlehome.item.batch.delete */
    public JsonNode batchDeleteProducts(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.batch.delete", toJson(data));
    }

    // ==================== 商品复制 ====================

    /** 复制商品（一键转卖）— mtop.taobao.idlehome.item.copy */
    public JsonNode copyProduct(String sourceItemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceItemId", sourceItemId != null ? sourceItemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.copy", toJson(data));
    }

    // ==================== 商品状态查询 ====================

    /** 获取商品完整状态信息 — mtop.taobao.idlehome.item.fullinfo.get */
    public JsonNode getProductFullInfo(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.fullinfo.get", toJson(data));
    }

    /** 获取商品浏览量统计 — mtop.taobao.idlehome.item.viewstats.get */
    public JsonNode getViewStats(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.viewstats.get", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
