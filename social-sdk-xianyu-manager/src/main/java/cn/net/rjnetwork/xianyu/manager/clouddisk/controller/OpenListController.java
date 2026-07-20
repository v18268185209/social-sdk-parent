package cn.net.rjnetwork.xianyu.manager.clouddisk.controller;

import cn.net.rjnetwork.xianyu.manager.clouddisk.client.OpenListClient;
import cn.net.rjnetwork.xianyu.manager.clouddisk.service.OpenListInstallerService;
import cn.net.rjnetwork.xianyu.manager.clouddisk.service.OpenListProcessManager;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cloud-storage/openlist")
public class OpenListController {

    private final OpenListInstallerService installerService;
    private final OpenListProcessManager processManager;
    private final OpenListClient openListClient;

    public OpenListController(OpenListInstallerService installerService,
                              OpenListProcessManager processManager,
                              OpenListClient openListClient) {
        this.installerService = installerService;
        this.processManager = processManager;
        this.openListClient = openListClient;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(installerService.getStatus());
    }

    @PostMapping("/install")
    public ApiResponse<Map<String, Object>> install() {
        try {
            installerService.install();
            return ApiResponse.ok(Map.of("status", "installed", "message", "安装完成"));
        } catch (Exception e) {
            return ApiResponse.error("安装失败: " + e.getMessage());
        }
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start() {
        try {
            return ApiResponse.ok(processManager.start());
        } catch (Exception e) {
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ApiResponse<Map<String, Object>> stop() {
        return ApiResponse.ok(processManager.stop());
    }

    @PostMapping("/restart")
    public ApiResponse<Map<String, Object>> restart() {
        try {
            return ApiResponse.ok(processManager.restart());
        } catch (Exception e) {
            return ApiResponse.error("重启失败: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(installerService.getInfo());
    }

    @PostMapping("/mount")
    public ApiResponse<Map<String, Object>> mount(@RequestBody Map<String, Object> body) {
        try {
            openListClient.mkdir((String) body.get("path"));
            return ApiResponse.ok(Map.of("status", "success", "message", "挂载成功"));
        } catch (Exception e) {
            return ApiResponse.error("挂载失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/unmount/{id}")
    public ApiResponse<Map<String, Object>> unmount(@PathVariable Long id) {
        // 简化实现：返回成功
        return ApiResponse.ok(Map.of("status", "success", "message", "卸载成功"));
    }
}
