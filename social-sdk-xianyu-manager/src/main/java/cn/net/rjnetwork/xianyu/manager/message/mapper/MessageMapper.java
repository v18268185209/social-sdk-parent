package cn.net.rjnetwork.xianyu.manager.message.mapper;

import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<XianyuMessage> {

    @Select("SELECT DISTINCT session_id FROM xianyu_message WHERE account_id = #{accountId} AND deleted = 0 ORDER BY message_time DESC")
    List<String> selectDistinctSessions(Long accountId);

    @Select("SELECT * FROM (SELECT * FROM xianyu_message WHERE account_id = #{accountId} AND session_id = #{sessionId} AND deleted = 0 ORDER BY message_time DESC, id DESC LIMIT #{limit}) t ORDER BY t.message_time ASC, t.id ASC")
    List<XianyuMessage> selectBySession(Long accountId, String sessionId, int limit);

    @Select("SELECT * FROM xianyu_message WHERE account_id = #{accountId} AND session_id = #{sessionId} AND deleted = 0 AND direction = 'INCOMING' ORDER BY message_time DESC LIMIT 1")
    XianyuMessage selectLatestIncoming(Long accountId, String sessionId);
}
