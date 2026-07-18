package cn.net.rjnetwork.xianyu.manager.virtual.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.virtual.dto.VirtualShipConfigRequest;
import cn.net.rjnetwork.xianyu.manager.virtual.dto.VirtualShipConfigUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.virtual.dto.CardPoolImportRequest;
import cn.net.rjnetwork.xianyu.manager.virtual.dto.ShipTaskRetryRequest;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualCardPool;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipConfig;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import cn.net.rjnetwork.xianyu.manager.virtual.service.VirtualShipService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 虚拟商品 / 自动发货管理 API
 */
@RestController
@RequestMapping("/api/virtual")
public class VirtualShipController {

    private final VirtualShipService shipService;

    public VirtualShipController(VirtualShipService shipService) {
        this.shipService = shipService;
    }

    // ==================== 卡密池 ====================

    /**
     * 批量导入卡密
     * POST /api/virtual/cards/import
     * {
     *   "productId": 1,
     *   "cards": ["卡号1|密码1", "卡号2", "卡号3|密码3"]
     * }
     */
    @PostMapping("/cards/import")
    public ApiResponse<Map<String, Object>> importCards(@RequestBody CardPoolImportRequest request) {
        int count = shipService.importCards(request.getProductId(), request.getCards());
        return ApiResponse.ok(Map.of("imported", count));
    }

    /**
     * 查询卡密池
     * GET /api/virtual/cards?productId=1&status=AVAILABLE
     */
    @GetMapping("/cards")
    public ApiResponse<List<VirtualCardPool>> listCards(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(shipService.listCards(productId, status));
    }

    // ==================== 发货任务 ====================

    /**
     * 查询发货任务列表
     * GET /api/virtual/tasks?status=PENDING&page=1&size=20
     */
    @GetMapping("/tasks")
    public ApiResponse<List<VirtualShipTask>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(shipService.listTasks(status, page, size));
    }

    /**
     * 重试失败任务
     * POST /api/virtual/tasks/retry
     * { "taskId": 1 }
     */
    @PostMapping("/tasks/retry")
    public ApiResponse<Void> retryTask(@RequestBody ShipTaskRetryRequest request) {
        VirtualShipTask task = shipService.listTasks("FAILED", 1, 100).stream()
                .filter(t -> t.getId().equals(request.getTaskId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + request.getTaskId()));
        shipService.processShipTask(task);
        return ApiResponse.ok(null);
    }

    // ==================== 配置 ====================

    /**
     * 查询自动发货配置
     * GET /api/virtual/config?accountId=1
     */
    @GetMapping("/config")
    public ApiResponse<VirtualShipConfig> getConfig(@RequestParam Long accountId) {
        return ApiResponse.ok(shipService.getConfig(accountId));
    }

    /**
     * 创建/更新自动发货配置
     * POST /api/virtual/config
     * {
     *   "accountId": 1,
     *   "enabled": true,
     *   "delaySeconds": 30,
     *   "autoConfirmDays": 7,
     *   "notifyAfterShip": true
     * }
     */
    @PostMapping("/config")
    public ApiResponse<VirtualShipConfig> saveConfig(@RequestBody VirtualShipConfigRequest request) {
        return ApiResponse.ok(shipService.saveConfig(
                request.getAccountId(),
                request.getEnabled(),
                request.getDelaySeconds(),
                request.getAutoConfirmDays(),
                request.getNotifyAfterShip()
        ));
    }

    /**
     * 更新配置（部分更新）
     * PUT /api/virtual/config/{id}
     */
    @PutMapping("/config/{id}")
    public ApiResponse<VirtualShipConfig> updateConfig(@PathVariable Long id, @RequestBody VirtualShipConfigUpdateRequest request) {
        request.setId(id);
        VirtualShipConfig existing = shipService.getConfig(request.getId());
        if (existing == null) return ApiResponse.fail("NOT_FOUND", "Config not found");
        return ApiResponse.ok(shipService.saveConfig(
                existing.getAccountId(),
                request.getEnabled(),
                request.getDelaySeconds(),
                request.getAutoConfirmDays(),
                request.getNotifyAfterShip()
        ));
    }
}
