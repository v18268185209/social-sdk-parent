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
     * <p>真实抓包验证（参考项目 xianyu-auto-reply rate_service.RateService.rate_buyer 已真验通）：
     * data={tradeId, rate(1=好评), feedback, createOrAppend(0=新建)}。
     * 注意：字段名是 tradeId 不是 orderId，是 rate(int) 不是 rating(String)，是 feedback 不是 content。</p>
     *
     * @param orderId  订单/交易 ID（对应闲鱼字段 tradeId）
     * @param rating   评分：GOOD→1(好评) / NORMAL→2(中评) / BAD→3(差评)
     * @param content  评价内容（对应闲鱼字段 feedback）
     */
    public JsonNode reviewOrder(String orderId, String rating, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tradeId", orderId != null ? orderId : "");
        data.put("rate", mapRating(rating));
        data.put("feedback", content != null ? content : "");
        data.put("createOrAppend", 0);
        return apiClient.callMtop("mtop.taobao.idle.rate.create", "4.0", toJson(data));
    }

    /** 前端 GOOD/NORMAL/BAD 映射为闲鱼 rate 整数值 */
    private static int mapRating(String rating) {
        if (rating == null) return 1;
        switch (rating.toUpperCase()) {
            case "GOOD": return 1;      // 好评
            case "NORMAL": return 2;    // 中评
            case "BAD": return 3;       // 差评
            default:
                // 兼容直接传数字的情况
                try { return Integer.parseInt(rating); } catch (NumberFormatException e) { return 1; }
        }
    }

    /**
     * 获取评价列表 — 真实接口 mtop.idle.web.trade.rate.list v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply buyer_credit_rule._check_buyer_rate_count 已真验通）：
     * data={rateType:0, ratedUid, raterType:0, rowsPerPage, pageNumber, foldFlag:0, fishAdCode:"330110", extraTag:""}。
     * 注意：字段名是 ratedUid 不是 buyerId，是 pageNumber 不是 page，是 rowsPerPage 不是 pageSize。
     * spm_cnt 应为 a21ybx.personal.0.0（在 RequestBuilder 中全局设置，此处无法单独覆盖）。</p>
     *
     * @param buyerId 买家 id（对应闲鱼字段 ratedUid），可选传 null 拉全量
     */
    public JsonNode getReviewList(String buyerId, String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rateType", 0);
        if (buyerId != null && !buyerId.isBlank()) data.put("ratedUid", buyerId);
        data.put("raterType", 0);
        data.put("rowsPerPage", parseInt(pageSize, 20));
        data.put("pageNumber", parseInt(page, 1));
        data.put("foldFlag", 0);
        data.put("fishAdCode", "330110");
        data.put("extraTag", "");
        return apiClient.callMtop("mtop.idle.web.trade.rate.list", "1.0", toJson(data));
    }

    /** 安全解析 int，失败返回 defaultValue */
    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    // ==================== 退款/售后 ====================

    /**
     * 获取退款/售后列表 — 真实接口 mtop.taobao.idle.merchant.refund.list v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply order_service._fetch_refund_orders_page 已真验通）：
     * data={pageNumber, rowsPerPage, queryType:"refund", refundSearchParam:{disputeStatus, queryCode:"ALL"}}。
     * 注意：disputeStatus 嵌套在 refundSearchParam 中，值是数字字符串 "1"/"2"/"3"=退款中, "5"=退款成功。
     * valueType:"string" 在参考项目中是放在 URL params 而非 data 中。</p>
     *
     * @param disputeStatus 退款状态：1/2/3=退款中, 5=退款成功, 空=全部
     */
    public JsonNode getRefundList(String disputeStatus, String page, String pageSize) {
        Map<String, Object> refundSearchParam = new LinkedHashMap<>();
        refundSearchParam.put("disputeStatus", disputeStatus != null ? disputeStatus : "");
        refundSearchParam.put("queryCode", "ALL");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNumber", parseInt(page, 1));
        data.put("rowsPerPage", parseInt(pageSize, 20));
        data.put("queryType", "refund");
        data.put("refundSearchParam", refundSearchParam);
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
