package cn.net.rjnetwork.xianyu.manager.market.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProductApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.market.mapper.MarketKeywordMapper;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketKeyword;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketSnapshot;
import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 市场情报关键词服务 — 轻量管理追踪关键词 + 抓取逻辑
 */
@Service
public class MarketKeywordService {

    private static final Logger logger = LoggerFactory.getLogger(MarketKeywordService.class);

    private final MarketKeywordMapper keywordMapper;
    private final AccountMapper accountMapper;
    private final PriceHistoryService priceHistoryService;
    private final cn.net.rjnetwork.xianyu.manager.market.mapper.MarketSnapshotMapper snapshotMapper;

    public MarketKeywordService(MarketKeywordMapper keywordMapper,
                                AccountMapper accountMapper,
                                PriceHistoryService priceHistoryService,
                                cn.net.rjnetwork.xianyu.manager.market.mapper.MarketSnapshotMapper snapshotMapper) {
        this.keywordMapper = keywordMapper;
        this.accountMapper = accountMapper;
        this.priceHistoryService = priceHistoryService;
        this.snapshotMapper = snapshotMapper;
    }

    /**
     * 添加追踪关键词
     */
    @Transactional
    public MarketKeyword addKeyword(String keyword, Integer crawlIntervalMinutes) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        keyword = keyword.trim();

        // 检查是否已存在
        MarketKeyword existing = keywordMapper.selectByKeyword(keyword);
        if (existing != null) {
            if ("PAUSED".equals(existing.getStatus())) {
                existing.setStatus("ACTIVE");
                existing.setUpdatedAt(LocalDateTime.now());
                keywordMapper.updateById(existing);
            }
            return existing;
        }

