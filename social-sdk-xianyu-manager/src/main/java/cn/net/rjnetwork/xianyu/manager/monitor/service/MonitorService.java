package cn.net.rjnetwork.xianyu.manager.monitor.service;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运营监控面板 - 以账号为主视角
 * 展示：在线/异常账号数、每个账号的上架/下架商品数、今日回复数、商品浏览数
 */
@Service
public class MonitorService {

    private final AccountMapper accountMapper;
    private final ProductMapper productMapper;
    private final MessageMapper messageMapper;
    private final OrderMapper orderMapper;

    public MonitorService(AccountMapper accountMapper, ProductMapper productMapper, MessageMapper messageMapper, OrderMapper orderMapper) {
        this.accountMapper = accountMapper;
        this.productMapper = productMapper;
        this.messageMapper = messageMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 运营仪表盘 - 以账号为主视角
     * 返回结构：overview 汇总 + accounts 每个账号的详细指标
     */
    @Cacheable(value = "dashboard", key = "'all'")
    public Map<String, Object> getDashboardStats() {
        List<XianyuAccount> allAccounts = accountMapper.selectList(null);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        List<Map<String, Object>> accountRows = new ArrayList<>();
        int totalProducts = 0, totalOnSale = 0, totalOffSale = 0, totalDraft = 0;
        int todayReplies = 0, totalViews = 0, totalFavorites = 0;
        int onlineCount = 0, offlineCount = 0, cookieExpiredCount = 0;

        for (XianyuAccount acc : allAccounts) {
            Long accountId = acc.getId();

            // 商品统计（按账号）
            List<XianyuProduct> products = productMapper.selectList(
                    new LambdaQueryWrapper<XianyuProduct>().eq(XianyuProduct::getAccountId, accountId)
            );
            int productCount = products.size();
            int onSale = (int) products.stream().filter(p -> "ON_SALE".equals(p.getStatus())).count();
            int offSale = (int) products.stream().filter(p -> "OFF_SALE".equals(p.getStatus())).count();
            int draft = (int) products.stream().filter(p -> "DRAFT".equals(p.getStatus())).count();
            int views = products.stream().mapToInt(p -> p.getViewCount() != null ? p.getViewCount() : 0).sum();
            int favorites = products.stream().mapToInt(p -> p.getFavoriteCount() != null ? p.getFavoriteCount() : 0).sum();

            // 今日回复数（该账号今日发出的消息）
            int replies = (int) messageMapper.selectList(
                    new LambdaQueryWrapper<XianyuMessage>()
                            .eq(XianyuMessage::getAccountId, accountId)
                            .eq(XianyuMessage::getDirection, "OUTGOING")
                            .ge(XianyuMessage::getMessageTime, todayStart)
            ).size();

            // 账号状态归类
            String status = acc.getStatus();
            boolean isOnline = "ACTIVE".equals(status);
            boolean isCookieExpired = "COOKIE_EXPIRED".equals(status);
            if (isOnline) onlineCount++;
            else if (isCookieExpired) cookieExpiredCount++;
            else offlineCount++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", accountId);
            row.put("accountName", acc.getAccountName());
            row.put("displayName", acc.getDisplayName());
            row.put("avatar", acc.getAvatar());
            row.put("status", status);
            row.put("isOnline", isOnline);
            row.put("cookieExpiresAt", acc.getCookieExpiresAt());
            row.put("productCount", productCount);
            row.put("onSaleCount", onSale);
            row.put("offSaleCount", offSale);
            row.put("draftCount", draft);
            row.put("viewCount", views);
            row.put("favoriteCount", favorites);
            row.put("todayReplies", replies);
            accountRows.add(row);

            totalProducts += productCount;
            totalOnSale += onSale;
            totalOffSale += offSale;
            totalDraft += draft;
            totalViews += views;
            totalFavorites += favorites;
            todayReplies += replies;
        }

        // 汇总概览
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalAccounts", allAccounts.size());
        overview.put("onlineAccounts", onlineCount);
        overview.put("offlineAccounts", offlineCount);
        overview.put("cookieExpiredAccounts", cookieExpiredCount);
        overview.put("totalProducts", totalProducts);
        overview.put("onSaleProducts", totalOnSale);
        overview.put("offSaleProducts", totalOffSale);
        overview.put("draftProducts", totalDraft);
        overview.put("todayReplies", todayReplies);
        overview.put("totalViews", totalViews);
        overview.put("totalFavorites", totalFavorites);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview);
        result.put("accounts", accountRows);
        result.put("orderTrend", buildOrderTrend(allAccounts));
        result.put("messageActivity", buildMessageActivity(allAccounts));
        result.put("accountStatus", buildAccountStatus(offlineCount, cookieExpiredCount, onlineCount));
        result.put("ruleHitStats", buildRuleHitStats(allAccounts));
        return result;
    }

