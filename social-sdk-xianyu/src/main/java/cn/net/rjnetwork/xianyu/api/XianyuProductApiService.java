package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼商品管理 API 服务
 * 封装商品列表、详情、搜索、上下架等 MTOP 接口调用
 */
public class XianyuProductApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuProductApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 搜索商品
     * API: mtop.taobao.idlemtopsearch.pc.search
     */
    public JsonNode searchProducts(String keyword, String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlemtopsearch.pc.search")
                .addParam("keyword", keyword)
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取我的商品列表
     * API: mtop.taobao.idlehome.mygoods.list.get
     */
    public JsonNode getMyProducts(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.mygoods.list.get")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取商品详情
     * API: mtop.taobao.idlehome.item.detail.get
     */
    public JsonNode getProductDetail(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.detail.get")
                .addParam("itemId", itemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 商品上架/下架
     * API: mtop.taobao.idlehome.item.status.update
     */
    public JsonNode updateProductStatus(String itemId, String status) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.status.update")
                .addParam("itemId", itemId)
                .addParam("status", status != null ? status : "onsale")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 首页 Feed 流
     * API: mtop.taobao.idlehome.home.webpc.feed
     */
    public JsonNode getHomeFeed() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.home.webpc.feed")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 搜索激活
     * API: mtop.taobao.idlemtopsearch.pc.item.search.activate
     */
    public JsonNode activateSearch(String keyword) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlemtopsearch.pc.item.search.activate")
                .addParam("keyword", keyword)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 搜索遮罩
     * API: mtop.taobao.idlemtopsearch.pc.search.shade
     */
    public JsonNode getSearchShade() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlemtopsearch.pc.search.shade")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
