package cn.net.rjnetwork.xianyu.manager.ops.controller;

import cn.net.rjnetwork.xianyu.manager.ai.ops.AiOpsService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsBatchCreateRequest;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsBatchCreateResult;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsMultiSyncRequest;
import cn.net.rjnetwork.xianyu.manager.ops.dto.OpsWeeklyReport;
import cn.net.rjnetwork.xianyu.manager.ops.model.AiOpsTask;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 运营 API（批量上品、多账号同步、运营周报）
 */
@RestController
@RequestMapping("/api/ai/ops")
public class AiOpsController {

    private final AiOpsService opsService;

    public AiOpsController(AiOpsService opsService) {
        this.opsService = opsService;
    }

    // ==================== 批量上品 ====================

    /**
     * POST /api/ai/ops/batch-create
     * {
     *   "accountId": 1,
     *   "category": "数码",
     *   "products": [
     *     { "source": "iPhone 15 Pro", "keywords": ["iPhone","15 Pro"], "condition": "九成新" }
     *   ]
     * }
     */
    @PostMapping("/batch-create")
    public ApiResponse<AiOpsTask> batchCreate(@RequestBody OpsBatchCreateRequest request,
                                              @RequestParam(required = false) Long modelId) {
        return ApiResponse.ok(opsService.startBatchCreate(request, modelId));
    }

    /**
     * GET /api/ai/ops/batch-create/progress?taskId=1
     */
    @GetMapping("/batch-create/progress")
    public ApiResponse<OpsBatchCreateResult> batchProgress(@RequestParam Long taskId) {
        return ApiResponse.ok(opsService.getBatchProgress(taskId));
    }

    // ==================== 多账号同步 ====================

    /**
     * POST /api/ai/ops/multi-sync
     * {
     *   "sourceAccountId": 1,
     *   "productId": 1,
     *   "targetAccountIds": [2, 3, 4],
     *   "delayMinutesPerAccount": 30
     * }
     */
    @PostMapping("/multi-sync")
    public ApiResponse<AiOpsTask> multiSync(@RequestBody OpsMultiSyncRequest request,
                                            @RequestParam(required = false) Long modelId) {
        return ApiResponse.ok(opsService.startMultiAccountSync(request, modelId));
    }

    // ==================== 运营周报 ====================

    /**
     * GET /api/ai/ops/weekly-report?accountId=1&modelId=1
     */
    @GetMapping("/weekly-report")
    public ApiResponse<OpsWeeklyReport> weeklyReport(@RequestParam Long accountId,
                                                     @RequestParam(required = false) Long modelId) {
        return ApiResponse.ok(opsService.generateWeeklyReport(accountId, modelId));
    }

    // ==================== 任务查询 ====================

    /**
     * GET /api/ai/ops/tasks?accountId=1&status=COMPLETED&page=1&size=20
     */
    @GetMapping("/tasks")
    public ApiResponse<List<AiOpsTask>> listTasks(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(opsService.listTasks(accountId, status, page, size));
    }
}
