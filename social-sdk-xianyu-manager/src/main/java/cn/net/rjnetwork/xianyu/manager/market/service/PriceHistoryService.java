package cn.net.rjnetwork.xianyu.manager.market.service;

import cn.net.rjnetwork.xianyu.manager.market.mapper.MarketDailyStatMapper;
import cn.net.rjnetwork.xianyu.manager.market.mapper.MarketSnapshotMapper;
import cn.net.rjnetwork.xianyu.manager.market.mapper.PriceHistoryMapper;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketDailyStat;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketSnapshot;
import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 价格历史 & 市场情报服务
 */
@Service
public class PriceHistoryService {

    private final PriceHistoryMapper priceMapper;
    private final MarketSnapshotMapper snapshotMapper;
    private final MarketDailyStatMapper dailyStatMapper;

    public PriceHistoryService(PriceHistoryMapper priceMapper, MarketSnapshotMapper snapshotMapper, MarketDailyStatMapper dailyStatMapper) {
        this.priceMapper = priceMapper;
        this.snapshotMapper = snapshotMapper;
        this.dailyStatMapper = dailyStatMapper;
    }

    /** 记录一次搜索快照 */
    public MarketSnapshot recordSnapshot(Long taskId, String keyword, Long accountId, int totalResults, String rawData) {
        MarketSnapshot s = new MarketSnapshot();
        s.setTaskId(taskId);
        s.setKeyword(keyword);
        s.setAccountId(accountId);
        s.setTotalResults(totalResults);
        s.setRawData(rawData);
        s.setSnapshotTime(LocalDateTime.now());
        s.setDeleted(0);
        snapshotMapper.insert(s);
        return s;
    }

    /** 批量记录价格历史 */
    public void batchRecordPriceHistory(String keyword, Long snapshotId, List<PriceHistory> records) {
        if (records == null || records.isEmpty()) return;
        for (PriceHistory r : records) {
            r.setKeyword(keyword);
            r.setSnapshotId(snapshotId);
            r.setSnapshotTime(LocalDateTime.now());
            r.setDeleted(0);
            priceMapper.insert(r);
        }
    }

    /** 获取关键词价格趋势（最近 N 天） */
    public List<MarketDailyStat> getPriceTrend(String keyword, int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        LocalDate to = LocalDate.now();
        return dailyStatMapper.selectByKeywordAndRange(keyword, from, to);
    }

    /** 获取最新市场统计 */
    public MarketDailyStat getLatestStat(String keyword) {
        return dailyStatMapper.selectLatest(keyword);
    }

    /** 计算并更新每日统计 */
    public void computeDailyStat(String keyword, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<PriceHistory> records = priceMapper.selectByKeywordSince(keyword, start);
        // 过滤到当天
        records = records.stream()
                .filter(r -> r.getSnapshotTime() != null && r.getSnapshotTime().isBefore(end))
                .collect(Collectors.toList());

        if (records.isEmpty()) return;

        List<Double> prices = records.stream()
                .map(PriceHistory::getPrice)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        if (prices.isEmpty()) return;

        MarketDailyStat stat = new MarketDailyStat();
        stat.setKeyword(keyword);
        stat.setStatDate(date);
        stat.setMinPrice(prices.get(0));
        stat.setMaxPrice(prices.get(prices.size() - 1));
        stat.setAvgPrice(prices.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        stat.setMedianPrice(percentile(prices, 50));
        stat.setP25Price(percentile(prices, 25));
        stat.setP75Price(percentile(prices, 75));
        stat.setSampledCount(prices.size());
        stat.setVolume(records.size());
        stat.setTotalListings(records.size());
        stat.setDeleted(0);

        // upsert
        MarketDailyStat existing = dailyStatMapper.selectLatest(keyword);
        if (existing != null && existing.getStatDate() != null && existing.getStatDate().equals(date)) {
            existing.setMinPrice(stat.getMinPrice());
            existing.setMaxPrice(stat.getMaxPrice());
            existing.setAvgPrice(stat.getAvgPrice());
            existing.setMedianPrice(stat.getMedianPrice());
            existing.setP25Price(stat.getP25Price());
            existing.setP75Price(stat.getP75Price());
            existing.setSampledCount(stat.getSampledCount());
            existing.setVolume(stat.getVolume());
            existing.setTotalListings(stat.getTotalListings());
            existing.setUpdatedAt(LocalDateTime.now());
            dailyStatMapper.updateById(existing);
        } else {
            dailyStatMapper.insert(stat);
        }
    }

    private double percentile(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    /** 获取商品历史价格 */
    public List<PriceHistory> getItemHistory(String itemId) {
        return priceMapper.selectItemHistory(itemId);
    }

    /** 获取关键词下的价格分布 */
    public Map<String, Object> getPriceDistribution(String keyword, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<PriceHistory> records = priceMapper.selectByKeywordSince(keyword, since);

        List<Double> prices = records.stream()
                .map(PriceHistory::getPrice)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword", keyword);
        result.put("days", days);
        result.put("sampleCount", prices.size());

        if (!prices.isEmpty()) {
            result.put("min", prices.get(0));
            result.put("max", prices.get(prices.size() - 1));
            result.put("avg", prices.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            result.put("median", percentile(prices, 50));
            result.put("p25", percentile(prices, 25));
            result.put("p75", percentile(prices, 75));
        }

        return result;
    }

    /** 获取所有追踪的关键词 */
    public List<String> getTrackedKeywords() {
        LambdaQueryWrapper<MarketSnapshot> w = new LambdaQueryWrapper<MarketSnapshot>()
                .select(MarketSnapshot::getKeyword)
                .groupBy(MarketSnapshot::getKeyword)
                .eq(MarketSnapshot::getDeleted, 0);
        List<MarketSnapshot> list = snapshotMapper.selectList(w);
        return list.stream().map(MarketSnapshot::getKeyword).distinct().collect(Collectors.toList());
    }
}
