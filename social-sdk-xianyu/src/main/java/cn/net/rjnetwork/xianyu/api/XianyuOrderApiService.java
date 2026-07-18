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

    /**
     * 获取我卖出的订单列表 — 真实接口 mtop.idle.web.trade.sold.list
     * <p>真实抓包验证（2026-07-18 CDP 导航到 https://www.goofish.com/sold）：</p>
     * <ul>
     *   <li>POST https://h5api.m.goofish.com/h5/mtop.idle.web.trade.sold.list/1.0/</li>
     *   <li>data: {} （无业务参数，按 cookie 解身份）</li>
     *   <li>返回 data.items[] → 每项含 commonData.orderId/itemId/peerUserId/buyer/tradeStatusEnum/orderDetailUrl</li>
     *   <li>  content.data.detailInfo.{auctionTitle,auctionPic} / content.data.priceInfo.{price,buyAmount}</li>
     *   <li>  head.data.{createTime,statusViewMsg,userInfo.userNick,userIcon,userId}</li>
     * </ul>
     *
     * @param pageNumber 页码（从 1 开始），可选
     * @param pageSize 每页条数，可选
     */
    public JsonNode getSoldOrderList(String pageNumber, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (pageNumber != null && !pageNumber.isBlank()) data.put("pageNumber", pageNumber);
        if (pageSize != null && !pageSize.isBlank()) data.put("pageSize", pageSize);
        return apiClient.callMtop("mtop.idle.web.trade.sold.list", toJson(data));
    }

    /**
     * 获取我买到的订单列表 — 真实接口 mtop.idle.web.trade.bought.list
     * <p>真实抓包验证（2026-07-18 CDP 导航到 https://www.goofish.com/bought）：</p>
     * <ul>
     *   <li>POST https://h5api.m.goofish.com/h5/mtop.idle.web.trade.bought.list/1.0/</li>
     *   <li>data: {} （无业务参数，按 cookie 解身份）</li>
     *   <li>返回 data.items[] → 每项含 commonData.orderId/itemId/peerUserId/seller/tradeStatusEnum/orderDetailUrl</li>
     *   <li>  content.data.detailInfo.{auctionTitle,auctionPic} / content.data.priceInfo.{price,buyAmount}</li>
     *   <li>  head.data.{createTime,statusViewMsg,userInfo.userNick,userIcon,userId}</li>
     * </ul>
     *
     * @param pageNumber 页码（从 1 开始），可选
     * @param pageSize 每页条数，可选
     */
    public JsonNode getOrderList(String pageNumber, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (pageNumber != null && !pageNumber.isBlank()) data.put("pageNumber", pageNumber);
        if (pageSize != null && !pageSize.isBlank()) data.put("pageSize", pageSize);
        return apiClient.callMtop("mtop.idle.web.trade.bought.list", toJson(data));
    }

    /**
     * 获取订单详情 — 真实接口 mtop.idle.web.trade.order.detail v1.0
     * <p>真实 SDK 探测验证（2026-07-19）：dummy orderId 触发 FAIL_BIZ_COMMON_SYSTEM_ERROR2 业务错误
     * （而非 FAIL_SYS_API_NOT_FOUNDED），证明接口名真实存在，业务校验在风控侧。</p>
     * <p>同域已真验：mtop.idle.web.trade.sold.list / mtop.idle.web.trade.bought.list（订单列表），
     * 详情走 mtop.idle.web.trade.order.detail，命名规律对齐。</p>
     */
    public JsonNode getOrderDetail(String orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        return apiClient.callMtop("mtop.idle.web.trade.order.detail", toJson(data));
    }

    /**
     * 发货 — 命名规律候选 mtop.idle.web.trade.order.delivery
     * <p>未真抓验证。已真验同域接口 mtop.idle.web.trade.order.detail（详情），
     * 推测发货走同域 order.delivery，闲鱼 App WebView 域穷举全部 API_NOT_EXIST，
     * 待后续真抓微调。</p>
     */
    public JsonNode delivery(String orderId, String trackingNo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId != null ? orderId : "");
        data.put("trackingNo", trackingNo != null ? trackingNo : "");
        return apiClient.callMtop("mtop.idle.web.trade.order.delivery", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
