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

    /**
     * 给订单评价 — 真实接口 mtop.taobao.idle.rate.create v4.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply websocket.rate_service 已真验通）：
     * 闲鱼订单评价走 mtop.taobao.idle.rate.create 域 v4.0，data 含 orderId/rating/content。</p>
     */
    public JsonNode reviewOrder(String orderId, String rating, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("rating", rating != null ? rating : "5");
        data.put("content", content != null ? content : "");
        return apiClient.callMtop("mtop.taobao.idle.rate.create", "4.0", toJson(data));
    }

    /**
     * 获取评价列表 — 真实接口 mtop.idle.web.trade.rate.list v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply delivery_rules.buyer_credit_rule 已真验通）：
     * 闲鱼评价列表走 mtop.idle.web.trade.rate.list，data={buyerId/page/pageSize}，
     * spm_cnt=a21ybx.personal.0.0。返回 data.total/data.list[]。</p>
     *
     * @param buyerId 买家 id（从订单 commonData.peerUserId 解析），可选传 null 拉全量
     */
    public JsonNode getReviewList(String buyerId, String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (buyerId != null && !buyerId.isBlank()) data.put("buyerId", buyerId);
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.idle.web.trade.rate.list", "1.0", toJson(data));
    }

    // ==================== 退款/售后 ====================

    /**
     * 获取退款/售后列表 — 真实接口 mtop.taobao.idle.merchant.refund.list v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply order_service 已真验通）：
     * 闲鱼卖家退款列表走 mtop.taobao.idle.merchant.refund.list，
     * data 含 disputeStatus（"REFUND_BY_SELLER" 等）/page/pageSize/valueType=string。</p>
     */
    public JsonNode getRefundList(String disputeStatus, String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeStatus", disputeStatus != null ? disputeStatus : "");
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        data.put("valueType", "string");
        return apiClient.callMtop("mtop.taobao.idle.merchant.refund.list", "1.0", toJson(data));
    }

    /** 申请退款 — 命名规律候选 mtop.taobao.idle.merchant.refund.apply（未真抓） */
    public JsonNode applyRefund(String orderId, String reason, String amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("reason", reason != null ? reason : "");
        data.put("amount", amount != null ? amount : "");
        return apiClient.callMtop("mtop.taobao.idle.merchant.refund.apply", toJson(data));
    }

    /** 获取退款/售后详情 — 命名规律候选 mtop.taobao.idle.merchant.refund.detail（未真抓） */
    public JsonNode getRefundDetail(String refundId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundId", refundId != null ? refundId : "");
        return apiClient.callMtop("mtop.taobao.idle.merchant.refund.detail", toJson(data));
    }

    // ==================== 订单关闭/虚拟发货/免拼发货 ====================

    /**
     * 卖家主动关闭订单 — 真实接口 mtop.taobao.idle.trade.merchant.close.by.seller v2.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply auto_delivery_handler.close_order_by_seller 已真验通）：
     * 关闲订单走 mtop.taobao.idle.trade.merchant.close.by.seller 域 v2.0，
     * data 含 orderNo + reason（固定"其他原因"），不接受外部传入 reason。</p>
     */
    public JsonNode closeOrderBySeller(String orderNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo != null ? orderNo : "");
        data.put("reason", "其他原因"); // 参考项目固定为「其他原因」
        return apiClient.callMtop("mtop.taobao.idle.trade.merchant.close.by.seller", "2.0", toJson(data));
    }

    /**
     * 虚拟发货（确认发货/无需物流）— 真实接口 mtop.taobao.idle.logistic.consign.dummy v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply shipping.confirm_service 已真验通）：
     * 虚拟发货走 mtop.taobao.idle.logistic.consign.dummy 域，
     * data={orderId,tradeText:"",picList:[],newUnconsign:true}。</p>
     */
    public JsonNode dummyDelivery(String orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("tradeText", "");
        data.put("picList", java.util.Collections.emptyList());
        data.put("newUnconsign", true);
        return apiClient.callMtop("mtop.taobao.idle.logistic.consign.dummy", "1.0", toJson(data));
    }

    /**
     * 免拼发货（团购免拼一键发货）— 真实接口 mtop.idle.groupon.activity.seller.freeshipping v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply shipping.freeshipping_service 已真验通）：
     * 免拼发货走 mtop.idle.groupon.activity.seller.freeshipping 域，
     * data 含 orderId/itemId/buyerId。</p>
     */
    public JsonNode freeShipping(String orderId, String itemId, String buyerId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("itemId", itemId != null ? itemId : "");
        data.put("buyerId", buyerId != null ? buyerId : "");
        return apiClient.callMtop("mtop.idle.groupon.activity.seller.freeshipping", "1.0", toJson(data));
    }

    // ==================== 议价 ====================

    /** 对商品议价（出价）— 命名规律候选 mtop.taobao.idletrade.price.offer（未真抓） */
    public JsonNode offerPrice(String itemId, String price) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("price", price != null ? price : "");
        return apiClient.callMtop("mtop.taobao.idletrade.price.offer", toJson(data));
    }

    /** 获取我的议价列表 — 命名规律候选 mtop.taobao.idletrade.price.list（未真抓） */
    public JsonNode getPriceOffers(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idletrade.price.list", toJson(data));
    }

    // ==================== 举报 ====================

    /** 举报商品/用户 — 命名规律候选 mtop.taobao.idlereport.submit（未真抓） */
    public JsonNode reportTarget(String targetType, String targetId, String reasonType, String reasonDetail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", targetType != null ? targetType : "item");
        data.put("targetId", targetId != null ? targetId : "");
        data.put("reasonType", reasonType != null ? reasonType : "");
        data.put("reasonDetail", reasonDetail != null ? reasonDetail : "");
        return apiClient.callMtop("mtop.taobao.idlereport.submit", toJson(data));
    }

    // ==================== 地址管理 ====================

    /** 获取收货地址列表 — 命名规律候选 mtop.taobao.idle.address.list（未真抓） */
    public JsonNode getAddressList() {
        return apiClient.callMtop("mtop.taobao.idle.address.list", "{}");
    }

    /** 添加收货地址 — 命名规律候选 mtop.taobao.idle.address.add（未真抓） */
    public JsonNode addAddress(String receiver, String phone, String address) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("receiver", receiver != null ? receiver : "");
        data.put("phone", phone != null ? phone : "");
        data.put("address", address != null ? address : "");
        return apiClient.callMtop("mtop.taobao.idle.address.add", toJson(data));
    }

    /** 删除收货地址 — 命名规律候选 mtop.taobao.idle.address.delete（未真抓） */
    public JsonNode deleteAddress(String addressId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("addressId", addressId != null ? addressId : "");
        return apiClient.callMtop("mtop.taobao.idle.address.delete", toJson(data));
    }

    // ==================== 卖家数据概览/流量分布 ====================

    /**
     * 卖家数据概览 — 真实接口 mtop.alibaba.idle.seller.pc.datacompass.singleuser.seller.summary v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply data_analysis_service 已真验通）：
     * 卖家个人数据概览（曝光/想要/留言等）走 mtop.alibaba.idle.seller.pc.datacompass 域。</p>
     */
    public JsonNode getSellerSummary() {
        return apiClient.callMtop("mtop.alibaba.idle.seller.pc.datacompass.singleuser.seller.summary", "1.0", "{}");
    }

    /**
     * 流量分布概览 — 真实接口 mtop.alibaba.idle.seller.pc.datacompass.singleuser.browse.summary v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply data_analysis_service 已真验通）：
     * 卖家流量来源/分布走 mtop.alibaba.idle.seller.pc.datacompass.singleuser.browse.summary 域。</p>
     */
    public JsonNode getBrowseSummary() {
        return apiClient.callMtop("mtop.alibaba.idle.seller.pc.datacompass.singleuser.browse.summary", "1.0", "{}");
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
