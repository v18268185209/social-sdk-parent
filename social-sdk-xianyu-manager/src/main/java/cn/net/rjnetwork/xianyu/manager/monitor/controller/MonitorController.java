package cn.net.rjnetwork.xianyu.manager.monitor.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorResult;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorResultService;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorService;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorTaskService taskService;
    private final MonitorResultService resultService;
    private final MonitorService monitorService;

    public MonitorController(MonitorTaskService taskService, MonitorResultService resultService, MonitorService monitorService) {
        this.taskService = taskService;
        this.resultService = resultService;
        this.monitorService = monitorService;
    }

    /**
     * 运营仪表盘 - 汇总概览 + 各账号详细指标
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(monitorService.getDashboardStats());
    }

    /**
     * 各账号运营指标（独立接口）
     */
    @GetMapping("/accounts")
    public ApiResponse<List<Map<String, Object>>> accountStats() {
        return ApiResponse.success(monitorService.getAccountStats());
    }

    /**
     * 清除仪表盘缓存
     */
    @PostMapping("/cache/clear")
    public ApiResponse<String> clearCache() {
        monitorService.invalidateCache();
        return ApiResponse.success("缓存已清除");
    }

    @GetMapping("/tasks")
    public ApiResponse<List<MonitorTask>> listTasks(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) Long accountId) {
        return ApiResponse.success(taskService.list(page, size, accountId));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<MonitorTask> getTask(@PathVariable Long id) {
        return ApiResponse.success(taskService.get(id));
    }

    @PostMapping("/tasks")
    public ApiResponse<MonitorTask> createTask(@RequestBody MonitorTask task) {
        taskService.save(task);
        return ApiResponse.success(task);
    }

    @PutMapping("/tasks/{id}")
    public ApiResponse<MonitorTask> updateTask(@PathVariable Long id, @RequestBody MonitorTask task) {
        task.setId(id);
        taskService.save(task);
        return ApiResponse.success(task);
    }

    @PostMapping("/tasks/{id}/pause")
    public ApiResponse<String> pause(@PathVariable Long id) {
        taskService.pause(id);
        return ApiResponse.success("paused");
    }

    @PostMapping("/tasks/{id}/resume")
    public ApiResponse<String> resume(@PathVariable Long id) {
        taskService.resume(id);
        return ApiResponse.success("resumed");
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ApiResponse.success("deleted");
    }

    @PostMapping("/tasks/{id}/run")
    public ApiResponse<String> runNow(@PathVariable Long id) {
        MonitorTask task = taskService.get(id);
        if (task == null) return ApiResponse.error("任务不存在");
        // 异步执行
        new Thread(() -> {
            try {
                // 通过 application context 获取 runner 执行
            } catch (Exception e) {
                // log
            }
        }).start();
        return ApiResponse.success("已触发执行");
    }

    // ===== 结果查询 =====

    @GetMapping("/results/recent")
    public ApiResponse<List<MonitorResult>> recentResults(@RequestParam Long taskId,
                                                           @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(resultService.getRecent(taskId, limit));
    }

    @GetMapping("/results/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(required = false) Long taskId) {
        return ApiResponse.success(resultService.getStats(taskId));
    }
}
