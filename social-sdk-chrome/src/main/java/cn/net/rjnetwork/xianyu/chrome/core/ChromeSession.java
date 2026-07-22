package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.exception.ChromeException;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import cn.net.rjnetwork.xianyu.captcha.service.SliderAntiDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
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
@Component
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

    /** CDP WS 调用默认超时（秒） */
    private static final int WS_CALL_TIMEOUT_SECONDS = 10;

    /** evaluate / addScript 时最大重试次数 */
    private static final int INJECT_RETRY_MAX = 3;

    /** evaluate / addScript 重试间隔（毫秒） */
    private static final long INJECT_RETRY_INTERVAL_MS = 300;

    private final ChromeConfig config;
    private final OkHttpClient httpClient;
    private final ChromePortPool portPool;
    /** 长连接 WebSocket 客户端（注入脚本专用，超长 readTimeout） */
    private final OkHttpClient wsClient;

    private final AtomicLong wsRequestId = new AtomicLong(0);

    @Autowired
    public ChromeSession(ChromeConfig config, ChromePortPool portPool) {
        this(config, portPool, null);
    }

    public ChromeSession(ChromeConfig config, ChromePortPool portPool, OkHttpClient httpClient) {
        this.config = config;
        this.portPool = portPool;
        this.httpClient = httpClient != null ? httpClient : new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        // WebSocket 客户端：注入脚本时需要长连接等待 Runtime.evaluate 返回
        this.wsClient = (httpClient != null ? httpClient : this.httpClient).newBuilder()
                .readTimeout(WS_CALL_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)
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

            // 3. 启动进程。工作目录不要设到 user-data-dir 内，避免相对路径再次嵌套解析。
            ProcessBuilder pb = new ProcessBuilder(command);
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

            log.info("[SESSION] Chrome 已启动, accountId={}, pid={}, port={}, profileDir={}, command={}",
                    accountId, process.pid(), port, profile.getProfileDir(), maskCommandForLog(command));

            // 4. 等待 CDP 就绪（轮询）
            long deadline = System.currentTimeMillis() + config.getLaunchTimeoutSeconds() * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive()) {
                    throw ChromeException.launchFailed(accountId,
                            "Chrome 进程已退出，退出码=" + process.exitValue() + buildChromeLogHint(profile));
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

    // ==================== 指纹注入（双通道 CD WebSocket） ====================

    /**
     * 向指定容器的所有 page target 注入反检测脚本。
     *
     * <p>双通道：
     * <ol>
     *   <li>{@code Page.addScriptToEvaluateOnNewDocument} — 持久化，后续每次 SPA 跳转/页面刷新都自动注入</li>
     *   <li>{@code Runtime.evaluate} — 立刻在当前页面生效</li>
     * </ol>
     *
     * <p>脚本内容由 {@code scriptProvider} 提供（调用方传入 {@code SliderAntiDetect.buildScript(seed)}），
     * ChromeSession 不关心脚本实现，只负责注入通道。
     *
     * @param port          CDP 端口
     * @param scriptProvider 反检测脚本提供者（per-account seed 派生）
     */
    public void injectFingerprintScript(int port, java.util.function.LongSupplier scriptProvider) throws IOException, TimeoutException {
        String script = SliderAntiDetect.buildScript(scriptProvider.getAsLong());
        // 找到第一个 page target 的 webSocketDebuggerUrl
        String wsUrl = findPageTargetWsUrl(port);
        if (wsUrl == null || wsUrl.isEmpty()) {
            log.warn("[INJECT] 未找到 page target, 无法注入, port={}", port);
            return;
        }
        // 通道 1：持久化注入
        sendCdpCommand(wsUrl, "Page.addScriptToEvaluateOnNewDocument",
                "{\"source\":\"" + escapeJson(script) + "\"}",
                "identifier");
        // 通道 2：立刻在当前页面生效（带重试，因为页面可能还没加载完）
        evaluateOnPageWithRetry(wsUrl, script);
        log.info("[INJECT] 指纹脚本已注入, port={}, scriptLen={}", port, script.length());
    }

    /**
     * 在 page target 上执行 {@code Runtime.evaluate}，带重试。
     * <p>页面加载完成前调用会失败，通过重试等待页面就绪。
     */
    private void evaluateOnPageWithRetry(String wsUrl, String expression) {
        for (int attempt = 1; attempt <= INJECT_RETRY_MAX; attempt++) {
            try {
                String result = evaluateOnPage(wsUrl, expression);
                log.debug("[INJECT] Runtime.evaluate 成功, attempt={}, result={}", attempt, result);
                return;
            } catch (Exception e) {
                log.debug("[INJECT] Runtime.evaluate 失败, attempt={}, err={}", attempt, e.getMessage());
                if (attempt < INJECT_RETRY_MAX) {
                    try {
                        Thread.sleep(INJECT_RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.warn("[INJECT] Runtime.evaluate 重试{}次后仍失败", INJECT_RETRY_MAX);
    }

    /**
     * 通过 WebSocket 向 page target 发送 CDP 命令并等待响应。
     *
     * @param wsUrl     page target 的 webSocketDebuggerUrl
     * @param method    CDP 方法名（如 Runtime.evaluate、Page.addScriptToEvaluateOnNewDocument）
     * @param params    JSON 格式的参数字符串（不含外层 id/response）
     * @return CDP 响应的 result JSON 字符串
     */
    public String sendCdpCommand(String wsUrl, String method, String params) throws IOException, TimeoutException {
        return sendCdpCommand(wsUrl, method, params, null);
    }

    public String sendCdpCommand(String wsUrl, String method, String params, String requireField) throws IOException, TimeoutException {
        if (wsUrl == null || wsUrl.isEmpty()) {
            throw new IllegalArgumentException("wsUrl 不能为空");
        }
        long id = wsRequestId.incrementAndGet();
        String requestBody = "{\"id\":" + id + ",\"method\":\"" + method + "\""
                + (params != null && !params.isEmpty() ? ",\"params\":" + params : "")
                + "}";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong responseId = new AtomicLong(-1);
        java.util.concurrent.atomic.AtomicReference<String> responseHolder = new java.util.concurrent.atomic.AtomicReference<>();

        Request wsRequest = new Request.Builder().url(wsUrl).build();
        WebSocket ws = wsClient.newWebSocket(wsRequest, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonNode node = JSON.readTree(text);
                    JsonNode respId = node.path("id");
                    if (respId.isNumber() && respId.asLong() == id) {
                        responseId.set(respId.asLong());
                        responseHolder.set(text);
                        latch.countDown();
                    }
                } catch (Exception ignored) {}
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                latch.countDown();
            }
        });
        try {
            ws.send(requestBody);
            boolean awaited = latch.await(WS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!awaited) {
                throw new TimeoutException("CDP 命令超时: " + method);
            }
            String resp = responseHolder.get();
            if (resp == null) {
                throw new IOException("CDP 命令无响应: " + method);
            }
            // 校验是否成功
            JsonNode respNode = JSON.readTree(resp);
            if (respNode.path("error") != null && !respNode.path("error").isMissingNode()) {
                throw new IOException("CDP 命令失败: " + respNode.path("error"));
            }
            return resp;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("CDP 调用被中断: " + method, ie);
        } finally {
            try { ws.close(1000, "done"); } catch (Exception ignored) {}
        }
    }

    /**
     * 查找指定端口上第一个 page target 的 webSocketDebuggerUrl。
     *
     * @return webSocketDebuggerUrl，未找到返回 null
     */
    public String findPageTargetWsUrl(int port) {
        try {
            JsonNode targets = listTargets(port);
            if (targets == null || targets.isEmpty()) {
                return null;
            }
            for (JsonNode t : targets) {
                if ("page".equals(t.path("type").asText())) {
                    String wsUrl = t.path("webSocketDebuggerUrl").asText();
                    if (wsUrl != null && !wsUrl.isEmpty()) {
                        return wsUrl;
                    }
                }
            }
        } catch (IOException e) {
            log.debug("[INJECT] 查找 page target 失败, port={}", port);
        }
        return null;
    }

    /**
     * 在 page target 上执行 {@code Runtime.evaluate}。
     */
    private String evaluateOnPage(String wsUrl, String expression) throws IOException, TimeoutException {
        String params = "{\"expression\":\"" + escapeJson(expression) + "\",\"returnByValue\":true}";
        return sendCdpCommand(wsUrl, "Runtime.evaluate", params, "result");
    }

    /**
     * 简单 JSON 转义（脚本内容含引号/反斜杠）。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
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
        Path dir = Paths.get(profile.getProfileDir()).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.debug("[SESSION] 创建 profile 目录: {}", dir);
        }
        profile.setProfileDir(dir.toString());
    }

    private List<String> buildLaunchCommand(ChromeProfile profile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(detectChromeExecutable(profile.getAccountId()));
        cmd.add("--remote-debugging-address=127.0.0.1");
        cmd.add("--remote-debugging-port=" + profile.getCdpPort());
        cmd.add("--user-data-dir=" + Paths.get(profile.getProfileDir()).toAbsolutePath().normalize());
        cmd.add("--window-size=" + config.getWindowWidth() + "," + config.getWindowHeight());

        if (profile.getProxyUrl() != null && !profile.getProxyUrl().isEmpty()) {
            cmd.add("--proxy-server=" + profile.getProxyUrl());
        }

        if (config.isHeadless()) {
            if ("legacy".equalsIgnoreCase(config.getHeadlessMode())) {
                cmd.add("--headless");
            } else {
                cmd.add("--headless=new");
            }
            cmd.add("--disable-gpu");
            cmd.add("--window-position=-32000,-32000");
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

    private String detectChromeExecutable(long accountId) {
        String configuredPath = config.getExecutablePath();
        if (configuredPath != null && !configuredPath.isBlank()) {
            String executable = resolveExecutableAt(configuredPath);
            if (executable != null) {
                return executable;
            }
            log.warn("[SESSION] 配置的 Chrome 路径不可用: {}", configuredPath);
        }

        String chromeBin = System.getenv("CHROME_BIN");
        if (chromeBin != null && !chromeBin.isBlank()) {
            String executable = resolveExecutableAt(chromeBin);
            if (executable != null) {
                return executable;
            }
            log.warn("[SESSION] CHROME_BIN 不可用: {}", chromeBin);
        }

        ChromeDetector.DetectionResult detected = ChromeDetector.detect();
        if (detected.found) {
            return detected.path;
        }

        throw ChromeException.launchFailed(accountId,
                "未找到可用的 Chrome/Chromium/Edge 浏览器，请在 Chrome 配置中设置 chrome.executable-path，"
                        + "或安装 Chrome/Edge，或设置 CHROME_BIN。已搜索路径数=" + detected.searched.size());
    }

    private String resolveExecutableAt(String path) {
        File f = new File(path.trim());
        return f.exists() && f.canExecute() ? f.getAbsolutePath() : null;
    }

    private String buildChromeLogHint(ChromeProfile profile) {
        File err = new File(profile.getProfileDir(), ".chrome-err.log");
        File out = new File(profile.getProfileDir(), ".chrome-out.log");
        String errTail = tailFile(err, 20);
        String outTail = tailFile(out, 10);
        StringBuilder hint = new StringBuilder();
        hint.append("，profileDir=").append(profile.getProfileDir());
        if (!errTail.isBlank()) {
            hint.append("，stderrTail=").append(errTail);
        }
        if (!outTail.isBlank()) {
            hint.append("，stdoutTail=").append(outTail);
        }
        if (errTail.isBlank() && outTail.isBlank()) {
            hint.append("，Chrome 日志为空，请检查该 profile 目录权限、浏览器是否被安全软件拦截、以及是否存在同 profile 残留进程");
        }
        return hint.toString();
    }

    private String tailFile(File file, int maxLines) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) return "";
            int from = Math.max(0, lines.size() - maxLines);
            return String.join(" | ", lines.subList(from, lines.size()));
        } catch (Exception e) {
            return "<读取 " + file.getName() + " 失败: " + e.getMessage() + ">";
        }
    }

    private String maskCommandForLog(List<String> command) {
        if (command == null || command.isEmpty()) return "";
        return String.join(" ", command);
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
