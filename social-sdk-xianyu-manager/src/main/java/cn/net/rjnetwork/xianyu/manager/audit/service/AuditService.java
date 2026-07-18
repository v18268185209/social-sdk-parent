package cn.net.rjnetwork.xianyu.manager.audit.service;

import cn.net.rjnetwork.xianyu.manager.audit.mapper.AuditLogMapper;
import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
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
        return auditLogMapper.selectList(null);
    }
}
