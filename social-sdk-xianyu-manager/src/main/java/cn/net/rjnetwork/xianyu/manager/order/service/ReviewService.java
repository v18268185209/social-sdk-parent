package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价与信用服务 — 调 SDK {@code reviewOrder / getReviewList / getUserCredit}。
 * <p>闭环链路：订单完成→评价(买家/卖家互评)→信用画像→影响后续交易决策。
 * 含退款评价（applyRefund/getRefundList/getRefundDetail）。</p>
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AccountMapper accountMapper;
    private final OrderMapper orderMapper;

    public ReviewService(AccountMapper accountMapper, OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
    }

    /** 对指定订单发表评价（rating + content） */
    public Map<String, Object> reviewOrder(Long accountId, String orderId, String rating, String content) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.reviewOrder(orderId, rating, content);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("orderId", orderId);
        ret.put("success", !isRisk(resp));
        ret.put("response", resp);
        log.info("[REVIEW] account={} order={} rating={} success={}", accountId, orderId, rating, ret.get("success"));
        return ret;
    }

    /** 拉评价列表（buyerId 为空时用当前账号 userId；旧账号 userId 为空则从登录 cookie 的 unb 兜底） */
    public JsonNode getReviewList(Long accountId, String buyerId, int page, int pageSize) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        String ratedUid = firstNotBlank(buyerId, acc.getUserId(), getCookieValue(acc.getCookieHeader(), "unb"));
        JsonNode resp = api.getReviewList(ratedUid, String.valueOf(page), String.valueOf(pageSize));
        enrichReviewOrders(resp, acc);
        return resp;
    }

    /** 拉用户信用画像（ userId=null 时取自己） */
    public JsonNode getUserCredit(Long accountId, String userId) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getUserCredit(userId != null ? userId : acc.getUserId());
    }

    /** 申请退款 */
    public Map<String, Object> applyRefund(Long accountId, String orderId, String reason, String amount) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.applyRefund(orderId, reason, amount);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("orderId", orderId);
        ret.put("success", !isRisk(resp));
        ret.put("response", resp);
        return ret;
    }

    /** 退款列表 */
    public JsonNode getRefundList(Long accountId, String disputeStatus, int page, int pageSize) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getRefundList(disputeStatus, String.valueOf(page), String.valueOf(pageSize));
    }

    /** 退款详情 */
    public JsonNode getRefundDetail(Long accountId, String refundId) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getRefundDetail(refundId);
    }

    private void enrichReviewOrders(JsonNode resp, XianyuAccount acc) {
        JsonNode cardList = resp.path("data").path("cardList");
        if (!cardList.isArray()) return;

        List<XianyuOrder> orders = orderMapper.selectList(new QueryWrapper<XianyuOrder>()
                .eq("account_id", acc.getId())
                .eq("deleted", 0)
                .orderByDesc("order_time")
                .last("LIMIT 500"));
        String selfName = firstNotBlank(acc.getDisplayName(), acc.getAccountName(), acc.getUserId(), getCookieValue(acc.getCookieHeader(), "tracknick"));

        for (JsonNode card : cardList) {
            JsonNode cardData = card.path("cardData");
            if (!(cardData instanceof ObjectNode data)) continue;
            XianyuOrder order = findMatchingOrder(data, orders);
            if (order != null) {
                putIfNotBlank(data, "orderId", order.getOrderId());
                putIfNotBlank(data, "itemTitle", order.getItemTitle());
                putIfNotBlank(data, "matchedOrderType", order.getType());
                putIfNotBlank(data, "counterpartyName", order.getCounterpartyName());
                putIfNotBlank(data, "buyerId", order.getBuyerId());
                putIfNotBlank(data, "sellerId", order.getSellerId());
                if ("BOUGHT".equals(order.getType())) {
                    putIfNotBlank(data, "sellerName", order.getCounterpartyName());
                    putIfNotBlank(data, "buyerName", selfName);
                } else if ("SOLD".equals(order.getType())) {
                    putIfNotBlank(data, "sellerName", selfName);
                    putIfNotBlank(data, "buyerName", order.getCounterpartyName());
                }
            } else {
                enrichPartyFromReviewRole(data, selfName);
            }
        }
    }

    private XianyuOrder findMatchingOrder(JsonNode review, List<XianyuOrder> orders) {
        if (orders == null || orders.isEmpty()) return null;
        String itemId = getText(review, "itemId");
        if ("0".equals(itemId)) itemId = null;
        String raterNick = getText(review, "raterUserNick");
        LocalDateTime reviewTime = parseReviewTime(firstNotBlank(getText(review, "gmtCreate"), getText(review, "gmtCreateStr")));

        XianyuOrder best = null;
        int bestScore = 0;
        for (XianyuOrder order : orders) {
            int score = 0;
            if (itemId != null && itemId.equals(order.getItemId())) score += 100;
            if (raterNick != null && raterNick.equals(order.getCounterpartyName())) score += 60;
            if (reviewTime != null && order.getOrderTime() != null) {
                long days = Math.abs(java.time.Duration.between(order.getOrderTime(), reviewTime).toDays());
                if (!order.getOrderTime().isAfter(reviewTime.plusDays(1)) && days <= 120) score += Math.max(1, 40 - (int) days);
            }
            if (score > bestScore) {
                bestScore = score;
                best = order;
            }
        }
        return bestScore >= 60 ? best : null;
    }

    private void enrichPartyFromReviewRole(ObjectNode data, String selfName) {
        String raterNick = getText(data, "raterUserNick");
        String role = firstRateTagText(data.path("rateTagList"));
        if ("卖家".equals(role)) {
            putIfNotBlank(data, "sellerName", raterNick);
            putIfNotBlank(data, "buyerName", selfName);
        } else if ("买家".equals(role)) {
            putIfNotBlank(data, "buyerName", raterNick);
            putIfNotBlank(data, "sellerName", selfName);
        }
    }

    private String firstRateTagText(JsonNode tags) {
        if (!tags.isArray() || tags.isEmpty()) return null;
        return getText(tags.get(0), "text");
    }

    private LocalDateTime parseReviewTime(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            if (v.length() == 10) return LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            return LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getText(JsonNode node, String field) {
        if (node == null || field == null || !node.has(field) || node.path(field).isNull()) return null;
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private void putIfNotBlank(ObjectNode node, String field, String value) {
        if (node == null || field == null || value == null || value.isBlank()) return;
        node.put(field, value);
    }

    private String firstNotBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String getCookieValue(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank() || name == null || name.isBlank()) return null;
        String prefix = name + "=";
        for (String part : cookieHeader.split(";")) {
            String item = part.trim();
            if (item.startsWith(prefix)) {
                String value = item.substring(prefix.length()).trim();
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private boolean isRisk(JsonNode resp) {
        if (resp == null) return false;
        String s = resp.toString();
        return s.contains("FAIL_SYS_USER_VALIDATE") || s.contains("punish") || s.contains("RGV587");
    }

    private XianyuAccount requireAccount(Long accountId) {
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null || acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
            throw new IllegalArgumentException("Account not found or cookie expired, accountId=" + accountId);
        }
        return acc;
    }
}
