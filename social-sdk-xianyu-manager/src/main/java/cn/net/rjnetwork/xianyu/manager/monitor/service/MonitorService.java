package cn.net.rjnetwork.xianyu.manager.monitor.service;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.collect.mapper.CollectMapper;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.RuleMapper;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuKeywordRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MonitorService {

    private final AccountMapper accountMapper;
    private final ProductMapper productMapper;
    private final MessageMapper messageMapper;
    private final OrderMapper orderMapper;
    private final RuleMapper ruleMapper;
    private final CollectMapper collectMapper;

    // 内存缓存统计数据
    private final Map<String, Map<String, Object>> statsCache = new ConcurrentHashMap<>();

    public MonitorService(AccountMapper accountMapper, ProductMapper productMapper,
                          MessageMapper messageMapper, OrderMapper orderMapper,
                          RuleMapper ruleMapper, CollectMapper collectMapper) {
        this.accountMapper = accountMapper;
        this.productMapper = productMapper;
        this.messageMapper = messageMapper;
        this.orderMapper = orderMapper;
        this.ruleMapper = ruleMapper;
        this.collectMapper = collectMapper;
    }

    /**
     * 获取仪表盘统计数据
     */
    @Cacheable(value = "dashboard", key = "'all'")
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 账号统计
        List<XianyuAccount> allAccounts = accountMapper.selectList(null);
        long activeCount = allAccounts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
        long disabledCount = allAccounts.stream().filter(a -> "DISABLED".equals(a.getStatus())).count();
        stats.put("totalAccounts", allAccounts.size());
        stats.put("activeAccounts", activeCount);
        stats.put("disabledAccounts", disabledCount);

        // 商品统计
        List<XianyuProduct> allProducts = productMapper.selectList(null);
        long onSaleCount = allProducts.stream().filter(p -> "ON_SALE".equals(p.getStatus())).count();
        long draftCount = allProducts.stream().filter(p -> "DRAFT".equals(p.getStatus())).count();
        stats.put("totalProducts", allProducts.size());
        stats.put("onSaleProducts", onSaleCount);
        stats.put("draftProducts", draftCount);

        // 消息统计
        long todayMessages = messageMapper.selectList(
                new LambdaQueryWrapper<XianyuMessage>()
                        .ge(XianyuMessage::getMessageTime, java.time.LocalDate.now().atStartOfDay())
        ).size();
        stats.put("todayMessages", todayMessages);

        // 订单统计
        List<XianyuOrder> allOrders = orderMapper.selectList(null);
        long pendingOrders = allOrders.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        long shippedOrders = allOrders.stream().filter(o -> "SHIPPED".equals(o.getStatus())).count();
        stats.put("totalOrders", allOrders.size());
        stats.put("pendingOrders", pendingOrders);
        stats.put("shippedOrders", shippedOrders);

        // 规则统计
        long activeRules = ruleMapper.selectList(
                new LambdaQueryWrapper<XianyuKeywordRule>().eq(XianyuKeywordRule::getEnabled, true)
        ).size();
        stats.put("activeRules", activeRules);

        // 收藏统计
        long totalCollects = collectMapper.selectCount(null);
        stats.put("totalCollects", totalCollects);

        return stats;
    }

    /**
     * 账号维度统计
     */
    public List<Map<String, Object>> getAccountStats() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        return accounts.stream().map(acc -> {
            Map<String, Object> m = new HashMap<>();
            m.put("accountId", acc.getId());
            m.put("accountName", acc.getAccountName());
            m.put("status", acc.getStatus());
            m.put("userId", acc.getUserId());

            // 商品数
            long productCount = productMapper.selectCount(
                    new LambdaQueryWrapper<XianyuProduct>().eq(XianyuProduct::getAccountId, acc.getId())
            );
            m.put("productCount", productCount);

            // 消息数
            long messageCount = messageMapper.selectCount(
                    new LambdaQueryWrapper<XianyuMessage>().eq(XianyuMessage::getAccountId, acc.getId())
            );
            m.put("messageCount", messageCount);

            // 订单数
            long orderCount = orderMapper.selectCount(
                    new LambdaQueryWrapper<XianyuOrder>().eq(XianyuOrder::getAccountId, acc.getId())
            );
            m.put("orderCount", orderCount);

            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 清除缓存
     */
    @CacheEvict(value = "dashboard", allEntries = true)
    public void invalidateCache() {
        statsCache.clear();
    }
}
