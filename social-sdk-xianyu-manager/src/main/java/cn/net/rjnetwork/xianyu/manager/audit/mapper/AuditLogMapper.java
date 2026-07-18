package cn.net.rjnetwork.xianyu.manager.audit.mapper;

import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
