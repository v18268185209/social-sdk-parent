package cn.net.rjnetwork.xianyu.manager.market.task;

import cn.net.rjnetwork.xianyu.manager.market.service.PriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class MarketDailyComputeTask {

    private static final Logger logger = LoggerFactory.getLogger(MarketDailyComputeTask.class);
    private final PriceHistoryService priceHistoryService;

    public MarketDailyComputeTask(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    @Scheduled(cron = "0 17 2 * * ?")
    public void computeYesterday() {
        logger.info("开始计算市场每日统计...");
        List<String> keywords = priceHistoryService.getTrackedKeywords();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        for (String kw : keywords) {
            try {
                priceHistoryService.computeDailyStat(kw, yesterday);
            } catch (Exception e) {
                logger.warn("计算 {} 统计失败: {}", kw, e.getMessage());
            }
        }
        logger.info("市场每日统计计算完成，共 {} 个关键词", keywords.size());
    }
}
