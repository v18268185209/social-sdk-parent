package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.exception.ChromeException;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chrome 容器运行时。
 *
 * <p>封装单个 Chrome 进程的生命周期：
 * <ul>
 *   <li>启动（{@link #launch(ChromeProfile)}）— 用 {@link ProcessBuilder} 启动 Chromium，{@code --remote-debugging-port} 绑定 profile 指定的端口，
 *       {@code --proxy-server} 绑定指定代理</li>
 *   <li>健康检测（{@link #ping()}）— 向 {@code /json/version} 发 HTTP GET 确认 CDP 存活</li>
 *   <li>退出（{@link #shutdown()}）— 优雅销毁进程</li>
 * </ul>
 *
 * <p>启动时会创建 {@link ChromeProfile#getProfileDir()} 下的 {@code user-data-dir}，确保会话隔离。
 */
public class ChromeSession {

    private static final Logger log = LoggerFactory.getLogger(ChromeSession.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 用于探测 CDP 就绪的短连接超时（毫秒） */
    private static final int CDP_READINESS_TIMEOUT_MS = 1000;

    /** Chrome 启动后等待其可接受连接的轮询间隔（毫秒） */
    private static final int LAUNCH_POLL_INTERVAL_MS = 200;

    /**
     * 探测 CDP 就绪的 {@code /json/version} 返回预期包含的字段（用于判断启动成功）。
     */
    private static final String CDP_READY_MARKER = "webSocketDebuggerUrl";

    private final ChromeConfig config;
    private final OkHttpClient httpClient;
    private final ChromePortPool portPool;

    public ChromeSession(ChromeConfig config, ChromePortPool portPool, OkHttpClient httpClient) {
        this.config = config;
        this.portPool = portPool;
        this.httpClient = httpClient != null ? httpClient : new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 启动 Chrome 容器并等待其 CDP 就绪。
     *
     * <p>1. 准备 profileDir（不存在则创建）；2. 启动进程；3. 轮询 {@code /json/version} 直到 CDP 可响应。
     *
     * @param profile 容器描述（需提前填好 cdpPort、proxyUrl、profileDir；launch 会覆盖 cdpEndpoint / status / launchedAt）
     * @throws ChromeException 启动失败
     */
    public void launch(ChromeProfile profile) {
        long accountId = profile.getAccountId();
        int port = profile.getCdpPort();

        try {
            // 1. 准备 user-data-dir
            ensureProfileDir(profile);

            // 2. 构造命令行
            List<String> command = buildLaunchCommand(profile);

            // 3. 启动进程
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(profile.getProfileDir()));
            pb.redirectErrorStream(false);

            if (config.isLogChromeOutput()) {
                pb.redirectOutput(new File(profile.getProfileDir(), ".chrome-out.log"));
                pb.redirectError(new File(profile.getProfileDir(), ".chrome-err.log"));
            } else {
                pb.redirectOutput(new File(profile.getProfileDir(), ".chrome-out.log"));
                pb.redirectError(new File(profile.getProfileDir(), ".chrome-err.log"));
            }

            Process process = pb.start();
            profile.setChromeProcess(process);
            profile.setStatus(ChromeProfile.ContainerStatus.LAUNCHING);
            profile.setCdpEndpoint(String.format("http://127.0.0.1:%d", port));
            profile.setLaunchedAt(LocalDateTime.now());

            log.info("[SESSION] Chrome 已启动, accountId={}, pid={}, port={}, profileDir={}",
                    accountId, process.pid(), port, profile.getProfileDir());

            // 4. 等待 CDP 就绪（轮询）
            long deadline = System.currentTimeMillis() + config.getLaunchTimeoutSeconds() * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive()) {
                    throw ChromeException.launchFailed(accountId,
                            "Chrome 进程已退出，退出码=" + process.exitValue());
                }
                if (isCdpReady(port)) {
                    profile.setStatus(ChromeProfile.ContainerStatus.RUNNING);
                    profile.setCrashCount(0);
                    log.info("[SESSION] CDP 就绪, accountId={}", accountId);
                    return;
                }
                Thread.sleep(LAUNCH_POLL_INTERVAL_MS);
            }

            // 超时：强制销毁并抛错
            destroyProcessSilently(process);
            throw ChromeException.launchFailed(accountId,
                    "CDP 未在 " + config.getLaunchTimeoutSeconds() + " 秒内就绪");

        } catch (ChromeException ce) {
            profile.setStatus(ChromeProfile.ContainerStatus.CRASHED);
            throw ce;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            profile.setStatus(ChromeProfile.ContainerStatus.CRASHED);
            throw ChromeException.launchFailed(accountId, ie);
        } catch (Exception e) {
            profile.setStatus(ChromeProfile.ContainerStatus.CRASHED);
            throw ChromeException.launchFailed(accountId, e);
        }
    }

    /**
     * 探测指定端口上的 CDP 是否已就绪（尝试 {@code /json/version}）。
     */
    public boolean isCdpReady(int port) {
        String url = String.format("http://127.0.0.1:%d/json/version", port);
        Request req = new Request.Builder().url(url).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                return false;
            }
            ResponseBody body = resp.body();
            if (body == null) {
                return false;
            }
            String text = body.string();
            return text.contains(CDP_READY_MARKER);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 向指定端口的 CDP 发送一个命令（不等待结果）。
     * <p>HTTP 命令：navigate、evaluate 等简单命令都可以通过 HTTP POST。
     *
     * @param cdpUrl    CDP HTTP 端点（如 /json/protocol 或 /json/runtime/evaluate）
     * @param payload   JSON 序列化 body
     * @return CDP 响应 body（可 null）
     * @throws IOException 网络错误
     */
    public String sendCdpPost(String cdpUrl, JsonNode payload) throws IOException {
        okhttp3.MediaType JSON_TYPE = okhttp3.MediaType.parse("application/json; charset=utf-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(JSON_TYPE, payload.toString());
        Request req = new Request.Builder().url(cdpUrl).post(body).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            return rb != null ? rb.string() : null;
        }
    }

    /**
     * 导航到指定 URL（通过 HTTP {@code /json/navigate} 端点或 Navigator CDP 命令）。
     *
     * @param target  完整 http(s) 中间页面 URL（会解析为 CDP target 后导航）
     * @return 导航的 targetId / sessionId
     * @throws IOException CDP 请求失败时抛出
     */
    public void navigateTo(int port, String target) throws IOException {
        JsonNode targets = listTargets(port);
        if (targets == null || targets.isEmpty()) {
            // 没有现有 target，新建一个
            createTarget(port, target);
            return;
        }
        // 找到第一个可用的 page target 并导航
        for (JsonNode t : targets) {
            String type = t.path("type").asText();
            if ("page".equals(type)) {
                String id = t.path("id").asText();
                sendCdpPost(String.format("http://127.0.0.1:%d/json/navigate?%s", port, target), null);
                return;
            }
        }
        createTarget(port, target);
    }

    /**
     * 列出当前所有 CDP targets（通过指定端口）。
     */
    public JsonNode listTargets(int port) throws IOException {
        Request req = new Request.Builder()
                .url(String.format("http://127.0.0.1:%d/json/list", port))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            return rb != null ? JSON.readTree(rb.string()) : null;
        }
    }

    /**
     * 在指定 CDP 端口列出所有 targets。
     */
    public JsonNode listTargets(int port) throws IOException {
        Request req = new Request.Builder()
                .url(String.format("http://127.0.0.1:%d/json/list", port))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            return rb != null ? JSON.readTree(rb.string()) : null;
        }
    }

    /**
     * 通过 CDP 打开一个新 target 并导航到指定 URL。
     */
    public String createTarget(int port, String url) throws IOException {
        Request req = new Request.Builder()
                .url(String.format("http://127.0.0.1:%d/json/new?%s", port, url))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            return rb != null ? rb.string() : null;
        }
    }

    /**
     * 关闭指定 target。
     */
    public String closeTarget(int port, String targetId) throws IOException {
        Request req = new Request.Builder()
                .url(String.format("http://127.0.0.1:%d/json/close/%s", port, targetId))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            return rb != null ? rb.string() : null;
        }
    }

    /**
     * 优雅关闭 Chrome 进程（尝试等待自然退出；超时后强制 kill）。
     */
    public void shutdown(ChromeProfile profile) {
        Process proc = profile.getChromeProcess();
        if (proc == null || !proc.isAlive()) {
            profile.setStatus(ChromeProfile.ContainerStatus.STOPPED);
            return;
        }
        try {
            proc.destroy();
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
            }
            profile.setStatus(ChromeProfile.ContainerStatus.STOPPED);
            log.info("[SESSION] Chrome 已关闭, accountId={}, pid={}", profile.getAccountId(), proc.pid());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            profile.setStatus(ChromeProfile.ContainerStatus.STOPPED);
        }
    }

    /**
     * 释放 CDP 回端口池。
     */
    public void releasePort(ChromeProfile profile) {
        if (profile.getCdpPort() > 0) {
            portPool.releasePort(profile.getCdpPort());
        }
    }

    // ==================== 内部方法 ====================

    private void ensureProfileDir(ChromeProfile profile) throws IOException {
        Path dir = Paths.get(profile.getProfileDir());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.debug("[SESSION] 创建 profile 目录: {}", dir);
        }
    }

    private List<String> buildLaunchCommand(ChromeProfile profile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(detectChromeExecutable());
        cmd.add("--remote-debugging-port=" + profile.getCdpPort());
        cmd.add("--user-data-dir=" + profile.getProfileDir());
        cmd.add("--window-size=" + config.getWindowWidth() + "," + config.getWindowHeight());

        if (profile.getProxyUrl() != null && !profile.getProxyUrl().isEmpty()) {
            cmd.add("--proxy-server=" + profile.getProxyUrl());
        }

        // 反检测启动参数
        String[] args = config.getCustomLaunchArgs();
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg != null && !arg.isEmpty()) {
                    cmd.add(arg);
                }
            }
        } else {
            // 内置反检测参数（与 SliderAntiDetect.LAUNCH_ARGS 对齐）
            cmd.add("--disable-blink-features=AutomationControlled");
            cmd.add("--no-sandbox");
            cmd.add("--disable-dev-shm-usage");
            cmd.add("--disable-setuid-sandbox");
            cmd.add("--disable-infobars");
            cmd.add("--disable-background-timer-throttling");
            cmd.add("--disable-backgrounding-occluded-windows");
            cmd.add("--disable-renderer-backgrounding");
            cmd.add("--disable-features=IsolateOrigins,site-per-process");
            cmd.add("--disable-site-isolation-trials");
            cmd.add("--disable-hang-monitor");
            cmd.add("--disable-prompt-on-repost");
            cmd.add("--disable-sync");
            cmd.add("--disable-default-apps");
            cmd.add("--disable-crash-reporter");
            cmd.add("--disable-component-extensions-with-background-pages");
            cmd.add("--disable-features=TranslateUI");
            cmd.add("--disable-ipc-flooding-protection");
            cmd.add("--no-first-run");
            cmd.add("--no-default-browser-check");
            cmd.add("--enable-features=NetworkService,NetworkServiceInProcess");
            cmd.add("--force-color-profile=srgb");
            cmd.add("--metrics-recording-only");
            cmd.add("--mute-audio");
            cmd.add("--disable-notifications");
            cmd.add("--disable-popup-blocking");
            cmd.add("--password-store=basic");
            cmd.add("--use-mock-keychain");
        }

        // 不自动打开首页
        cmd.add("about:blank");

        return cmd;
    }

    private String detectChromeExecutable() {
        if (config.getExecutablePath() != null && !config.getExecutablePath().isEmpty()) {
            return config.getExecutablePath();
        }
        // 常见 macOS 路径
        String[] candidates = {
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium",
                "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary",
        };
        for (String c : candidates) {
            if (new File(c).exists()) {
                return c;
            }
        }
        // 从 PATH 探测
        try {
            Process which = new ProcessBuilder("which", "google-chrome").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(which.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (IOException ignored) {
        }
        // 默认：直接叫 google-chrome
        return "google-chrome";
    }

    private void destroyProcessSilently(Process process) {
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
    }
}
