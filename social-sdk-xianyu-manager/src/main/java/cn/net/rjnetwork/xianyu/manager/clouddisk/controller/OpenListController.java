package cn.net.rjnetwork.xianyu.manager.clouddisk.controller;

import cn.net.rjnetwork.xianyu.manager.clouddisk.service.OpenListTaskService;
import cn.net.rjnetwork.xianyu.manager.clouddisk.client.OpenListClient;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/cloud-storage/openlist")
public class OpenListController {

    private final OpenListTaskService taskService;
    private final OpenListClient openListClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenListController(OpenListTaskService taskService, OpenListClient openListClient) {
        this.taskService = taskService;
        this.openListClient = openListClient;
    }

    // ============== OpenList 实例管理 ==============

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

    @GetMapping("/events")
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        taskService.subscribe(emitter);
        return emitter;
    }

    // ============== 存储挂载管理 ==============

    /**
     * GET /api/cloud-storage/openlist/storages — 列出所有存储挂载
     */
    @GetMapping("/storages")
    public ApiResponse<List<Map<String, Object>>> listStorages() {
        try {
            String json = openListClient.listStorages();
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            JsonNode content = data.path("content");

            List<Map<String, Object>> storages = new ArrayList<>();
            if (content.isArray()) {
                for (JsonNode item : content) {
                    Map<String, Object> storage = new LinkedHashMap<>();
                    storage.put("id", item.path("id").asLong());
                    storage.put("mountPath", item.path("mount_path").asText());
                    storage.put("driver", item.path("driver").asText());
                    storage.put("enabled", item.path("enabled").asBoolean(true));
                    storage.put("order", item.path("order").asInt(0));
                    storage.put("status", item.path("status").asText(""));
                    storage.put("remark", item.path("remark").asText(""));
                    storages.add(storage);
                }
            }
            return ApiResponse.ok(storages);
        } catch (IOException e) {
            return ApiResponse.error("获取存储列表失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/storages — 添加存储挂载
     */
    @PostMapping("/storages")
    public ApiResponse<Map<String, Object>> createStorage(@RequestBody Map<String, Object> storage) {
        try {
            String json = openListClient.createStorage(storage);
            JsonNode root = objectMapper.readTree(json);
            long id = root.path("data").path("id").asLong();
            return ApiResponse.ok(Map.of("id", id, "message", "存储挂载添加成功"));
        } catch (IOException e) {
            return ApiResponse.error("添加存储挂载失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/storages/update — 更新存储挂载
     */
    @PostMapping("/storages/update")
    public ApiResponse<Map<String, Object>> updateStorage(@RequestBody Map<String, Object> storage) {
        try {
            openListClient.updateStorage(storage);
            return ApiResponse.ok(Map.of("message", "存储挂载更新成功"));
        } catch (IOException e) {
            return ApiResponse.error("更新存储挂载失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/storages/delete — 删除存储挂载
     */
    @PostMapping("/storages/delete")
    public ApiResponse<Map<String, Object>> deleteStorage(@RequestParam long id) {
        try {
            openListClient.deleteStorage(id);
            return ApiResponse.ok(Map.of("message", "存储挂载已删除"));
        } catch (IOException e) {
            return ApiResponse.error("删除存储挂载失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/storages/enable — 启用存储挂载
     */
    @PostMapping("/storages/enable")
    public ApiResponse<Map<String, Object>> enableStorage(@RequestParam long id) {
        try {
            openListClient.enableStorage(id);
            return ApiResponse.ok(Map.of("message", "已启用"));
        } catch (IOException e) {
            return ApiResponse.error("启用失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/storages/disable — 禁用存储挂载
     */
    @PostMapping("/storages/disable")
    public ApiResponse<Map<String, Object>> disableStorage(@RequestParam long id) {
        try {
            openListClient.disableStorage(id);
            return ApiResponse.ok(Map.of("message", "已禁用"));
        } catch (IOException e) {
            return ApiResponse.error("禁用失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/cloud-storage/openlist/drivers — 获取驱动信息
     */
    @GetMapping("/drivers")
    public ApiResponse<String> getDriverInfo(@RequestParam String driver) {
        try {
            String json = openListClient.getDriverInfo(driver);
            return ApiResponse.ok(json);
        } catch (IOException e) {
            return ApiResponse.error("获取驱动信息失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/cloud-storage/openlist/drivers/list — 列出常用驱动
     */
    @GetMapping("/drivers/list")
    public ApiResponse<List<Map<String, Object>>> listDrivers() {
        List<Map<String, Object>> drivers = new ArrayList<>();

        // 常用驱动列表
        String[][] driverInfos = {
            {"BaiduNetdisk", "百度网盘", "refresh_token,root_folder_path"},
            {"AliyundriveOpen", "阿里云盘 Open", "refresh_token,root_folder_id,drive_type"},
            {"Quark", "夸克网盘", "cookie,root_folder_id"},
            {"123Pan", "123 云盘", "username,password"},
            {"Onedrive", "OneDrive", "refresh_token,client_id,client_secret,region"},
            {"GoogleDrive", "Google Drive", "refresh_token,client_id,client_secret"},
            {"WebDav", "WebDAV", "url,username,password"},
            {"S3", "S3 对象存储", "access_key_id,secret_access_key,bucket,endpoint"},
            {"Local", "本机存储", "root_folder_path"},
            {"Teambition", "Teambition", "region,root_folder_id"},
        };

        for (String[] info : driverInfos) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", info[0]);
            d.put("label", info[1]);
            d.put("fields", info[2]);
            drivers.add(d);
        }

        return ApiResponse.ok(drivers);
    }

    // ============== 文件代理接口 ==============

    /**
     * GET /api/cloud-storage/openlist/files?path=/baidu — 列出目录下的文件
     */
    @GetMapping("/files")
    public ApiResponse<String> listFiles(@RequestParam String path) {
        try {
            String json = openListClient.listFiles(path);
            return ApiResponse.ok(json);
        } catch (IOException e) {
            return ApiResponse.error("列出文件失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/files/upload — 上传文件到指定路径
     */
    @PostMapping("/files/upload")
    public ApiResponse<String> uploadFile(@RequestParam MultipartFile file,
                                          @RequestParam String path) {
        try {
            String result = openListClient.uploadFile(path, file);
            return ApiResponse.ok(result);
        } catch (IOException e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/cloud-storage/openlist/files/delete — 删除文件
     */
    @PostMapping("/files/delete")
    public ApiResponse<String> deleteFile(@RequestBody Map<String, String> params) {
        try {
            String path = params.get("path");
            String filename = params.get("filename");
            openListClient.delete(List.of((path + "/" + filename).replace("//", "/")));
            return ApiResponse.ok("删除成功");
        } catch (IOException e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
}
