package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼商品编辑与完整上下架 API 服务
 * 封装商品编辑、完整上下架、批量操作、价格调整等 MTOP 接口调用
 *
 * <p>这是对 XianyuProductApiService 的补充，提供更细粒度的商品管理能力。</p>
 */
public class XianyuProductEditApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuProductEditApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 商品编辑 ====================

    /**
     * 编辑商品基本信息
     * API: mtop.taobao.idlehome.item.edit
     *
     * @param itemId 商品 ID
     * @param title 商品标题
     * @param description 商品描述
     * @param price 价格
     * @param originalPrice 原价
     * @param categoryId 分类 ID
     * @param location 发货地
     * @return 编辑结果 JSON
     */
    public JsonNode editProduct(String itemId, String title, String description,
                                 String price, String originalPrice,
                                 String categoryId, String location) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.edit")
                .addParam("itemId", itemId)
                .addParam("title", title != null ? title : "")
                .addParam("description", description != null ? description : "")
                .addParam("price", price != null ? price : "")
                .addParam("originalPrice", originalPrice != null ? originalPrice : "")
                .addParam("categoryId", categoryId != null ? categoryId : "")
                .addParam("location", location != null ? location : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 编辑商品详情图
     * API: mtop.taobao.idlehome.item.detail.edit
     *
     * @param itemId 商品 ID
     * @param images 图片 URL 列表（逗号分隔）
     * @return 编辑结果 JSON
     */
    public JsonNode editProductDetails(String itemId, String images) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.detail.edit")
                .addParam("itemId", itemId)
                .addParam("images", images != null ? images : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 完整上下架 ====================

    /**
     * 商品上架（完整流程：检查状态 -> 上架）
     * API: mtop.taobao.idlehome.item.status.update
     *
     * @param itemId 商品 ID
     * @return 上架结果 JSON
     */
    public JsonNode shelfOn(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.status.update")
                .addParam("itemId", itemId)
                .addParam("status", "onsale")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 商品下架（完整流程：检查状态 -> 下架）
     * API: mtop.taobao.idlehome.item.status.update
     *
     * @param itemId 商品 ID
     * @return 下架结果 JSON
     */
    public JsonNode shelfOff(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.status.update")
                .addParam("itemId", itemId)
                .addParam("status", "offshelf")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 批量上架商品
     *
     * @param itemIds 商品 ID 列表（逗号分隔）
     * @return 批量上架结果 JSON
     */
    public JsonNode batchShelfOn(String itemIds) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.batch.status.update")
                .addParam("itemIds", itemIds)
                .addParam("status", "onsale")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 批量下架商品
     *
     * @param itemIds 商品 ID 列表（逗号分隔）
     * @return 批量下架结果 JSON
     */
    public JsonNode batchShelfOff(String itemIds) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.batch.status.update")
                .addParam("itemIds", itemIds)
                .addParam("status", "offshelf")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 价格调整 ====================

    /**
     * 调整商品价格
     * API: mtop.taobao.idlehome.item.price.update
     *
     * @param itemId 商品 ID
     * @param price 新价格
     * @return 价格调整结果 JSON
     */
    public JsonNode updatePrice(String itemId, String price) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.price.update")
                .addParam("itemId", itemId)
                .addParam("price", price)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 调整商品原价
     * API: mtop.taobao.idlehome.item.originalprice.update
     *
     * @param itemId 商品 ID
     * @param originalPrice 新原价
     * @return 原价调整结果 JSON
     */
    public JsonNode updateOriginalPrice(String itemId, String originalPrice) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.originalprice.update")
                .addParam("itemId", itemId)
                .addParam("originalPrice", originalPrice)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 库存管理 ====================

    /**
     * 调整商品库存
     * API: mtop.taobao.idlehome.item.stock.update
     *
     * @param itemId 商品 ID
     * @param stock 新库存数量
     * @return 库存调整结果 JSON
     */
    public JsonNode updateStock(String itemId, String stock) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.stock.update")
                .addParam("itemId", itemId)
                .addParam("stock", stock)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 商品分类 ====================

    /**
     * 获取可用分类列表
     * API: mtop.taobao.idlecategory.list
     *
     * @param parentId 父分类 ID（0 表示顶级分类）
     * @return 分类列表 JSON
     */
    public JsonNode getCategoryList(String parentId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecategory.list")
                .addParam("parentId", parentId != null ? parentId : "0")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * AI 智能推荐分类
     * API: mtop.taobao.idlecategory.recommend
     *
     * @param title 商品标题
     * @param description 商品描述
     * @return 推荐分类列表 JSON
     */
    public JsonNode recommendCategory(String title, String description) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecategory.recommend")
                .addParam("title", title != null ? title : "")
                .addParam("description", description != null ? description : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 商品删除 ====================

    /**
     * 删除商品（永久下架并删除）
     * API: mtop.taobao.idlehome.item.delete
     *
     * @param itemId 商品 ID
     * @return 删除结果 JSON
     */
    public JsonNode deleteProduct(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.delete")
                .addParam("itemId", itemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 批量删除商品
     *
     * @param itemIds 商品 ID 列表（逗号分隔）
     * @return 批量删除结果 JSON
     */
    public JsonNode batchDeleteProducts(String itemIds) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.batch.delete")
                .addParam("itemIds", itemIds)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 商品复制 ====================

    /**
     * 复制商品（一键转卖）
     * API: mtop.taobao.idlehome.item.copy
     *
     * @param sourceItemId 源商品 ID
     * @return 复制结果 JSON
     */
    public JsonNode copyProduct(String sourceItemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.copy")
                .addParam("sourceItemId", sourceItemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 商品状态查询 ====================

    /**
     * 获取商品完整状态信息
     * API: mtop.taobao.idlehome.item.fullinfo.get
     *
     * @param itemId 商品 ID
     * @return 商品完整信息 JSON
     */
    public JsonNode getProductFullInfo(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.fullinfo.get")
                .addParam("itemId", itemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取商品浏览量统计
     * API: mtop.taobao.idlehome.item.viewstats.get
     *
     * @param itemId 商品 ID
     * @return 浏览量统计 JSON
     */
    public JsonNode getViewStats(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.viewstats.get")
                .addParam("itemId", itemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
