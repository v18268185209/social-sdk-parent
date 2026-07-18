package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuOrderApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;

    public OrderSyncService(AccountMapper accountMapper, OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 同步指定账号的订单（bought + sold）
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

            // 同步我买到的 (bought)
            JsonNode boughtData = orderApi.getOrderList(null, null);
            List<XianyuOrder> boughtOrders = parseOrders(boughtData, "BOUGHT", accountId);
            upsertOrders(boughtOrders, accountId, "BOUGHT");
            result.boughtCount = boughtOrders.size();

            // 同步我卖出的 (sold)
            JsonNode soldData = orderApi.getSoldOrderList(null, null);
            List<XianyuOrder> soldOrders = parseOrders(soldData, "SOLD", accountId);
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

            // bought 原始结构
            JsonNode boughtData = orderApi.getOrderList(null, null);
            debug.put("bought_raw", boughtData);
            debug.put("bought_data_path", boughtData != null && boughtData.has("data") ? boughtData.get("data").toString() : "no data");

            // sold 原始结构
            JsonNode soldData = orderApi.getSoldOrderList(null, null);
            debug.put("sold_raw", soldData);
            debug.put("sold_data_path", soldData != null && soldData.has("data") ? soldData.get("data").toString() : "no data");

        } catch (Exception e) {
            debug.put("exception", e.getMessage());
        }
        return debug;
    }

    /**
     * 解析 API 返回的订单列表
     */
    private List<XianyuOrder> parseOrders(JsonNode response, String type, Long accountId) {
        List<XianyuOrder> orders = new ArrayList<>();
        if (response == null || !response.has("data")) {
            return orders;
        }

        JsonNode data = response.get("data");
        JsonNode items = data.path("items");
        if (!items.isArray()) {
            return orders;
        }

        for (JsonNode item : items) {
            XianyuOrder order = new XianyuOrder();
            order.setAccountId(accountId);
            order.setType(type);

            // commonData
            JsonNode commonData = item.path("commonData");
            if (commonData.isObject()) {
                order.setOrderId(getText(commonData, "orderId"));
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
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            // head.data.userInfo - 对手方昵称
            JsonNode headData = item.path("head").path("data");
            if (headData.isObject()) {
                JsonNode userInfo = headData.path("userInfo");
                if (userInfo.isObject()) {
                    order.setCounterpartyName(getText(userInfo, "userNick"));
                }

                // 订单状态
                String statusMsg = getText(headData, "statusViewMsg");
                if (statusMsg != null && !statusMsg.isEmpty()) {
                    order.setStatus(mapStatus(statusMsg));
                }

                // 订单创建时间
                String createTime = getText(headData, "createTime");
                if (createTime != null && !createTime.isEmpty()) {
                    order.setOrderTime(parseDateTime(createTime));
                }
            }

            orders.add(order);
        }

        return orders;
    }

    /**
     * 将 API 状态消息映射到标准状态码
     */
    private String mapStatus(String statusMsg) {
        if (statusMsg == null) return "PENDING";
        if (statusMsg.contains("待付款")) return "PENDING";
        if (statusMsg.contains("待发货")) return "PAID";
        if (statusMsg.contains("已发货")) return "SHIPPED";
        if (statusMsg.contains("已完成")) return "COMPLETED";
        if (statusMsg.contains("退款")) return "REFUNDING";
        if (statusMsg.contains("已关闭")) return "CLOSED";
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
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 批量 upsert 订单（按 orderId 去重）
     */
    private void upsertOrders(List<XianyuOrder> orders, Long accountId, String type) {
        for (XianyuOrder order : orders) {
            if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
                continue;
            }

            // 检查是否已存在（同一 accountId + orderId）
            XianyuOrder existing = findByAccountIdAndOrderId(accountId, order.getOrderId());
            if (existing != null) {
                // 更新
                existing.setItemTitle(order.getItemTitle());
                existing.setCounterpartyName(order.getCounterpartyName());
                existing.setAmount(order.getAmount());
                existing.setStatus(order.getStatus());
                existing.setOrderTime(order.getOrderTime());
                existing.setUpdatedAt(LocalDateTime.now());
                orderMapper.updateById(existing);
            } else {
                // 新增
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                orderMapper.insert(order);
            }
        }
    }

    /**
     * 按 accountId + orderId 查询
     */
    private XianyuOrder findByAccountIdAndOrderId(Long accountId, String orderId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuOrder> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
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

        public SyncResult() {
        }

        public static SyncResult error(String message) {
            SyncResult r = new SyncResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
