package cn.net.rjnetwork.xianyu.manager.audit.service;

import cn.net.rjnetwork.xianyu.manager.audit.mapper.AuditLogMapper;
import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public Page<AuditLog> listLogs(int pageNum, int pageSize, String action, String resourceType) {
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AuditLog::getActionTime);
        if (action != null && !action.isBlank()) {
            wrapper.like(AuditLog::getAction, action);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            wrapper.eq(AuditLog::getResourceType, resourceType);
        }
        return auditLogMapper.selectPage(page, wrapper);
    }

    /** 兼容旧调用：不分页查询 */
    public List<AuditLog> listLogsAll(String action, String resourceType) {
        return listLogs(1, 500, action, resourceType).getRecords();
    }
}
