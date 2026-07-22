package cn.net.rjnetwork.xianyu.manager.market.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketDailyStat;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketKeyword;
import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import cn.net.rjnetwork.xianyu.manager.market.model.SellerProfile;
import cn.net.rjnetwork.xianyu.manager.market.service.MarketKeywordService;
import cn.net.rjnetwork.xianyu.manager.market.service.PriceHistoryService;
import cn.net.rjnetwork.xianyu.manager.market.service.SellerProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 市场情报 API
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final PriceHistoryService priceHistoryService;
    private final SellerProfileService sellerProfileService;
    private final MarketKeywordService marketKeywordService;

    public MarketController(PriceHistoryService priceHistoryService,
                            SellerProfileService sellerProfileService,
                            MarketKeywordService marketKeywordService) {
        this.priceHistoryService = priceHistoryService;
        this.sellerProfileService = sellerProfileService;
        this.marketKeywordService = marketKeywordService;
    }

    // ===== 关键词管理（独立入口） =====

    /** 获取所有追踪的关键词（含状态、抓取时间等详细信息） */
    @GetMapping("/keywords")
    public ApiResponse<List<MarketKeyword>> keywords() {
        return ApiResponse.success(marketKeywordService.listAll());
    }

    /** 添加追踪关键词 */
    @PostMapping("/keywords")
    public ApiResponse<MarketKeyword> addKeyword(@RequestParam String keyword,
                                                  @RequestParam(required = false, defaultValue = "30") Integer interval) {
        return ApiResponse.success(marketKeywordService.addKeyword(keyword, interval));
    }

    /** 暂停追踪关键词 */
    @PostMapping("/keywords/{keyword}/pause")
    public ApiResponse<MarketKeyword> pauseKeyword(@PathVariable String keyword) {
        return ApiResponse.success(marketKeywordService.pauseKeyword(keyword));
    }

    /** 恢复追踪关键词 */
    @PostMapping("/keywords/{keyword}/resume")
    public ApiResponse<MarketKeyword> resumeKeyword(@PathVariable String keyword) {
        return ApiResponse.success(marketKeywordService.resumeKeyword(keyword));
    }

    /** 删除追踪关键词 */
    @DeleteMapping("/keywords/{keyword}")
    public ApiResponse<String> deleteKeyword(@PathVariable String keyword) {
        marketKeywordService.deleteKeyword(keyword);
        return ApiResponse.success("deleted");
    }

    /** 手动触发抓取指定关键词 */
    @PostMapping("/keywords/{keyword}/crawl")
    public ApiResponse<Integer> crawlKeyword(@PathVariable String keyword) {
        MarketKeyword mk = marketKeywordService.listActive().stream()
                .filter(k -> k.getKeyword().equals(keyword))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("关键词不存在: " + keyword));
        int count = marketKeywordService.crawlKeyword(mk);
        return ApiResponse.success(count);
    }

    // ===== 价格趋势 & 分布 =====

    /** 获取关键词价格趋势（兼容旧接口，返回 String 列表） */
    @GetMapping("/trend-keywords")
    public ApiResponse<List<String>> trendKeywords() {
        return ApiResponse.success(priceHistoryService.getTrackedKeywords());
    }

    /** 获取关键词价格趋势 */
    @GetMapping("/trend/{keyword}")
    public ApiResponse<List<MarketDailyStat>> trend(@PathVariable String keyword,
                                                     @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(priceHistoryService.getPriceTrend(keyword, days));
    }

    /** 获取关键词价格分布 */
    @GetMapping("/distribution/{keyword}")
    public ApiResponse<Map<String, Object>> distribution(@PathVariable String keyword,
                                                          @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(priceHistoryService.getPriceDistribution(keyword, days));
    }

    /** 获取最新市场统计 */
    @GetMapping("/latest/{keyword}")
    public ApiResponse<MarketDailyStat> latest(@PathVariable String keyword) {
        return ApiResponse.success(priceHistoryService.getLatestStat(keyword));
    }

    /** 获取商品历史价格 */
    @GetMapping("/item/{itemId}")
    public ApiResponse<List<PriceHistory>> itemHistory(@PathVariable String itemId) {
        return ApiResponse.success(priceHistoryService.getItemHistory(itemId));
    }

    /** 触发每日统计计算 */
    @PostMapping("/compute-daily")
    public ApiResponse<String> computeDaily(@RequestParam String keyword,
                                             @RequestParam(required = false) String date) {
        java.time.LocalDate d = date != null ? java.time.LocalDate.parse(date) : java.time.LocalDate.now();
        priceHistoryService.computeDailyStat(keyword, d);
        return ApiResponse.success("computed");
    }

    // ===== 卖家画像 =====

    /** 抓取卖家画像 */
    @PostMapping("/seller-fetch/{userId}")
    public ApiResponse<SellerProfile> fetchSeller(@PathVariable String userId) {
        SellerProfile p = sellerProfileService.fetchSellerProfile(userId);
        if (p == null) return ApiResponse.error("抓取失败");
        return ApiResponse.success(p);
    }

    @GetMapping("/seller/{userId}")
    public ApiResponse<SellerProfile> getSeller(@PathVariable String userId) {
        return ApiResponse.success(sellerProfileService.getByUserId(userId));
    }

    @GetMapping("/seller-search")
    public ApiResponse<List<SellerProfile>> searchSeller(@RequestParam String keyword) {
        return ApiResponse.success(sellerProfileService.search(keyword));
    }
}
