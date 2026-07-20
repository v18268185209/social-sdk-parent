package cn.net.rjnetwork.xianyu.manager.monitor.mapper;

import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MonitorTaskMapper extends BaseMapper<MonitorTask> {
    @Select("SELECT * FROM monitor_task WHERE status = 'ACTIVE' AND deleted = 0 AND (next_run_at IS NULL OR next_run_at <= #{now}) LIMIT #{limit}")
    List<MonitorTask> selectDueTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("SELECT * FROM monitor_task WHERE account_id = #{accountId} AND deleted = 0 ORDER BY id DESC")
    List<MonitorTask> selectByAccount(@Param("accountId") Long accountId);
}
