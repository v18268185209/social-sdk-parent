package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼商品管理 API 服务
 * 封装商品列表、详情、搜索、上下架等 MTOP 接口调用
 *
 * <p>所有业务参数通过 data JSON 传递，底层 XianyuMtopApiClient 自动计算 sign、预热 token、
 * 设置 Referer/Origin，无需手动构造 URL 和签名。</p>
 */
public class XianyuProductApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuProductApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 搜索商品 — mtop.taobao.idlemtopsearch.pc.search */
    public JsonNode searchProducts(String keyword, String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword != null ? keyword : "");
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlemtopsearch.pc.search", toJson(data));
    }

    /** 获取我的商品列表 — mtop.taobao.idlehome.mygoods.list.get */
    public JsonNode getMyProducts(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlehome.mygoods.list.get", toJson(data));
    }

    /** 获取商品详情 — mtop.taobao.idlehome.item.detail.get */
    public JsonNode getProductDetail(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.detail.get", toJson(data));
    }

    /** 商品上架/下架 — mtop.taobao.idlehome.item.status.update */
    public JsonNode updateProductStatus(String itemId, String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("status", status != null ? status : "onsale");
        return apiClient.callMtop("mtop.taobao.idlehome.item.status.update", toJson(data));
    }

    /** 首页 Feed 流 — mtop.taobao.idlehome.home.webpc.feed */
    public JsonNode getHomeFeed() {
        return apiClient.callMtop("mtop.taobao.idlehome.home.webpc.feed", "{}");
    }

    /** 搜索激活 — mtop.taobao.idlemtopsearch.pc.item.search.activate */
    public JsonNode activateSearch(String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword != null ? keyword : "");
        return apiClient.callMtop("mtop.taobao.idlemtopsearch.pc.item.search.activate", toJson(data));
    }

    /** 搜索遮罩 — mtop.taobao.idlemtopsearch.pc.search.shade */
    public JsonNode getSearchShade() {
        return apiClient.callMtop("mtop.taobao.idlemtopsearch.pc.search.shade", "{}");
    }

    private static String toJson(Map<String, ?> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