        MarketKeyword mk = new MarketKeyword();
        mk.setKeyword(keyword);
        mk.setStatus("ACTIVE");
        mk.setCrawlIntervalMinutes(crawlIntervalMinutes != null ? crawlIntervalMinutes : 30);
        mk.setDeleted(0);
        mk.setCreatedAt(LocalDateTime.now());
        mk.setUpdatedAt(LocalDateTime.now());
        keywordMapper.insert(mk);
        return mk;
    }

    /**
     * 暂停追踪
     */
    @Transactional
    public MarketKeyword pauseKeyword(String keyword) {
        MarketKeyword mk = keywordMapper.selectByKeyword(keyword);
        if (mk == null) {
            throw new IllegalArgumentException("关键词不存在: " + keyword);
        }
        mk.setStatus("PAUSED");
        mk.setUpdatedAt(LocalDateTime.now());
        keywordMapper.updateById(mk);
        return mk;
    }

    /**
     * 恢复追踪
     */
    @Transactional
    public MarketKeyword resumeKeyword(String keyword) {
        MarketKeyword mk = keywordMapper.selectByKeyword(keyword);
        if (mk == null) {
            throw new IllegalArgumentException("关键词不存在: " + keyword);
        }
        mk.setStatus("ACTIVE");
        mk.setUpdatedAt(LocalDateTime.now());
        keywordMapper.updateById(mk);
        return mk;
    }

    /**
     * 删除追踪关键词（软删除）
     */
    @Transactional
    public void deleteKeyword(String keyword) {
        MarketKeyword mk = keywordMapper.selectByKeyword(keyword);
        if (mk != null) {
            mk.setDeleted(1);
            mk.setUpdatedAt(LocalDateTime.now());
            keywordMapper.updateById(mk);
        }
    }

    /**
     * 获取所有未删除的追踪关键词
     */
    public List<MarketKeyword> listAll() {
        return keywordMapper.selectAllActive();
    }

    /**
     * 获取所有 ACTIVE 状态的关键词
     */
    public List<MarketKeyword> listActive() {
        LambdaQueryWrapper<MarketKeyword> w = new LambdaQueryWrapper<>();
        w.eq(MarketKeyword::getStatus, "ACTIVE")
         .eq(MarketKeyword::getDeleted, 0);
        return keywordMapper.selectList(w);
    }

    /**
     * 执行抓取指定关键词（通过首个可用账号）
     * @return 抓取到的商品数，失败返回 -1
     */
    public int crawlKeyword(MarketKeyword keyword) {
        // 找一个可用账号
        List<XianyuAccount> accounts = accountMapper.selectList(
                new LambdaQueryWrapper<XianyuAccount>()
                        .eq(XianyuAccount::getDeleted, 0)
                        .isNotNull(XianyuAccount::getCookieHeader)
                        .ne(XianyuAccount::getCookieHeader, "")
                        .last("LIMIT 1"));

        if (accounts.isEmpty()) {
            logger.warn("无可用账号抓取关键词: {}", keyword.getKeyword());
            return -1;
        }

        XianyuAccount account = accounts.get(0);
        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);

            // 激活搜索
            productApi.activateSearch(keyword.getKeyword());

            // 搜索商品
            JsonNode searchResult = productApi.searchProducts(keyword.getKeyword(), "1", "30");

            MarketSnapshot snapshot = new MarketSnapshot();
            snapshot.setTaskId(null);  // 市场情报抓取不关联监控任务
            snapshot.setKeyword(keyword.getKeyword());
            snapshot.setAccountId(account.getId());
            snapshot.setRawData(searchResult != null ? searchResult.toString() : "");
            snapshot.setSnapshotTime(LocalDateTime.now());
            snapshot.setDeleted(0);
            snapshot.setCreatedAt(LocalDateTime.now());

            int totalResults = 0;
            List<PriceHistory> priceRecords = new ArrayList<>();

            if (searchResult != null && searchResult.has("data")) {
                JsonNode data = searchResult.get("data");
                JsonNode items = data.has("items") ? data.get("items") :
                                 data.has("resultList") ? data.get("resultList") : null;

                if (items != null && items.isArray()) {
                    totalResults = items.size();
                    snapshot.setTotalResults(totalResults);

                    for (JsonNode item : items) {
                        try {
                            String itemId = getText(item, "itemId", "id", "item_id");
                            String title = getText(item, "title", "name", "itemTitle");
                            if (itemId == null && title == null) continue;

                            PriceHistory ph = new PriceHistory();
                            ph.setKeyword(keyword.getKeyword());
                            ph.setItemId(itemId != null ? itemId : java.util.UUID.randomUUID().toString());
                            ph.setItemTitle(title != null ? title : "未知商品");
                            ph.setPrice(parseDouble(item, "price", "currentPrice", "salePrice"));
                            ph.setSellerNickname(getText(item, "sellerNickname", "sellerName", "nickname"));
                            ph.setSellerCreditScore(parseInt(item, "sellerCreditScore", "creditScore"));
                            ph.setSnapshotId(snapshot.getId());
                            ph.setSnapshotTime(LocalDateTime.now());
                            ph.setDeleted(0);
                            ph.setCreatedAt(LocalDateTime.now());
                            priceRecords.add(ph);
                        } catch (Exception e) {
                            logger.warn("处理商品失败: {}", e.getMessage());
                        }
                    }
                }
            }

            // 先插入 snapshot 获取 id
            snapshotMapper.insert(snapshot);

            // 更新 snapshot_id 并批量写入 price_history
            for (PriceHistory ph : priceRecords) {
                ph.setSnapshotId(snapshot.getId());
            }
            if (!priceRecords.isEmpty()) {
                priceHistoryService.batchRecordPriceHistory(keyword.getKeyword(), snapshot.getId(), priceRecords);
            }

            // 更新关键词抓取状态
            keyword.setLastCrawlAt(LocalDateTime.now());
            keyword.setLastCrawlResultCount(totalResults);
            keyword.setUpdatedAt(LocalDateTime.now());
            keywordMapper.updateById(keyword);

            logger.info("关键词 [{}] 抓取完成，共 {} 条商品", keyword.getKeyword(), totalResults);
            return totalResults;

        } catch (Exception e) {
            logger.error("抓取关键词 [{}] 失败: {}", keyword.getKeyword(), e.getMessage());
            keyword.setLastCrawlAt(LocalDateTime.now());
            keyword.setLastCrawlResultCount(-1);
            keyword.setUpdatedAt(LocalDateTime.now());
            keywordMapper.updateById(keyword);
            return -1;
        }
    }

    private String getText(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) return node.get(f).asText();
        }
        return null;
    }

    private Double parseDouble(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) {
                try { return node.get(f).asDouble(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Integer parseInt(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) {
                try { return node.get(f).asInt(); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
