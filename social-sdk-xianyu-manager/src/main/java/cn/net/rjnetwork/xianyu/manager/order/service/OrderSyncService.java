package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuOrderApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单同步服务 - 按账号从闲鱼 API 拉取订单数据并同步到本地 DB
 */
@Service
public class OrderSyncService {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    /** 最大翻页次数，防止死循环（API 每次默认 10 条） */
    private static final int MAX_PAGES = 50;

    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OrderSyncService(AccountMapper accountMapper, OrderMapper orderMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 同步指定账号的订单（bought + sold），自动翻页直到没有更多数据
     */
    @Transactional
    public SyncResult syncOrders(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return SyncResult.error("账号不存在");
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            return SyncResult.error("账号未设置 Cookie");
        }

        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuOrderApiService orderApi = new XianyuOrderApiService(mtopClient);

            SyncResult result = new SyncResult();

            // 同步我买到的 (bought) — 数据结构: data.items[], data.nextPage, data.lastEndRow
            List<XianyuOrder> boughtOrders = syncBoughtOrders(orderApi, "BOUGHT", accountId);
            upsertOrders(boughtOrders, accountId, "BOUGHT");
            result.boughtCount = boughtOrders.size();

            // 同步我卖出的 (sold) — 数据结构: data.module.items[], module.nextPage, module.lastEndRow
            List<XianyuOrder> soldOrders = syncSoldOrders(orderApi, "SOLD", accountId);
            upsertOrders(soldOrders, accountId, "SOLD");
            result.soldCount = soldOrders.size();

            result.success = true;
            result.totalCount = result.boughtCount + result.soldCount;
            result.syncedAt = LocalDateTime.now();
            return result;

        } catch (Exception e) {
            return SyncResult.error("同步订单失败: " + e.getMessage());
        }
    }

    /**
     * 调试接口：返回 bought/sold API 原始返回的关键信息（解构结构 Helper）
     */
    public Map<String, Object> debugRawResponse(Long accountId) {
        Map<String, Object> debug = new HashMap<>();
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            debug.put("error", "账号不存在");
            return debug;
        }

        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuOrderApiService orderApi = new XianyuOrderApiService(mtopClient);

            // bought 原始结构（第一页）
            JsonNode boughtData = orderApi.getOrderList("1", "10");
            debug.put("bought_raw", boughtData);
            debug.put("bought_page", "1/10");
            debug.put("bought_nextPage", boughtData != null && boughtData.has("data") ? 
                      boughtData.get("data").path("nextPage").asBoolean(false) : false);
            debug.put("bought_totalCount", boughtData != null && boughtData.has("data") ? 
                      boughtData.get("data").path("totalCount").asInt(0) : 0);

            // sold 原始结构（第一页）
            JsonNode soldData = orderApi.getSoldOrderList("1", "10");
            debug.put("sold_raw", soldData);
            debug.put("sold_page", "1/10");
            debug.put("sold_nextPage", soldData != null && soldData.has("data") ? 
                      soldData.get("data").path("module").path("nextPage").asBoolean(false) : false);
            debug.put("sold_totalCount", soldData != null && soldData.has("data") ? 
                      soldData.get("data").path("module").path("totalCount").asInt(0) : 0);

        } catch (Exception e) {
            debug.put("exception", e.getMessage());
        }
        return debug;
    }

    // ===== Bought 订单同步（cursor-based翻页） =====

    /**
     * 同步 bought 订单，自动翻页。
     * API: mtop.idle.web.trade.bought.list
     * 结构: data.items[], data.lastEndRow, data.nextPage
     * 分页参数: 使用 lastEndRow（游标），pageNumber/pageSize 可能不生效
     */
    private List<XianyuOrder> syncBoughtOrders(XianyuOrderApiService orderApi, String type, Long accountId) {
        List<XianyuOrder> allOrders = new ArrayList<>();
        int lastEndRow = 0;
        int consecutiveFullPages = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode response = orderApi.getOrderList(String.valueOf(page), "20");
            if (response == null || !response.has("data")) break;

            JsonNode data = response.get("data");
            JsonNode items = data.path("items");
            if (!items.isArray()) break;

            // 检查是否有下一页
            boolean hasNext = data.path("nextPage").asBoolean(false);
            int newLastEndRow = data.path("lastEndRow").asInt(0);

            for (JsonNode item : items) {
                XianyuOrder order = parseBoughtItem(item, accountId, type);
                if (order.getOrderId() != null && !order.getOrderId().isEmpty()) {
                    allOrders.add(order);
                }
            }

            int count = items.size();
            if (count == 0) break;
            
            // 如果连续两页都返回相同数量的满页，认为API不再支持分页
            if (count >= 10 && consecutiveFullPages >= 2) break;
            if (count >= 10) consecutiveFullPages++;
            else consecutiveFullPages = 0;
            
            // 如果nextPage=false或items不足10个，没有更多数据
            if (!hasNext || count < 10) break;
            
            lastEndRow = newLastEndRow;
        }

        return allOrders;
    }

    /**
     * 同步 sold 订单，自动翻页。
     * API: mtop.taobao.idle.trade.merchant.sold.get
     * 结构: data.module.items[], data.module.lastEndRow, data.module.nextPage
     */
    private List<XianyuOrder> syncSoldOrders(XianyuOrderApiService orderApi, String type, Long accountId) {
        List<XianyuOrder> allOrders = new ArrayList<>();
        int consecutiveFullPages = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode response = orderApi.getSoldOrderList(String.valueOf(page), "20");
            if (response == null || !response.has("data")) break;

            JsonNode data = response.get("data");
            JsonNode module = data.path("module");
            if (!module.isObject()) break;

            JsonNode items = module.path("items");
            if (!items.isArray()) break;

            boolean hasNext = module.path("nextPage").asBoolean(false);
            int lastEndRow = module.path("lastEndRow").asInt(0);
            int totalCount = module.path("totalCount").asInt(0);

            for (JsonNode item : items) {
                XianyuOrder order = parseSoldItem(item, accountId, type);
                if (order.getOrderId() != null && !order.getOrderId().isEmpty()) {
                    allOrders.add(order);
                }
            }

            int count = items.size();
            if (count == 0) break;
            
            // 如果总数为0且没有下一页，结束
            if (totalCount == 0 && !hasNext) break;
            
            // 如果连续两页都返回相同数量的满页，认为API不再支持分页
            if (count >= 10 && consecutiveFullPages >= 2) break;
            if (count >= 10) consecutiveFullPages++;
            else consecutiveFullPages = 0;
            
            if (!hasNext || count < 10) break;
        }

        return allOrders;
    }

    // ===== 订单解析 =====

    /**
     * 解析 bought 订单项
     */
    private XianyuOrder parseBoughtItem(JsonNode item, Long accountId, String type) {
        XianyuOrder order = new XianyuOrder();
        order.setAccountId(accountId);
        order.setType(type);

        // commonData
        JsonNode commonData = item.path("commonData");
        if (commonData.isObject()) {
            // 优先用 orderIdStr 或 orderId
            order.setOrderId(commonData.has("orderIdStr") ? commonData.path("orderIdStr").asText() : getText(commonData, "orderId"));
            order.setTradeStatusEnum(getText(commonData, "tradeStatusEnum"));
            // 卖家标记
            order.setIsSeller(Boolean.TRUE.equals(commonData.path("seller").asText(null)));
        }

        // content.data.detailInfo / priceInfo
        JsonNode contentData = item.path("content").path("data");
        if (contentData.isObject()) {
            JsonNode detailInfo = contentData.path("detailInfo");
            if (detailInfo.isObject()) {
                order.setItemTitle(getText(detailInfo, "auctionTitle"));
            }
            JsonNode priceInfo = contentData.path("priceInfo");
            if (priceInfo.isObject()) {
                String priceStr = getText(priceInfo, "price");
                if (priceStr != null && !priceStr.isEmpty()) {
                    try {
                        order.setAmount(new BigDecimal(priceStr));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // head.data.userInfo + statusViewMsg + createTime
        JsonNode headData = item.path("head").path("data");
        if (headData.isObject()) {
            JsonNode userInfo = headData.path("userInfo");
            if (userInfo.isObject()) {
                order.setCounterpartyName(getText(userInfo, "userNick"));
            }

            // 优先用 tradeStatusEnum 映射状态
            String tradeStatus = getTradeStatusFromCommon(item, "commonData");
            order.setStatus(tradeStatus != null ? tradeStatus : mapStatusFromMsg(getText(headData, "statusViewMsg")));

            String createTime = getText(headData, "createTime");
            if (createTime != null && !createTime.isEmpty()) {
                order.setOrderTime(parseDateTime(createTime));
            }
        }

        return order;
    }

    /**
     * 解析 sold 订单项（结构与 bought 类似但数据路径不同）
     */
    private XianyuOrder parseSoldItem(JsonNode item, Long accountId, String type) {
        return parseBoughtItem(item, accountId, type);
    }

    /**
     * 尝试从 commonData 中提取 tradeStatusEnum
     */
    private String getTradeStatusFromCommon(JsonNode item, String field) {
        JsonNode common = item.path(field);
        if (common.isObject()) {
            return getText(common, "tradeStatusEnum");
        }
        return null;
    }

    /**
     * 将 tradeStatusEnum 映射为标准状态码
     * 真实值来自 API: refund_success, buyer_to_confirm, trade_success 等
     */
    private String mapStatusFromEnum(String enumVal) {
        if (enumVal == null || enumVal.isEmpty()) return "PENDING";

        switch (enumVal) {
            case "trade_success": return "COMPLETED";
            case "buyer_to_confirm": return "PAID"; // 买家待确认收货
            case "refund_success":
            case "refund_refund":
            case "trade_refund": return "REFUNDED";
            case "trade_in_audit":
            case "refund_agree":
            case "refund_process": return "REFUNDING";
            case "trade_closed":
            case "trade_cancelled":
            case "cancel": return "CLOSED";
            case "pending_pay":
            case "waiting_pay":
            case "trade_pending": return "PENDING";
            case "trade_delivered":
            case "sent": return "SHIPPED";
            case "paid":
            case "trade_paid": return "PAID";
            case "trade_suspended": return "PENDING";
            default:
                // 未知枚举，fallback 到状态消息
                return null;
        }
    }

    /**
     * 将 API 状态消息映射到标准状态码（兜底方案）
     */
    private String mapStatusFromMsg(String statusMsg) {
        if (statusMsg == null) return "PENDING";
        if (statusMsg.contains("待付款")) return "PENDING";
        if (statusMsg.contains("待发货") || statusMsg.contains("已付款")) return "PAID";
        if (statusMsg.contains("已发货") || statusMsg.contains("等待见面交易")) return "SHIPPED";
        if (statusMsg.contains("已完成") || statusMsg.contains("交易成功") || statusMsg.contains("交易完成")) return "COMPLETED";
        if (statusMsg.contains("退款中") || statusMsg.contains("协商退款")) return "REFUNDING";
        if (statusMsg.contains("退款成功") || statusMsg.contains("有退款") || statusMsg.contains("已退款")) return "REFUNDED";
        if (statusMsg.contains("已关闭") || statusMsg.contains("交易关闭")) return "CLOSED";
        return "PENDING";
    }

    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(dateStr, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 批量 upsert 订单（按 accountId + orderId 去重）
     */
    private void upsertOrders(List<XianyuOrder> orders, Long accountId, String type) {
        String accountName = accountName(accountId);
        for (XianyuOrder order : orders) {
            if (order.getOrderId() == null || order.getOrderId().isEmpty()) continue;

            XianyuOrder existing = findByAccountIdAndOrderId(accountId, order.getOrderId());
            if (existing != null) {
                boolean statusChanged = !str(existing.getStatus()).equals(str(order.getStatus()));
                boolean titleChanged = !str(existing.getItemTitle()).equals(str(order.getItemTitle()));

                existing.setItemTitle(order.getItemTitle());
                existing.setCounterpartyName(order.getCounterpartyName());
                existing.setAmount(order.getAmount());
                existing.setStatus(order.getStatus());
                existing.setOrderTime(order.getOrderTime());
                existing.setTradeStatusEnum(order.getTradeStatusEnum());
                existing.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateById(existing);

                if (statusChanged || titleChanged) {
                    eventPublisher.publishEvent(new NotifyEvent(
                            statusChanged ? "ORDER_STATUS_CHANGED" : "ORDER_UPDATED",
                            accountId, accountName,
                            Map.of("accountName", accountName, "orderId", order.getOrderId(),
                                    "itemTitle", str(order.getItemTitle()), "status", str(order.getStatus()))));
                }
            } else {
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                orderMapper.insert(order);
                eventPublisher.publishEvent(new NotifyEvent("NEW_ORDER", accountId, accountName,
                        Map.of("accountName", accountName, "orderId", order.getOrderId(),
                                "itemTitle", str(order.getItemTitle()), "amount", str(order.getAmount()),
                                "counterparty", str(order.getCounterpartyName()), "status", str(order.getStatus()))));
            }
        }
    }

    private String accountName(Long accountId) {
        XianyuAccount a = accountMapper.selectById(accountId);
        if (a == null) return String.valueOf(accountId);
        return a.getDisplayName() != null ? a.getDisplayName() : a.getAccountName();
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    /**
     * 按 accountId + orderId 查询
     */
    private XianyuOrder findByAccountIdAndOrderId(Long accountId, String orderId) {
        LambdaQueryWrapper<XianyuOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuOrder::getAccountId, accountId)
                .eq(XianyuOrder::getOrderId, orderId)
                .last("LIMIT 1");
        return orderMapper.selectOne(wrapper);
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asText();
    }

    /**
     * 同步结果
     */
    public static class SyncResult {
        public boolean success;
        public String message;
        public int boughtCount;
        public int soldCount;
        public int totalCount;
        public LocalDateTime syncedAt;

        public SyncResult() {}

        public static SyncResult error(String message) {
            SyncResult r = new SyncResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
