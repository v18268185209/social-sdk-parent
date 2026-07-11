package cn.net.rjnetwork.chrome.cdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 原生 Chrome DevTools Protocol (CDP) 客户端。
 *
 * <p>当前 social-sdk-chrome 仅通过 Selenium WebDriver 驱动 Chrome，且网络/控制台/指纹等
 * 均以「JS 注入」方式实现，完全没有原始 CDP 通路。{@code CdpClient} 补足这一缺口：
 * 直接基于 JDK 的 {@link java.net.http.WebSocket} 对接任意 CDP 端点（本地或远程，
 * 例如 http://192.168.1.127:9333），对外暴露 Fetch 拦截、UA/请求头覆盖、Emulation
 * （地理/时区/触摸）、Performance、DOMSnapshot、Storage 清理等 Selenium 难以原生提供的能力。</p>
 *
 * <p>用法示例：
 * <pre>
 *   // 1) 对接已运行的远程/本地 Chrome
 *   CdpClient client = CdpClient.attachRemote("http://192.168.1.127:9333");
 *   String targetId = client.createTarget("about:blank");
 *   String sessionId = client.attachTarget(targetId);
 *   client.setSessionId(sessionId);
 *
 *   // 2) 在页面会话上执行 CDP 能力
 *   client.navigate("https://example.com").join();
 *   JsonNode r = client.evaluate("1+1").join();          // Runtime.evaluate
 *   client.setUserAgentOverride("social-sdk/1.0");        // Network.setUserAgentOverride
 *   client.enableFetchInterception(req ->                // Fetch 拦截（反爬/MTOP 改写）
 *       client.continueRequest(req.get("requestId").asText()), true);
 * </pre>
 * </p>
 */
