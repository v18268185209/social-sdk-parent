package cn.net.rjnetwork.xianyu.manager.system.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("appName", "闲鱼多账号管理平台");
        info.put("version", "1.0.0");
        info.put("database", "SQLite3");
        info.put("cache", "Caffeine In-Memory");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("springBootVersion", "3.5.4");
        return ApiResponse.ok(info);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        return ApiResponse.ok(health);
    }
}
