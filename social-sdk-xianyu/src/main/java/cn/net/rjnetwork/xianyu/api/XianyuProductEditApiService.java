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

    /**
     * 编辑商品详情图 — 命名规律候选 mtop.taobao.idlemanage.item.detail.edit
     * <p>未真抓验证（闲鱼 PC/H5 详情页未暴露编辑按钮入口，走内部 SPA 域）。
     * 已真验同域接口：com.taobao.idle.item.delete v1.1（删除），
     * 推测编辑类走 mtop.taobao.idlemanage.* 域，待后续真抓微调。</p>
     */
    public JsonNode editProductDetails(String itemId, String images) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("images", images != null ? images : "");
        return apiClient.callMtop("mtop.taobao.idlemanage.item.detail.edit", toJson(data));
    }

    // ==================== 完整上下架 ====================

    /**
     * 商品上架 — 命名规律候选 mtop.taobao.idlemanage.item.upshelf
     * <p>未真抓验证。已真验下架走 mtop.taobao.idle.item.downshelf v2.0（不是 idlemanage 域），
     * 上架是下架的姊妹接口，命名规律候选 upshelf，待后续真抓微调。</p>
     */
    public JsonNode shelfOn(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        // 上架是下架的姊妹接口，按真实下架接口同域命名：mtop.taobao.idle.item.upshelf v2.0
        return apiClient.callMtop("mtop.taobao.idle.item.upshelf", "2.0", toJson(data));
    }

    /**
     * 商品下架 — 真实接口 mtop.taobao.idle.item.downshelf v2.0
     * <p>真实抓包验证（2026-07-19 CDP 抓详情页「下架」按钮 React onClick handler 源代码）。
     * 与 XianyuProductApiService.updateProductStatus(offsale) 同接口，保留这个方法为兼容旧 facade 调用。</p>
     */
    public JsonNode shelfOff(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.item.downshelf", "2.0", toJson(data));
    }

    /** 批量上架商品 — 命名规律候选 mtop.taobao.idle.item.batch.upshelf v2.0（未真抓） */
    public JsonNode batchShelfOn(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idle.item.batch.upshelf", "2.0", toJson(data));
    }

    /** 批量下架商品 — 命名规律候选 mtop.taobao.idle.item.batch.downshelf v2.0（未真抓） */
    public JsonNode batchShelfOff(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idle.item.batch.downshelf", "2.0", toJson(data));
    }

    // ==================== 价格调整 ====================

    /** 调整商品价格 — 命名规律候选 mtop.taobao.idlemanage.item.price.update（未真抓） */
    public JsonNode updatePrice(String itemId, String price) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("price", price != null ? price : "");
        return apiClient.callMtop("mtop.taobao.idlemanage.item.price.update", toJson(data));
    }

    /** 调整商品原价 — 命名规律候选 mtop.taobao.idlemanage.item.originalprice.update（未真抓） */
    public JsonNode updateOriginalPrice(String itemId, String originalPrice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("originalPrice", originalPrice != null ? originalPrice : "");
        return apiClient.callMtop("mtop.taobao.idlemanage.item.originalprice.update", toJson(data));
    }

    // ==================== 库存管理 ====================

    /** 调整商品库存 — 命名规律候选 mtop.taobao.idlemanage.item.stock.update（未真抓） */
    public JsonNode updateStock(String itemId, String stock) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("stock", stock != null ? stock : "");
        return apiClient.callMtop("mtop.taobao.idlemanage.item.stock.update", toJson(data));
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

    /**
     * 删除商品 — 真实接口 mtop.alibaba.idle.seller.pc.item.delete v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply 已真验通）：
     * 闲鱼 PC 卖家中心删除商品走 mtop.alibaba.idle.seller.pc.item.delete 域，
     * 之前抓到的 com.taobao.idle.item.delete 是详情页按钮 onClick 姿妹接口（也存在但走 App WebView），
     * 这里用参考项目真验通的 PC 域接口名。</p>
     */
    public JsonNode deleteProduct(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.alibaba.idle.seller.pc.item.delete", "1.0", toJson(data));
    }

    /**
     * 商品擦亮（提升曝光排名）— 真实接口 mtop.taobao.idle.item.polish v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply scheduler.polish_task 已真验通）：
     * 闲鱼定时擦亮任务走 mtop.taobao.idle.item.polish，data={itemId}，
     * spm_cnt=a21ybx.item.0.0 / spm_pre=a21ybx.personal.feeds.1.42f86ac21eZ9zd</p>
     */
    public JsonNode polishItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.item.polish", "1.0", toJson(data));
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
