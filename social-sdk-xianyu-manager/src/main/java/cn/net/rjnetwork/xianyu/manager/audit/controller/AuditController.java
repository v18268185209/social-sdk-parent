package cn.net.rjnetwork.xianyu.manager.audit.controller;

import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import cn.net.rjnetwork.xianyu.manager.audit.service.AuditService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    public ApiResponse<List<AuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType) {
        return ApiResponse.ok(auditService.listLogs(page, size, action, resourceType));
    }
}