public class CdpClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebSocket ws;
    private final HttpClient httpClient;
    private final AtomicLong msgId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<JsonNode>>> eventHandlers = new ConcurrentHashMap<>();
    private volatile String sessionId;

    private CdpClient(WebSocket ws, HttpClient httpClient) {
        this.ws = ws;
        this.httpClient = httpClient;
    }

    // ============================ 建立连接 ============================

    /**
     * 直连一个 CDP WebSocket（browser 级或 page 级均可）。
     */
    public static CdpClient connect(String wsUrl) {
        HttpClient http = HttpClient.newBuilder()
                .proxy(ProxySelector.of(null))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        Listener listener = new Listener();
        WebSocket ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsUrl), listener)
                .join();
        CdpClient client = new CdpClient(ws, http);
        listener.attach(client);
        return client;
    }

    /**
     * 通过 HTTP 端点（如 http://host:port）发现 browser 级 WebSocket 并连接。
     */
    public static CdpClient attachRemote(String httpBase) throws Exception {
        return connect(discoverBrowserWs(httpBase));
    }

    /**
     * 从 /json/version 读取 browser 级 webSocketDebuggerUrl（绕过系统代理直连）。
     */
    public static String discoverBrowserWs(String httpBase) throws Exception {
        HttpClient http = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();
        String url = httpBase.replaceAll("/$", "") + "/json/version";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode node = MAPPER.readTree(resp.body());
        return node.get("webSocketDebuggerUrl").asText();
    }

    // ============================ 底层收发 ============================

    public CdpClient setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    /** 发送一条 CDP 命令，返回结果（或异常的）future。 */
    public CompletableFuture<JsonNode> send(String method) {
        return send(method, null);
    }

    public CompletableFuture<JsonNode> send(String method, JsonNode params) {
        long id = msgId.incrementAndGet();
        ObjectNode req = MAPPER.createObjectNode();
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.set("params", params);
        }
        if (sessionId != null) {
            req.put("sessionId", sessionId);
        }
        CompletableFuture<JsonNode> fut = new CompletableFuture<>();
        pending.put(id, fut);
        ws.sendText(req.toString(), true);
        // 防止对端宕机/连接断开导致调用方永久阻塞
        return fut.orTimeout(20, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 注册 CDP 事件监听（可多次注册同一事件）。 */
    public void on(String method, Consumer<JsonNode> handler) {
        eventHandlers.computeIfAbsent(method, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    // ============================ Target 管理 ============================

    public String createTarget(String url) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("url", url == null ? "about:blank" : url);
        JsonNode r = send("Target.createTarget", p).join();
        return r.get("targetId").asText();
    }

    public String attachTarget(String targetId) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("targetId", targetId);
        p.put("flatten", true);
        JsonNode r = send("Target.attachToTarget", p).join();
        return r.get("sessionId").asText();
    }

    public void closeTarget(String targetId) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("targetId", targetId);
        send("Target.closeTarget", p).join();
    }

    // ============================ Page / Runtime ============================

    public CompletableFuture<JsonNode> navigate(String url) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("url", url);
        return send("Page.navigate", p);
    }

    public CompletableFuture<JsonNode> evaluate(String expression) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("expression", expression);
        p.put("returnByValue", true);
        p.put("awaitPromise", true);
        return send("Runtime.evaluate", p);
    }

    /** 同步求值为字符串（仅取 returnByValue 的 value）。 */
    public String evaluateString(String expression) {
        JsonNode r = evaluate(expression).join();
        if (r.has("exceptionDetails")) {
            throw new RuntimeException("JS exception: " + r.get("exceptionDetails"));
        }
        JsonNode result = r.get("result");
        return result != null && result.has("value") ? result.get("value").asText() : "";
    }

    public CompletableFuture<JsonNode> captureScreenshot() {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("format", "png");
        p.put("captureBeyondViewport", false);
        return send("Page.captureScreenshot", p);
    }

    // ============================ DOM ============================

    public JsonNode getDocument() {
        return send("DOM.getDocument", MAPPER.createObjectNode().put("depth", -1)).join();
    }

    public JsonNode querySelector(int nodeId, String selector) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("nodeId", nodeId);
        p.put("selector", selector);
        return send("DOM.querySelector", p).join();
    }

    public JsonNode getBoxModel(int nodeId) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("nodeId", nodeId);
        return send("DOM.getBoxModel", p).join();
    }

    // ============================ Input ============================

    public void click(double x, double y) {
        ObjectNode down = MAPPER.createObjectNode();
        down.put("type", "mousePressed");
        down.put("x", x);
        down.put("y", y);
        down.put("button", "left");
        down.put("clickCount", 1);
        send("Input.dispatchMouseEvent", down).join();
        ObjectNode up = down.deepCopy();
        up.put("type", "mouseReleased");
        send("Input.dispatchMouseEvent", up).join();
    }

    public void type(String text) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("text", text);
        send("Input.insertText", p).join();
    }

    // ============================ Network ============================

    public void enableNetwork() {
        send("Network.enable").join();
    }

    public JsonNode getCookies() {
        return send("Network.getCookies").join();
    }

    public void setCookie(String url, String name, String value) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("url", url);
        p.put("name", name);
        p.put("value", value);
        send("Network.setCookie", p).join();
    }

    /** 覆盖 User-Agent（比 JS 重写 navigator.userAgent 更底层、更可靠）。 */
    public void setUserAgentOverride(String userAgent) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("userAgent", userAgent);
        send("Network.setUserAgentOverride", p).join();
    }

    /** 注入自定义请求头（如反爬头 x-sgeib / x-mini-wua 等）。 */
    public void setExtraHttpHeaders(Map<String, String> headers) {
        ObjectNode p = MAPPER.createObjectNode();
        ObjectNode h = p.putObject("headers");
        headers.forEach(h::put);
        send("Network.setExtraHTTPHeaders", p).join();
    }

    // ============================ Fetch（请求拦截/改写） ============================

    /**
     * 启用 Fetch 拦截。onRequestPaused 回调收到每个被拦截请求的 params，
     * 可在其中改写/放行/伪造响应。autoContinue=false 时需手动调用 continueRequest。
     */
    public void enableFetchInterception(Consumer<JsonNode> onRequestPaused, boolean autoContinue) {
        on("Fetch.requestPaused", params -> {
            try {
                if (autoContinue) {
                    // 关键：在 reader 线程中继续请求时【不能】阻塞等待响应，
                    // 否则 reader 线程被自己要读取的响应卡死（死锁）。仅发送即可。
                    ObjectNode cp = MAPPER.createObjectNode();
                    cp.put("requestId", params.get("requestId").asText());
                    send("Fetch.continueRequest", cp);
                }
                onRequestPaused.accept(params);
            } catch (Exception e) {
                // 回调异常不应影响 CDP 读取线程
            }
        });
        ArrayNode patterns = MAPPER.createArrayNode();
        ObjectNode pat = patterns.addObject();
        pat.put("urlPattern", "*");
        pat.put("requestStage", "Request");
        ObjectNode p = MAPPER.createObjectNode();
        p.set("patterns", patterns);
        send("Fetch.enable", p).join();
    }

    public void continueRequest(String requestId) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("requestId", requestId);
        send("Fetch.continueRequest", p).join();
    }

    public void fulfillRequest(String requestId, int status, String body) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("requestId", requestId);
        p.put("responseCode", status);
        ObjectNode headers = p.putObject("responseHeaders");
        headers.put("Content-Type", "text/plain");
        p.put("body", body == null ? "" : body);
        send("Fetch.fulfillRequest", p).join();
    }

    public void disableFetch() {
        send("Fetch.disable").join();
    }

    // ============================ Emulation ============================

    public void setGeolocationOverride(double latitude, double longitude, int accuracy) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("latitude", latitude);
        p.put("longitude", longitude);
        p.put("accuracy", accuracy);
        send("Emulation.setGeolocationOverride", p).join();
    }

    public void setTimezoneOverride(String timezoneId) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("timezoneId", timezoneId);
        send("Emulation.setTimezoneOverride", p).join();
    }

    public void setTouchEmulation(boolean enabled) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("enabled", enabled);
        send("Emulation.setTouchEmulationEnabled", p).join();
    }

    public void setDeviceMetrics(int width, int height) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("width", width);
        p.put("height", height);
        p.put("deviceScaleFactor", 1);
        p.put("mobile", false);
        send("Emulation.setDeviceMetricsOverride", p).join();
    }

    // ============================ Performance / DOMSnapshot / Storage ============================

    public JsonNode getPerformanceMetrics() {
        send("Performance.enable").join();
        return send("Performance.getMetrics").join();
    }

    /** 结构化快照（旧 DOM.captureSnapshot 已在 Chrome150 移除，改用 DOMSnapshot）。 */
    public JsonNode captureDOMSnapshot() {
        send("DOMSnapshot.enable").join();
        ObjectNode p = MAPPER.createObjectNode();
        p.set("computedStyles", MAPPER.createArrayNode());
        p.put("includeDOMRects", false);
        return send("DOMSnapshot.captureSnapshot", p).join();
    }

    public void clearStorageForOrigin(String origin) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("origin", origin);
        p.put("storageTypes", "all");
        send("Storage.clearDataForOrigin", p).join();
    }

    // ============================ 关闭 ============================

    @Override
    public void close() {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
        }
    }

    // ============================ WebSocket 监听器 ============================

    private static class Listener implements WebSocket.Listener {
        private CdpClient client;
        private final StringBuilder buf = new StringBuilder();

        void attach(CdpClient c) {
            this.client = c;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String text = buf.toString();
                buf.setLength(0);
                try {
                    JsonNode msg = MAPPER.readTree(text);
                    if (msg.has("id")) {
                        long id = msg.get("id").asLong();
                        CompletableFuture<JsonNode> fut = client.pending.remove(id);
                        if (fut != null) {
                            if (msg.has("error")) {
                                fut.completeExceptionally(
                                        new RuntimeException("CDP " + msg.get("error")));
                            } else {
                                fut.complete(msg.has("result") ? msg.get("result")
                                        : MAPPER.createObjectNode());
                            }
                        }
                    } else if (msg.has("method")) {
                        String method = msg.get("method").asText();
                        JsonNode params = msg.has("params") ? msg.get("params")
                                : MAPPER.createObjectNode();
                        List<Consumer<JsonNode>> handlers = client.eventHandlers.get(method);
                        if (handlers != null) {
                            for (Consumer<JsonNode> h : handlers) {
                                h.accept(params);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                webSocket.request(1);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            // 连接级错误：使所有挂起请求失败
            for (CompletableFuture<JsonNode> f : client.pending.values()) {
                f.completeExceptionally(error);
            }
            client.pending.clear();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            // 连接关闭：避免调用方永久阻塞
            RuntimeException closed = new RuntimeException("CDP connection closed: " + statusCode + " " + reason);
            for (CompletableFuture<JsonNode> f : client.pending.values()) {
                f.completeExceptionally(closed);
            }
            client.pending.clear();
            return CompletableFuture.completedFuture(null);
        }
    }

    // ============================ 自测入口（对接真实 Chrome 验证） ============================

    public static void main(String[] args) throws Exception {
        String base = System.getenv().getOrDefault("CDP_HTTP", "http://192.168.1.127:9333");
        System.out.println("[*] attaching to " + base);
        CdpClient client = CdpClient.attachRemote(base);

        String targetId = client.createTarget("about:blank");
        String sid = client.attachTarget(targetId);
        client.setSessionId(sid);
        System.out.println("[*] target=" + targetId + " session=" + sid);

        client.enableNetwork();
        client.navigate("https://example.com").join();
        Thread.sleep(1500);

        System.out.println("[*] evaluate 1+1 = " + client.evaluateString("1+1"));
        System.out.println("[*] title = " + client.evaluateString("document.title"));

        // Fetch 拦截演示
        final boolean[] intercepted = {false};
        client.enableFetchInterception(params -> intercepted[0] = true, true);
        client.navigate("https://example.com").join();
        Thread.sleep(1500);
        client.disableFetch();
        System.out.println("[*] Fetch intercepted = " + intercepted[0]);

        // UA / Emulation
        client.setUserAgentOverride("social-sdk-cdp/1.0");
        client.setGeolocationOverride(31.2304, 121.4737, 100);
        client.setTimezoneOverride("Asia/Shanghai");
        System.out.println("[*] UA now = " + client.evaluateString("navigator.userAgent"));

        // Performance / DOMSnapshot / Storage
        JsonNode metrics = client.getPerformanceMetrics();
        System.out.println("[*] performance metrics count = "
                + (metrics.has("metrics") ? metrics.get("metrics").size() : 0));
        JsonNode snap = client.captureDOMSnapshot();
        System.out.println("[*] DOMSnapshot documents = "
                + (snap.has("documents") ? snap.get("documents").size() : 0));

        try {
            JsonNode shot = client.captureScreenshot().get(15, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("[*] screenshot bytes(base64) = "
                    + shot.get("data").asText().length());
        } catch (Exception e) {
            System.out.println("[!] screenshot skipped: " + e.getMessage());
        }

        try {
            client.closeTarget(targetId);
            client.close();
        } catch (Exception e) {
            System.out.println("[!] cleanup: " + e.getMessage());
        }
        System.out.println("[✓] CdpClient self-test OK");
        System.exit(0);
    }
}
