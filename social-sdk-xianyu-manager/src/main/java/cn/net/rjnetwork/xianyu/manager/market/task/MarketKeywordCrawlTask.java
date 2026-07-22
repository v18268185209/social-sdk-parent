package cn.net.rjnetwork.xianyu.manager.market.task;

import cn.net.rjnetwork.xianyu.manager.market.model.MarketKeyword;
import cn.net.rjnetwork.xianyu.manager.market.service.MarketKeywordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场情报关键词抓取定时任务 — 遍历 ACTIVE 关键词，按各自间隔抓取
 */
@Component
public class MarketKeywordCrawlTask {

    private static final Logger logger = LoggerFactory.getLogger(MarketKeywordCrawlTask.class);

    private final MarketKeywordService marketKeywordService;

    public MarketKeywordCrawlTask(MarketKeywordService marketKeywordService) {
        this.marketKeywordService = marketKeywordService;
    }

    /**
     * 每 5 分钟检查一次，抓取到达间隔的关键词
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void crawlDueKeywords() {
        List<MarketKeyword> activeKeywords = marketKeywordService.listActive();
        if (activeKeywords.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (MarketKeyword kw : activeKeywords) {
            try {
                // 判断是否到达抓取间隔
                if (kw.getLastCrawlAt() != null && kw.getCrawlIntervalMinutes() != null) {
                    long minutesSinceLast = java.time.Duration.between(kw.getLastCrawlAt(), now).toMinutes();
                    if (minutesSinceLast < kw.getCrawlIntervalMinutes()) {
                        continue;  // 还没到间隔，跳过
                    }
                }
                logger.info("开始抓取关键词: {}", kw.getKeyword());
                marketKeywordService.crawlKeyword(kw);
            } catch (Exception e) {
                logger.error("抓取关键词 [{}] 失败: {}", kw.getKeyword(), e.getMessage());
            }
        }
    }
}
