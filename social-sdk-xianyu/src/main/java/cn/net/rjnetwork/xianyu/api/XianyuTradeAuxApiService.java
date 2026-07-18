package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼交易辅助 API 服务
 * 封装评价、退款/售后、议价、举报等 MTOP 接口调用
 *
 * <p>这些是订单管理的重要补充能力，覆盖交易闭环中的售后环节。</p>
 */
public class XianyuTradeAuxApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuTradeAuxApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 评价 ====================

    /**
     * 给订单评价
     * API: mtop.taobao.idletrade.order.review
     *
     * @param orderId 订单 ID
     * @param rating 评分（1-5）
     * @param content 评价内容
     * @return 评价结果 JSON
     */
    public JsonNode reviewOrder(String orderId, String rating, String content) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.order.review")
                .addParam("orderId", orderId)
                .addParam("rating", rating != null ? rating : "5")
                .addParam("content", content != null ? content : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取我的评价列表
     * API: mtop.taobao.idletrade.review.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 评价列表 JSON
     */
    public JsonNode getReviewList(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.review.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 退款/售后 ====================

    /**
     * 申请退款
     * API: mtop.taobao.idletrade.refund.apply
     *
     * @param orderId 订单 ID
     * @param reason 退款原因
     * @param amount 退款金额
     * @return 退款申请结果 JSON
     */
    public JsonNode applyRefund(String orderId, String reason, String amount) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.refund.apply")
                .addParam("orderId", orderId)
                .addParam("reason", reason != null ? reason : "")
                .addParam("amount", amount != null ? amount : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取退款/售后列表
     * API: mtop.taobao.idletrade.refund.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 退款列表 JSON
     */
    public JsonNode getRefundList(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.refund.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取退款/售后详情
     * API: mtop.taobao.idletrade.refund.detail
     *
     * @param refundId 退款 ID
     * @return 退款详情 JSON
     */
    public JsonNode getRefundDetail(String refundId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.refund.detail")
                .addParam("refundId", refundId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 议价 ====================

    /**
     * 对商品议价（出价）
     * API: mtop.taobao.idletrade.price.offer
     *
     * @param itemId 商品 ID
     * @param price 出价金额
     * @return 议价结果 JSON
     */
    public JsonNode offerPrice(String itemId, String price) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.price.offer")
                .addParam("itemId", itemId)
                .addParam("price", price)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取我的议价列表
     * API: mtop.taobao.idletrade.price.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 议价列表 JSON
     */
    public JsonNode getPriceOffers(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idletrade.price.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 举报 ====================

    /**
     * 举报商品/用户
     * API: mtop.taobao.idlereport.submit
     *
     * @param targetType 目标类型（item/user）
     * @param targetId 目标 ID
     * @param reasonType 举报原因类型
     * @param reasonDetail 举报详情
     * @return 举报结果 JSON
     */
    public JsonNode reportTarget(String targetType, String targetId, String reasonType, String reasonDetail) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlereport.submit")
                .addParam("targetType", targetType != null ? targetType : "item")
                .addParam("targetId", targetId)
                .addParam("reasonType", reasonType != null ? reasonType : "")
                .addParam("reasonDetail", reasonDetail != null ? reasonDetail : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 地址管理 ====================

    /**
     * 获取收货地址列表
     * API: mtop.taobao.idleaddress.list
     *
     * @return 地址列表 JSON
     */
    public JsonNode getAddressList() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleaddress.list")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 添加收货地址
     * API: mtop.taobao.idleaddress.add
     *
     * @param receiver 收货人
     * @param phone 手机号
     * @param address 详细地址
     * @return 添加结果 JSON
     */
    public JsonNode addAddress(String receiver, String phone, String address) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleaddress.add")
                .addParam("receiver", receiver)
                .addParam("phone", phone)
                .addParam("address", address)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 删除收货地址
     * API: mtop.taobao.idleaddress.delete
     *
     * @param addressId 地址 ID
     * @return 删除结果 JSON
     */
    public JsonNode deleteAddress(String addressId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleaddress.delete")
                .addParam("addressId", addressId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
