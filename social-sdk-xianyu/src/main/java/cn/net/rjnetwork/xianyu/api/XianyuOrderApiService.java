package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼订单管理 API 服务
 * 封装订单列表、详情、发货等 MTOP 接口调用
 */
public class XianyuOrderApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuOrderApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 获取订单列表 — mtop.taobao.idletrade.list.get */
    public JsonNode getOrderList(String tab, String page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tab", tab != null ? tab : "sold");
        data.put("page", page != null ? page : "1");
        return apiClient.callMtop("mtop.taobao.idletrade.list.get", toJson(data));
    }

    /** 获取订单详情 — mtop.taobao.idletrade.order.detail.get */
    public JsonNode getOrderDetail(String orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        return apiClient.callMtop("mtop.taobao.idletrade.order.detail.get", toJson(data));
    }

    /** 自动发货 — mtop.taobao.idletrade.delivery */
    public JsonNode delivery(String orderId, String trackingNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("trackingNo", trackingNo != null ? trackingNo : "");
        return apiClient.callMtop("mtop.taobao.idletrade.delivery", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
