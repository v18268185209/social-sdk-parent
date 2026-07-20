package cn.net.rjnetwork.xianyu.manager.monitor.mapper;

import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MonitorResultMapper extends BaseMapper<MonitorResult> {
    @Select("SELECT * FROM monitor_result WHERE task_id = #{taskId} ORDER BY created_at DESC LIMIT #{limit}")
    List<MonitorResult> selectRecent(@Param("taskId") Long taskId, @Param("limit") int limit);
}
