package cn.net.rjnetwork.xianyu.manager.clouddisk.controller;

import cn.net.rjnetwork.xianyu.manager.clouddisk.service.OpenListTaskService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/cloud-storage/openlist")
public class OpenListController {

    private final OpenListTaskService taskService;

    public OpenListController(OpenListTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(taskService.getStatus());
    }

    @PostMapping("/install")
    public ApiResponse<Map<String, Object>> install() {
        try {
            CompletableFuture<Void> future = taskService.startInstallAsync();
            return ApiResponse.ok(Map.of("status", "started", "message", "安装已启动"));
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("启动安装失败: " + e.getMessage());
        }
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start() {
        try {
            CompletableFuture<Void> future = taskService.startOpenListAsync();
            return ApiResponse.ok(Map.of("status", "started", "message", "启动已启动"));
        } catch (IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        taskService.stopOpenList();
        return ApiResponse.ok(Map.of("status", "stopped", "message", "已停止"));
    }

    @PostMapping("/restart")
    public ApiResponse<Map<String, Object>> restart() {
        try {
            CompletableFuture<Void> future = taskService.restartOpenListAsync();
            return ApiResponse.ok(Map.of("status", "started", "message", "重启已启动"));
        } catch (Exception e) {
            return ApiResponse.error("重启失败: " + e.getMessage());
        }
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> progress() {
        return ApiResponse.ok(taskService.getCurrentProgress());
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        taskService.subscribe(emitter);
        return emitter;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(taskService.getStatus());
    }
}
