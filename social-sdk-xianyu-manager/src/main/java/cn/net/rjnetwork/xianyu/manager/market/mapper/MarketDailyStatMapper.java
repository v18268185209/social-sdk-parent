package cn.net.rjnetwork.xianyu.manager.market.mapper;

import cn.net.rjnetwork.xianyu.manager.market.model.MarketDailyStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface MarketDailyStatMapper extends BaseMapper<MarketDailyStat> {

    @Select("SELECT * FROM market_daily_stat WHERE keyword = #{keyword} AND stat_date BETWEEN #{from} AND #{to} ORDER BY stat_date")
    List<MarketDailyStat> selectByKeywordAndRange(@Param("keyword") String keyword,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @Select("SELECT * FROM market_daily_stat WHERE keyword = #{keyword} ORDER BY stat_date DESC LIMIT 1")
    MarketDailyStat selectLatest(@Param("keyword") String keyword);

    @Insert("INSERT INTO market_daily_stat (keyword, stat_date, search_volume, competition_score, cpc_price, trend_json, created_at, updated_at, deleted) VALUES (#{keyword}, #{statDate}, #{searchVolume}, #{competitionScore}, #{cpcPrice}, #{trendJson}, #{createdAt}, #{updatedAt}, #{deleted}) ON CONFLICT(keyword, stat_date) DO UPDATE SET search_volume=excluded.search_volume, competition_score=excluded.competition_score, cpc_price=excluded.cpc_price, trend_json=excluded.trend_json, updated_at=excluded.updated_at")
    int upsertDailyStat(MarketDailyStat stat);
}
