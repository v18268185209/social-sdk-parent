package cn.net.rjnetwork.xianyu.manager.market.mapper;

import cn.net.rjnetwork.xianyu.manager.market.model.MarketSnapshot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MarketSnapshotMapper extends BaseMapper<MarketSnapshot> {

    @Select("SELECT * FROM market_snapshot WHERE task_id = #{taskId} ORDER BY snapshot_time DESC LIMIT 1")
    MarketSnapshot selectLatestByTask(@Param("taskId") Long taskId);

    @Select("SELECT * FROM market_snapshot WHERE keyword = #{keyword} AND snapshot_time >= #{since} ORDER BY snapshot_time DESC")
    List<MarketSnapshot> selectByKeywordSince(@Param("keyword") String keyword, @Param("since") LocalDateTime since);

    @Select("SELECT * FROM market_snapshot WHERE task_id = #{taskId} AND snapshot_time >= #{since} ORDER BY snapshot_time")
    List<MarketSnapshot> selectTaskHistory(@Param("taskId") Long taskId, @Param("since") LocalDateTime since);
}
