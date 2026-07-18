package cn.net.rjnetwork.xianyu.manager.monitor.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(monitorService.getDashboardStats());
    }

    @GetMapping("/accounts")
    public ApiResponse<List<Map<String, Object>>> accountStats() {
        return ApiResponse.ok(monitorService.getAccountStats());
    }

    @PostMapping("/cache/clear")
    public ApiResponse<Void> clearCache() {
        monitorService.invalidateCache();
        return ApiResponse.ok(null);
    }
}
