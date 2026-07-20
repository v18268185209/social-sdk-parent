package cn.net.rjnetwork.xianyu.manager.market.mapper;

import cn.net.rjnetwork.xianyu.manager.market.model.PriceHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PriceHistoryMapper extends BaseMapper<PriceHistory> {

    @Select("SELECT * FROM price_history WHERE keyword = #{keyword} AND snapshot_time >= #{since} ORDER BY snapshot_time")
    List<PriceHistory> selectByKeywordSince(@Param("keyword") String keyword, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM price_history WHERE item_id = #{itemId} ORDER BY snapshot_time DESC LIMIT 30")
    List<PriceHistory> selectItemHistory(@Param("itemId") String itemId);

    @Select("SELECT AVG(price) as avg_price FROM price_history WHERE keyword = #{keyword} AND snapshot_time >= #{since}")
    Double selectAvgPrice(@Param("keyword") String keyword, @Param("since") LocalDateTime since);
}
