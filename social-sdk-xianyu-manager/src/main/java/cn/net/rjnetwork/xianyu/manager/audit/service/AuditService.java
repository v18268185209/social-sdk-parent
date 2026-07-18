package cn.net.rjnetwork.xianyu.manager.audit.service;

import cn.net.rjnetwork.xianyu.manager.audit.mapper.AuditLogMapper;
import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void save(AuditLog log) {
        auditLogMapper.insert(log);
    }

    public List<AuditLog> listLogs(int pageNum, int pageSize, String action, String resourceType) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.like(AuditLog::getAction, action);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            wrapper.eq(AuditLog::getResourceType, resourceType);
        }
        wrapper.orderByDesc(AuditLog::getActionTime);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + Math.max(0, (pageNum - 1) * pageSize));
        return auditLogMapper.selectList(wrapper);
    }
}
