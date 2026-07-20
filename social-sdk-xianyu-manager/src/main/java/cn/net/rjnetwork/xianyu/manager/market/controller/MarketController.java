package cn.net.rjnetwork.xianyu.manager.market.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketDailyStat;
import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import cn.net.rjnetwork.xianyu.manager.market.model.SellerProfile;
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

    public MarketController(PriceHistoryService priceHistoryService, SellerProfileService sellerProfileService) {
        this.priceHistoryService = priceHistoryService;
        this.sellerProfileService = sellerProfileService;
    }

    /** 获取追踪的关键词列表 */
    @GetMapping("/keywords")
    public ApiResponse<List<String>> keywords() {
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
