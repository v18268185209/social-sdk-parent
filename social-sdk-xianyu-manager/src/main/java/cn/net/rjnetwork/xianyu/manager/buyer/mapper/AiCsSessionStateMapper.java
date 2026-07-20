package cn.net.rjnetwork.xianyu.manager.buyer.mapper;

import cn.net.rjnetwork.xianyu.manager.buyer.model.AiCsSessionState;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiCsSessionStateMapper extends BaseMapper<AiCsSessionState> {
    @Select("SELECT * FROM ai_cs_session_state WHERE session_id = #{sessionId} LIMIT 1")
    AiCsSessionState selectBySessionId(@Param("sessionId") Long sessionId);
}
