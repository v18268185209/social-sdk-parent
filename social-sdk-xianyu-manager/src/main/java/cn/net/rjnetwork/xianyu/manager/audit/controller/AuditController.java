package cn.net.rjnetwork.xianyu.manager.audit.controller;

import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import cn.net.rjnetwork.xianyu.manager.audit.service.AuditService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 分页查询审计日志
     * 返回结构：
     * {
     *   success: true,
     *   data: {
     *     records: [...],
     *     total: 100,
     *     current: 1,
     *     size: 20
     *   }
     * }
     */
    @GetMapping("/logs")
    public ApiResponse<ApiResponse.PageResponse<AuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType) {
        Page<AuditLog> result = auditService.listLogs(page, size, action, resourceType);
        return ApiResponse.ok(toPageResponse(result));
    }

    private static <T> ApiResponse.PageResponse<T> toPageResponse(Page<T> page) {
        ApiResponse.PageResponse<T> response = new ApiResponse.PageResponse<>();
        response.setRecords(page.getRecords());
        response.setTotal(page.getTotal());
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        return response;
    }
}