    /**
     * 构建近 N 天订单趋势，用于折线图
     */
    private List<Map<String, Object>> buildOrderTrend(List<XianyuAccount> allAccounts) {
        int days = 14;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> trend = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.format(fmt));
            day.put("sold", 0);
            day.put("bought", 0);
            trend.put(d.toString(), day);
        }
        List<XianyuOrder> allOrders = orderMapper.selectList(null);
        for (XianyuOrder order : allOrders) {
            if (order.getOrderTime() == null) continue;
            LocalDate d = order.getOrderTime().toLocalDate();
            String key = d.toString();
            if (!trend.containsKey(key)) continue;
            Map<String, Object> day = trend.get(key);
            if ("SOLD".equals(order.getType())) {
                day.put("sold", (int) day.get("sold") + 1);
            } else if ("BOUGHT".equals(order.getType())) {
                day.put("bought", (int) day.get("bought") + 1);
            }
        }
        return new ArrayList<>(trend.values());
    }

    /**
     * 构建近 N 天消息活跃度，用于折线图
     */
    private List<Map<String, Object>> buildMessageActivity(List<XianyuAccount> allAccounts) {
        int days = 14;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Object>> trend = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.format(fmt));
            day.put("incoming", 0);
            day.put("outgoing", 0);
            trend.put(d.toString(), day);
        }
        for (XianyuAccount acc : allAccounts) {
            List<XianyuMessage> msgs = messageMapper.selectList(
                    new LambdaQueryWrapper<XianyuMessage>().eq(XianyuMessage::getAccountId, acc.getId())
            );
            for (XianyuMessage msg : msgs) {
                if (msg.getMessageTime() == null) continue;
                LocalDate d = msg.getMessageTime().toLocalDate();
                String key = d.toString();
                if (!trend.containsKey(key)) continue;
                Map<String, Object> day = trend.get(key);
                if ("OUTGOING".equals(msg.getDirection())) {
                    day.put("outgoing", (int) day.get("outgoing") + 1);
                } else {
                    day.put("incoming", (int) day.get("incoming") + 1);
                }
            }
        }
        return new ArrayList<>(trend.values());
    }

    /**
     * 构建账号状态分布，用于饼图
     */
    private List<Map<String, Object>> buildAccountStatus(int offline, int expired, int online) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("name", "在线", "value", online));
        list.add(Map.of("name", "离线", "value", offline));
        list.add(Map.of("name", "Cookie过期", "value", expired));
        return list;
    }

    /**
     * 构建规则命中统计，用于柱状图
     */
    private List<Map<String, Object>> buildRuleHitStats(List<XianyuAccount> allAccounts) {
        // 当前返回各账号规则命中计数（由前端展示），留个占位
        // 后续可从 XianyuMessage 表的 auto_reply=true 字段聚合
        // 当前仅按账号维度统计消息收发
        return Collections.emptyList();
    }

    /**
     * 清除缓存
     */
    @CacheEvict(value = "dashboard", allEntries = true)
    public void invalidateCache() {
        // 缓存已清，无需额外操作
    }
}
