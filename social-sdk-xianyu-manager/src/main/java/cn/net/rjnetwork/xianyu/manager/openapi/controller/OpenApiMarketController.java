package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiMarketDailyStatVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiSellerProfileVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiPriceHistoryVO;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.market.mapper.MarketDailyStatMapper;
import cn.net.rjnetwork.xianyu.manager.market.mapper.PriceHistoryMapper;
import cn.net.rjnetwork.xianyu.manager.market.mapper.SellerProfileMapper;
import cn.net.rjnetwork.xianyu.manager.market.model.MarketDailyStat;
import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import cn.net.rjnetwork.xianyu.manager.market.model.SellerProfile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 市场情报域对外接口：全局数据集（无账号作用域过滤），含每日关键词统计、卖家画像、价格历史。
 */
@RestController
@RequestMapping("/openapi/v1/market")
public class OpenApiMarketController {

    private final MarketDailyStatMapper dailyStatMapper;
    private final SellerProfileMapper sellerProfileMapper;
    private final PriceHistoryMapper priceHistoryMapper;

    public OpenApiMarketController(MarketDailyStatMapper dailyStatMapper,
                                   SellerProfileMapper sellerProfileMapper,
                                   PriceHistoryMapper priceHistoryMapper) {
        this.dailyStatMapper = dailyStatMapper;
        this.sellerProfileMapper = sellerProfileMapper;
        this.priceHistoryMapper = priceHistoryMapper;
    }

    // ---------- 每日关键词价格统计 ----------
    @GetMapping("/daily-stats")
    public OpenApiResponse<List<OpenApiMarketDailyStatVO>> listDailyStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LambdaQueryWrapper<MarketDailyStat> qw = new LambdaQueryWrapper<MarketDailyStat>()
                .orderByDesc(MarketDailyStat::getStatDate);
        if (keyword != null && !keyword.isBlank()) qw.eq(MarketDailyStat::getKeyword, keyword);
        if (from != null) qw.ge(MarketDailyStat::getStatDate, from);
        if (to != null) qw.le(MarketDailyStat::getStatDate, to);

        List<OpenApiMarketDailyStatVO> result = dailyStatMapper.selectList(qw).stream()
                .map(this::toDailyStatVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    // ---------- 卖家画像 ----------
    @GetMapping("/sellers")
    public OpenApiResponse<List<OpenApiSellerProfileVO>> listSellers(
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SellerProfile> qw = new LambdaQueryWrapper<SellerProfile>()
                .orderByDesc(SellerProfile::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) qw.like(SellerProfile::getNickname, keyword);

        List<OpenApiSellerProfileVO> result = sellerProfileMapper.selectList(qw).stream()
                .map(this::toSellerVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    // ---------- 价格历史 ----------
    @GetMapping("/price-history")
    public OpenApiResponse<List<OpenApiPriceHistoryVO>> listPriceHistory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String itemId) {
        LambdaQueryWrapper<PriceHistory> qw = new LambdaQueryWrapper<PriceHistory>()
                .orderByDesc(PriceHistory::getSnapshotTime);
        if (keyword != null && !keyword.isBlank()) qw.eq(PriceHistory::getKeyword, keyword);
        if (itemId != null && !itemId.isBlank()) qw.eq(PriceHistory::getItemId, itemId);

        List<OpenApiPriceHistoryVO> result = priceHistoryMapper.selectList(qw).stream()
                .map(this::toPriceHistoryVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    // ---------- 内部辅助转换为 VO ----------
    private OpenApiMarketDailyStatVO toDailyStatVo(MarketDailyStat s) {
        OpenApiMarketDailyStatVO vo = new OpenApiMarketDailyStatVO();
        vo.setId(s.getId());
        vo.setKeyword(s.getKeyword());
        vo.setStatDate(s.getStatDate());
        vo.setMinPrice(s.getMinPrice());
        vo.setMaxPrice(s.getMaxPrice());
        vo.setAvgPrice(s.getAvgPrice());
        vo.setMedianPrice(s.getMedianPrice());
        vo.setP25Price(s.getP25Price());
        vo.setP75Price(s.getP75Price());
        vo.setVolume(s.getVolume());
        vo.setTotalListings(s.getTotalListings());
        vo.setSampledCount(s.getSampledCount());
        vo.setCreatedAt(s.getCreatedAt());
        return vo;
    }

    private OpenApiSellerProfileVO toSellerVo(SellerProfile s) {
        OpenApiSellerProfileVO vo = new OpenApiSellerProfileVO();
        vo.setId(s.getId());
        vo.setUserId(s.getUserId());
        vo.setNickname(s.getNickname());
        vo.setAvatar(s.getAvatar());
        vo.setShopLevel(s.getShopLevel());
        vo.setCreditScore(s.getCreditScore());
        vo.setFollowers(s.getFollowers());
        vo.setFollowing(s.getFollowing());
        vo.setSoldCount(s.getSoldCount());
        vo.setOnSaleCount(s.getOnSaleCount());
        vo.setIntroduction(s.getIntroduction());
        vo.setIpLocation(s.getIpLocation());
        vo.setLastActiveAt(s.getLastActiveAt());
        vo.setProfileSyncedAt(s.getProfileSyncedAt());
        vo.setCreatedAt(s.getCreatedAt());
        return vo;
    }

    private OpenApiPriceHistoryVO toPriceHistoryVo(PriceHistory p) {
        OpenApiPriceHistoryVO vo = new OpenApiPriceHistoryVO();
        vo.setId(p.getId());
        vo.setKeyword(p.getKeyword());
        vo.setItemId(p.getItemId());
        vo.setItemTitle(p.getItemTitle());
        vo.setPrice(p.getPrice());
        vo.setCurrency(p.getCurrency());
        vo.setSellerId(p.getSellerId());
        vo.setSellerNickname(p.getSellerNickname());
        vo.setSellerCreditScore(p.getSellerCreditScore());
        vo.setItemCondition(p.getItemCondition());
        vo.setLocation(p.getLocation());
        vo.setListingTime(p.getListingTime());
        vo.setSnapshotId(p.getSnapshotId());
        vo.setSnapshotTime(p.getSnapshotTime());
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }
}
