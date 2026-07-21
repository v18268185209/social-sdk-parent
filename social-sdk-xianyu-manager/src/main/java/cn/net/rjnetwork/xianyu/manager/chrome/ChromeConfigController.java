package cn.net.rjnetwork.xianyu.manager.chrome;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeDetector;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeDownloader;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chrome 浏览器配置管理接口：探测、下载、保存。
 */
@RestController
@RequestMapping("/api/chrome-config")
public class ChromeConfigController {

    private static final Logger log = LoggerFactory.getLogger(ChromeConfigController.class);
    private static final String UPLOADS_DIR = "./chrome-bin";

    private final ChromeConfig chromeConfig;

    public ChromeConfigController(ChromeConfig chromeConfig) {
        this.chromeConfig = chromeConfig;
    }

    /** 当前配置 + 探测状态总览。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> getConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("executablePath", chromeConfig.getExecutablePath());
        data.put("headless", chromeConfig.isHeadless());
        data.put("headlessMode", chromeConfig.getHeadlessMode());
        data.put("portRangeStart", chromeConfig.getPortRangeStart());
        data.put("portRangeEnd", chromeConfig.getPortRangeEnd());
        data.put("userDataDirRoot", chromeConfig.getUserDataDirRoot());
        data.put("windowWidth", chromeConfig.getWindowWidth());
        data.put("windowHeight", chromeConfig.getWindowHeight());
        data.put("perAccountSeedNoise", chromeConfig.isPerAccountSeedNoise());
        data.put("launchTimeoutSeconds", chromeConfig.getLaunchTimeoutSeconds());
        data.put("maxCrashRecoveryAttempts", chromeConfig.getMaxCrashRecoveryAttempts());
        data.put("customLaunchArgs", chromeConfig.getCustomLaunchArgs());

        // 自动探测一次
        ChromeDetector.DetectionResult detected = ChromeDetector.detectAt(chromeConfig.getExecutablePath());
        data.put("detected", Map.of(
                "found", detected.found,
                "path", detected.path == null ? "" : detected.path,
                "type", detected.type == null ? "" : detected.type));

        return ApiResponse.ok(data);
    }

    /** 手动触发系统 Chrome 探测。 */
    @GetMapping("/detect")
    public ApiResponse<ChromeDetector.DetectionResult> detect() {
        ChromeDetector.DetectionResult result = ChromeDetector.detectAt(chromeConfig.getExecutablePath());
        return ApiResponse.ok(result);
    }

    /** 批量探测所有候选路径，返回候选列表与命中结果。 */
    @GetMapping("/detect/all")
    public ApiResponse<ChromeDetector.DetectionResult> detectAll() {
        return ApiResponse.ok(ChromeDetector.detect());
    }

    /** 保存配置（路径、头/无头、自定义参数）。 */
    @PostMapping("/save")
    public ApiResponse<Map<String, Object>> save(@RequestBody SaveRequest req) {
        LinkedHashMap<String, Object> applied = new LinkedHashMap<>();
        if (req.executablePath != null) {
            chromeConfig.setExecutablePath(req.executablePath);
            applied.put("executablePath", req.executablePath);
        }
        if (req.headless != null) {
            chromeConfig.setHeadless(req.headless);
            applied.put("headless", req.headless);
        }
        if (req.headlessMode != null) {
            chromeConfig.setHeadlessMode(req.headlessMode);
            applied.put("headlessMode", req.headlessMode);
        }
        if (req.customLaunchArgs != null) {
            chromeConfig.setCustomLaunchArgs(req.customLaunchArgs);
            applied.put("customLaunchArgs", req.customLaunchArgs);
        }
        if (req.windowWidth != null) {
            chromeConfig.setWindowWidth(req.windowWidth);
            applied.put("windowWidth", req.windowWidth);
        }
        if (req.windowHeight != null) {
            chromeConfig.setWindowHeight(req.windowHeight);
            applied.put("windowHeight", req.windowHeight);
        }
        if (req.perAccountSeedNoise != null) {
            chromeConfig.setPerAccountSeedNoise(req.perAccountSeedNoise);
            applied.put("perAccountSeedNoise", req.perAccountSeedNoise);
        }
        if (req.launchTimeoutSeconds != null) {
            chromeConfig.setLaunchTimeoutSeconds(req.launchTimeoutSeconds);
            applied.put("launchTimeoutSeconds", req.launchTimeoutSeconds);
        }
        if (req.maxCrashRecoveryAttempts != null) {
            chromeConfig.setMaxCrashRecoveryAttempts(req.maxCrashRecoveryAttempts);
            applied.put("maxCrashRecoveryAttempts", req.maxCrashRecoveryAttempts);
        }

        // 校验保存的路径
        String path = chromeConfig.getExecutablePath();
        if (path != null && !path.isBlank()) {
            boolean ok = Files.exists(Paths.get(path));
            applied.put("pathExists", ok);
        }

        log.info("[CHROME-CONFIG] 保存配置: {}", applied);
        return ApiResponse.ok(applied);
    }

    /** 触发下载 Chrome。 */
    @PostMapping("/download")
    public ApiResponse<Map<String, Object>> download() {
        ChromeDownloader.DownloadResult result = ChromeDownloader.download(UPLOADS_DIR);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", result.success);
        data.put("path", result.path == null ? "" : result.path);
        data.put("message", result.message);
        data.put("bytes", result.bytes);
        data.put("targetDir", UPLOADS_DIR);
        if (result.success) {
            return ApiResponse.ok(data);
        }
        ApiResponse<Map<String, Object>> fail = ApiResponse.fail("DOWNLOAD_FAILED", result.message);
        fail.setData(data);
        return fail;
    }

    /** 校验指定路径是否为有效可执行文件。 */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validate(@RequestBody ValidateRequest req) {
        Map<String, Object> data = new LinkedHashMap<>();
        String path = req == null ? "" : req.path;
        if (path == null || path.isBlank()) {
            data.put("valid", false);
            data.put("reason", "路径为空");
            ApiResponse<Map<String, Object>> r = ApiResponse.fail("EMPTY_PATH", "路径不能为空");
            r.setData(data);
            return r;
        }
        java.io.File f = new java.io.File(path.trim());
        data.put("path", f.getAbsolutePath());
        data.put("exists", f.exists());
        data.put("canExecute", f.canExecute());
        data.put("sizeBytes", f.exists() ? f.length() : 0);
        data.put("valid", f.exists() && f.canExecute());
        return ApiResponse.ok(data);
    }

    public static class SaveRequest {
        public String executablePath;
        public Boolean headless;
        public String headlessMode;
        public String[] customLaunchArgs;
        public Integer windowWidth;
        public Integer windowHeight;
        public Boolean perAccountSeedNoise;
        public Long launchTimeoutSeconds;
        public Integer maxCrashRecoveryAttempts;
    }

    public static class ValidateRequest {
        public String path;
    }
}
