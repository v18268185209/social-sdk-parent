package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼交易辅助 API 服务
 * 封装评价、退款/售后、议价、举报等 MTOP 接口调用
 */
public class XianyuTradeAuxApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuTradeAuxApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 评价 ====================

    /** 给订单评价 — mtop.taobao.idletrade.order.review */
    public JsonNode reviewOrder(String orderId, String rating, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("rating", rating != null ? rating : "5");
        data.put("content", content != null ? content : "");
        return apiClient.callMtop("mtop.taobao.idletrade.order.review", toJson(data));
    }

    /** 获取我的评价列表 — mtop.taobao.idletrade.review.list */
    public JsonNode getReviewList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idletrade.review.list", toJson(data));
    }

    // ==================== 退款/售后 ====================

    /** 申请退款 — mtop.taobao.idletrade.refund.apply */
    public JsonNode applyRefund(String orderId, String reason, String amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("reason", reason != null ? reason : "");
        data.put("amount", amount != null ? amount : "");
        return apiClient.callMtop("mtop.taobao.idletrade.refund.apply", toJson(data));
    }

    /** 获取退款/售后列表 — mtop.taobao.idletrade.refund.list */
    public JsonNode getRefundList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idletrade.refund.list", toJson(data));
    }

    /** 获取退款/售后详情 — mtop.taobao.idletrade.refund.detail */
    public JsonNode getRefundDetail(String refundId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundId", refundId != null ? refundId : "");
        return apiClient.callMtop("mtop.taobao.idletrade.refund.detail", toJson(data));
    }

    // ==================== 议价 ====================

    /** 对商品议价（出价）— mtop.taobao.idletrade.price.offer */
    public JsonNode offerPrice(String itemId, String price) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("price", price != null ? price : "");
        return apiClient.callMtop("mtop.taobao.idletrade.price.offer", toJson(data));
    }

    /** 获取我的议价列表 — mtop.taobao.idletrade.price.list */
    public JsonNode getPriceOffers(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idletrade.price.list", toJson(data));
    }

    // ==================== 举报 ====================

    /** 举报商品/用户 — mtop.taobao.idlereport.submit */
    public JsonNode reportTarget(String targetType, String targetId, String reasonType, String reasonDetail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", targetType != null ? targetType : "item");
        data.put("targetId", targetId != null ? targetId : "");
        data.put("reasonType", reasonType != null ? reasonType : "");
        data.put("reasonDetail", reasonDetail != null ? reasonDetail : "");
        return apiClient.callMtop("mtop.taobao.idlereport.submit", toJson(data));
    }

    // ==================== 地址管理 ====================

    /** 获取收货地址列表 — mtop.taobao.idleaddress.list */
    public JsonNode getAddressList() {
        return apiClient.callMtop("mtop.taobao.idleaddress.list", "{}");
    }

    /** 添加收货地址 — mtop.taobao.idleaddress.add */
    public JsonNode addAddress(String receiver, String phone, String address) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("receiver", receiver != null ? receiver : "");
        data.put("phone", phone != null ? phone : "");
        data.put("address", address != null ? address : "");
        return apiClient.callMtop("mtop.taobao.idleaddress.add", toJson(data));
    }

    /** 删除收货地址 — mtop.taobao.idleaddress.delete */
    public JsonNode deleteAddress(String addressId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("addressId", addressId != null ? addressId : "");
        return apiClient.callMtop("mtop.taobao.idleaddress.delete", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
