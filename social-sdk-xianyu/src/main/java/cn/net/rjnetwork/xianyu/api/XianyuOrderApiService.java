package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼订单管理 API 服务
 * 封装订单列表、详情、发货等 MTOP 接口调用
 */
public class XianyuOrderApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuOrderApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取订单列表
     * API: mtop.taobao.idletrade.list.get
     */
    public JsonNode getOrderList(String tab, String page) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.list.get")
                .addParam("tab", tab != null ? tab : "sold")
                .addParam("page", page != null ? page : "1")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取订单详情
     * API: mtop.taobao.idletrade.order.detail.get
     */
    public JsonNode getOrderDetail(String orderId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.order.detail.get")
                .addParam("orderId", orderId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 自动发货
     * API: mtop.taobao.idletrade.delivery
     */
    public JsonNode delivery(String orderId, String trackingNo) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.delivery")
                .addParam("orderId", orderId)
                .addParam("trackingNo", trackingNo)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
