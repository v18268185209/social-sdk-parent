package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyDigestConfigMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyDigestConfig;
import cn.net.rjnetwork.xianyu.manager.notify.service.DigestService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 每日摘要配置（单例 id=1）。提供读取/保存与“立即发送”接口。
 */
@RestController
@RequestMapping("/api/notify/digest")
public class NotifyDigestConfigController {

    private final NotifyDigestConfigMapper digestMapper;
    private final DigestService digestService;

    public NotifyDigestConfigController(NotifyDigestConfigMapper digestMapper, DigestService digestService) {
        this.digestMapper = digestMapper;
        this.digestService = digestService;
    }

    @GetMapping("/config")
    public ApiResponse<NotifyDigestConfig> get() {
        NotifyDigestConfig cfg = digestMapper.selectById(1L);
        if (cfg == null) {
            cfg = new NotifyDigestConfig();
            cfg.setId(1L);
        }
        return ApiResponse.ok(cfg);
    }

    @PutMapping("/config")
    public ApiResponse<Void> save(@RequestBody NotifyDigestConfig cfg) {
        cfg.setId(1L);
        cfg.setUpdatedAt(LocalDateTime.now());
        NotifyDigestConfig existing = digestMapper.selectById(1L);
        if (existing != null) {
            digestMapper.updateById(cfg);
        } else {
            cfg.setCreatedAt(LocalDateTime.now());
            digestMapper.insert(cfg);
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/send-now")
    public ApiResponse<Map<String, Object>> sendNow() {
        digestService.sendNow();
        return ApiResponse.ok(Map.of("sent", true));
    }
}
