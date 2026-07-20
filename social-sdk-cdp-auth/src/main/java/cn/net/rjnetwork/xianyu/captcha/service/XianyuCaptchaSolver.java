package cn.net.rjnetwork.xianyu.captcha.service;

import cn.net.rjnetwork.xianyu.captcha.config.CdpCaptchaConfig;
import cn.net.rjnetwork.xianyu.captcha.model.CaptchaResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

/**
 * 闲鱼滑块验证自动化工具 - 通过 CDP 远程控制浏览器完全自动化完成滑块验证
 */
@Service
public class XianyuCaptchaSolver {

    private static final Logger log = LoggerFactory.getLogger(XianyuCaptchaSolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CdpCaptchaConfig config;

    public XianyuCaptchaSolver(CdpCaptchaConfig config) {
        this.config = config;
    }

    /**
     * 尝试通过 CDP 自动完成滑块验证
     *
     * @param punishUrl 闲鱼风控 punish URL
     * @return 验证码处理结果，包含新 cookie
     */
    public CaptchaResult solve(String punishUrl) {
        try {
            log.info("[CDP-AUTH] Solving captcha for URL: {}", truncate(punishUrl, 200));

            // 1. 获取浏览器级 CDP WebSocket，用于 Target.* 命令
            String browserEndpoint = getCdpEndpointFromBrowser();

            // 2. 获取所有页面列表
            Map<String, Object> targets = getTargets(browserEndpoint);

            // 3. 查找或创建闲鱼标签页
            String targetId = findGoofishTargetId(targets);
            if (targetId == null) {
                targetId = createNewTarget(browserEndpoint, "about:blank");
            }
            if (targetId == null || targetId.isBlank()) {
                return CaptchaResult.fail("未找到或创建 CDP 页面目标");
            }

            // 4. 获取页面级 CDP WebSocket。Page/Runtime/Input 命令必须发到 page 端点，不能发到 browser 端点。
            String cdpEndpoint = getPageWebSocketEndpoint(targetId);

            // 5. 直接打开风控返回的 punish URL 触发滑块；不要打开不存在的 account-info 页面
            navigateToAccountPage(cdpEndpoint, punishUrl);
            Thread.sleep(5000);

            // 6. 检查是否有滑块验证界面
            boolean hasSlider = checkSliderExists(cdpEndpoint);

            if (!hasSlider) {
                triggerSliderCaptcha(cdpEndpoint);
                waitForElement(cdpEndpoint, ".slider-btn, .btn_slide, #slider-btn, [class*='slider']", 10000);
            }

            // 7. 阿里滑块会校验真实轨迹/设备指纹，CDP 自动拖动容易被判定失败。
            //    这里不再强行自动拖动，而是打开 punish 页后等待人工完成，完成后自动提取 x5sec/cookie 继续业务。
            log.warn("[CDP-AUTH] Slider page is ready. Please drag it manually in the CDP Chrome window, waiting up to {} seconds...", config.getTimeoutSeconds());
            boolean success = waitForCaptchaPassed(cdpEndpoint, config.getTimeoutSeconds() * 1000L);
            if (!success) {
                return CaptchaResult.fail("滑块验证未通过或等待人工验证超时");
            }

            // 8. 提取 cookie
            String cookie = extractCookie(cdpEndpoint, punishUrl);
            if (cookie != null && !cookie.isEmpty()) {
                log.info("[CDP-AUTH] Cookie extracted successfully");
                return CaptchaResult.ok(cookie);
            }

            return CaptchaResult.fail("未获取到新 cookie");

        } catch (Exception e) {
            log.error("[CDP-AUTH] Captcha solving failed: {}", e.getMessage(), e);
            return CaptchaResult.fail("验证码处理异常：" + e.getMessage());
        }
    }

    /**
     * 通过浏览器远程调试接口获取 CDP WebSocket 端点
     */
    private String getCdpEndpointFromBrowser() throws Exception {
        try {
            String browserEndpoint = "http://" + config.getHost() + ":" + config.getPort() + "/json/version";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(browserEndpoint))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = MAPPER.readTree(response.body());
            return node.path("webSocketDebuggerUrl").asText();
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to get CDP endpoint: {}", e.getMessage());
            throw new IOException("无法获取 CDP 端点", e);
        }
    }

    /**
     * 根据 targetId 获取页面级 WebSocket 端点。
     * Browser 端点只能执行 Target.* 命令，Page/Runtime/Input 命令必须发到页面自己的 webSocketDebuggerUrl。
     */
    private String getPageWebSocketEndpoint(String targetId) throws Exception {
        String listEndpoint = "http://" + config.getHost() + ":" + config.getPort() + "/json/list";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(listEndpoint))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode nodes = MAPPER.readTree(response.body());
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (targetId.equals(node.path("id").asText()) || targetId.equals(node.path("targetId").asText())) {
                    String ws = node.path("webSocketDebuggerUrl").asText("");
                    if (!ws.isBlank()) {
                        log.info("[CDP-AUTH] Using page CDP endpoint: {}", truncate(ws, 120));
                        return ws;
                    }
                }
            }
        }
        throw new IOException("page websocket endpoint not found for targetId=" + targetId);
    }

    /**
     * 连接 CDP WebSocket
     */
    private Socket openWebSocket(String wsUrl) throws Exception {
        // 使用 URI 而非 URL：java.net.URL 不支持 ws:// 协议，会抛 MalformedURLException
        URI uri = URI.create(wsUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : ("wss".equals(uri.getScheme()) ? 443 : 80);
        String path = uri.getPath();
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }

        Socket socket = new Socket(host, port);
        socket.setSoTimeout(config.getTimeoutSeconds() * 1000);

        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        InputStream input = socket.getInputStream();

        // 生成 WebSocket Key
        byte[] keyBytes = new SecureRandom().generateSeed(16);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        // 发送 WebSocket 握手请求
        String upgradeRequest = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n";

        out.print(upgradeRequest);
        out.flush();

        // 读取响应
        byte[] buffer = new byte[4096];
        int read = input.read(buffer);
        if (read == -1) {
            throw new IOException("WebSocket handshake failed: no response");
        }

        String response = new String(buffer, 0, read, StandardCharsets.UTF_8);
        if (!response.startsWith("HTTP/1.1 101")) {
            throw new IOException("WebSocket handshake failed: " + response.substring(0, Math.min(200, response.length())));
        }

        return socket;
    }

    /**
     * 获取所有目标页面列表
     */
    private Map<String, Object> getTargets(String cdpEndpoint) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> params = new LinkedHashMap<>();
            return sendCommand(socket, "Target.getTargets", params);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 查找闲鱼目标页面 ID
     */
    private String findGoofishTargetId(Map<String, Object> targets) {
        if (targets == null || !targets.containsKey("result")) return null;

        Map<String, Object> result = (Map<String, Object>) targets.get("result");
        if (result == null || !result.containsKey("targetInfos")) return null;

        java.util.List<?> targetInfos = (java.util.List<?>) result.get("targetInfos");
        if (targetInfos == null) return null;

        for (Object targetObj : targetInfos) {
            if (targetObj instanceof Map) {
                Map<String, Object> target = (Map<String, Object>) targetObj;
                String url = (String) target.get("url");
                if (url != null && url.contains("goofish.com")) {
                    return (String) target.get("targetId");
                }
            }
        }
        return null;
    }

    /**
     * 创建新标签页并导航到指定 URL
     */
    private String createNewTarget(String cdpEndpoint, String url) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", url);
            Map<String, Object> result = sendCommand(socket, "Target.createTarget", params);

            if (result.containsKey("result")) {
                Map<String, Object> res = (Map<String, Object>) result.get("result");
                return (String) res.get("targetId");
            }
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 附加到目标页面
     */
    private void attachToTarget(String cdpEndpoint, String targetId) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("targetId", targetId);
            sendCommand(socket, "Target.attachToTarget", params);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 导航到账户页面
     */
    private void navigateToAccountPage(String cdpEndpoint, String url) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> pageParams = new LinkedHashMap<>();
            pageParams.put("url", url);
            sendCommand(socket, "Page.enable", null);
            sendCommand(socket, "Runtime.enable", null);
            sendCommand(socket, "Page.navigate", pageParams);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static final AtomicLong CMD_ID = new AtomicLong(1);

    /**
     * 发送 CDP 命令
     */
    private Map<String, Object> sendCommand(Socket socket, String method, Map<String, Object> params) throws Exception {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        InputStream input = socket.getInputStream();

        // 构造请求 JSON
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", CMD_ID.getAndIncrement());
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }

        String json = MAPPER.writeValueAsString(request);

        // 构造 WebSocket 文本帧（客户端帧必须 masked；Chrome CDP 会拒绝未 masked 帧并断开）
        byte[] textBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] maskKey = new byte[4];
        new SecureRandom().nextBytes(maskKey);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text
        if (textBytes.length < 126) {
            frame.write(0x80 | textBytes.length);
        } else if (textBytes.length < 65536) {
            frame.write(0x80 | 126);
            frame.write((textBytes.length >> 8) & 0xFF);
            frame.write(textBytes.length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 56; i >= 0; i -= 8) {
                frame.write((textBytes.length >> i) & 0xFF);
            }
        }
        frame.write(maskKey);
        for (int i = 0; i < textBytes.length; i++) {
            frame.write(textBytes[i] ^ maskKey[i % 4]);
        }

        OutputStream output = socket.getOutputStream();
        output.write(frame.toByteArray());
        output.flush();

        // 读取 WebSocket 响应帧头（2 字节）
        byte[] header = new byte[2];
        if (readFully(input, header, 2) < 2) return new LinkedHashMap<>();

        int opcode = header[0] & 0x0F;
        int length = header[1] & 0x7F;

        if (length == 126) {
            byte[] ext = new byte[2];
            if (readFully(input, ext, 2) < 2) return new LinkedHashMap<>();
            length = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
        } else if (length == 127) {
            byte[] ext = new byte[8];
            if (readFully(input, ext, 8) < 8) return new LinkedHashMap<>();
            long len = 0;
            for (byte b : ext) len = (len << 8) | (b & 0xFF);
            length = (int) len;
        }

        byte[] payload = new byte[length];
        if (length > 0 && readFully(input, payload, length) < length) {
            return new LinkedHashMap<>();
        }

        // 解析 JSON
        try {
            String jsonResponse = new String(payload, StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(jsonResponse);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", node.has("id") ? node.get("id").asText() : "");
            result.put("method", node.has("method") ? node.get("method").asText() : "");
            if (node.has("result")) {
                result.put("result", MAPPER.convertValue(node.get("result"), Map.class));
            }
            return result;
        } catch (Exception e) {
            log.warn("[CDP] Failed to parse response: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 读取指定长度字节到缓冲区
     */
    private void readBytes(InputStream input, byte[] buffer) throws IOException {
        int totalRead = 0;
        while (totalRead < buffer.length) {
            int read = input.read(buffer, totalRead, buffer.length - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
    }

    /**
     * 精确读取指定长度字节，返回实际读取的字节数
     */
    private int readFully(InputStream input, byte[] buffer, int len) throws IOException {
        int totalRead = 0;
        while (totalRead < len) {
            int read = input.read(buffer, totalRead, len - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        return totalRead;
    }


    /**
     * 提取 Cookie
     */
    private String extractCookies(Socket socket) {
        try {
            Map<String, Object> cookieScript = new LinkedHashMap<>();
            cookieScript.put("expression", """
                    (function getCookies() {
                        var cookies = document.cookie.split(';');
                        var cookieObj = {};
                        for (var cookie of cookies) {
                            var parts = cookie.trim().split('=');
                            if (parts.length === 2) {
                                cookieObj[parts[0]] = parts[1];
                            }
                        }
                        return JSON.stringify(cookieObj);
                    })
                    """);
            cookieScript.put("awaitPromise", true);
            cookieScript.put("returnByValue", true);

            Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", cookieScript);

            Object value = extractRuntimeValue(result);
            if (value != null) {
                String json = String.valueOf(value);
                if (json != null && !json.equals("null")) {
                    StringBuilder sb = new StringBuilder();
                    JsonNode node = MAPPER.readTree(json);
                    if (node.isArray()) {
                        for (JsonNode cookie : node) {
                            if (sb.length() > 0) sb.append("; ");
                            sb.append(cookie.path("name").asText()).append("=").append(cookie.path("value").asText());
                        }
                    } else if (node.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> fieldIter = node.fields();
                        while (fieldIter.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fieldIter.next();
                            if (sb.length() > 0) sb.append("; ");
                            sb.append(entry.getKey()).append("=").append(entry.getValue().asText());
                        }
                    }
                    return sb.toString();
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to extract cookie: {}", e.getMessage());
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 检查滑块是否存在
     */
    private boolean checkSliderExists(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (function() {
                    var selectors = ['.slider-btn', '.btn_slide', '#slider-btn', '[class*="slider"]'];
                    for (var selector of selectors) {
                        if (document.querySelector(selector)) {
                            return true;
                        }
                    }
                    return false;
                })()
            """);
            script.put("awaitPromise", true);
            script.put("returnByValue", true);

            Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", script);

            if (result.containsKey("result")) {
                Map<String, Object> inner = (Map<String, Object>) result.get("result");
                return Boolean.TRUE.equals(Boolean.parseBoolean(String.valueOf(inner.get("value"))));
            }

            return false;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to check slider exists: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 触发滑块验证（通过点击页面上的滑块元素）
     */
    private void triggerSliderCaptcha(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (function() {
                    var selectors = ['input[type="text"]', 'button', '.goofish-slider-btn', '[class*="slider"]'];
                    for (var selector of selectors) {
                        var elements = document.querySelectorAll(selector);
                        if (elements.length > 0) {
                            var event = new MouseEvent('click', {
                                bubbles: true,
                                cancelable: true,
                                view: window
                            });
                            elements[0].dispatchEvent(event);
                            return true;
                        }
                    }
                    return false;
                })()
            """);
            script.put("awaitPromise", true);
            script.put("returnByValue", true);

            sendCommand(socket, "Runtime.evaluate", script);
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to trigger slider: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 等待滑块元素出现
     */
    private void waitForElement(String cdpEndpoint, String selector, long timeoutMs) throws InterruptedException {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    Map<String, Object> script = new LinkedHashMap<>();
                    script.put("expression", String.format("!!document.querySelector('%s')", selector));
                    script.put("awaitPromise", true);
                    script.put("returnByValue", true);

                    Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", script);
                    if (result.containsKey("result")) {
                        Map<String, Object> inner = (Map<String, Object>) result.get("result");
                        if ("true".equals(String.valueOf(inner.get("value")))) {
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[CDP-AUTH] Error checking element: {}", e.getMessage());
                }

                Thread.sleep(500);
            }
        } catch (InterruptedException ie) {
            throw ie;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] waitForElement failed: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 自动拖拽滑块（通过 CDP Input 事件模拟真实鼠标拖动）
     */
    private void performAutoSliderDrag(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);

            Map<String, Object> positionScript = new LinkedHashMap<>();
            positionScript.put("expression", """
                (() => {
                    const slider = document.querySelector('#nc_1_n1z, .btn_slide, .nc_iconfont');
                    const track = document.querySelector('#nc_1_nocaptcha, #nocaptcha, .nc-container') || (slider && slider.parentElement);
                    if (!slider || !track) return null;
                    const sr = slider.getBoundingClientRect();
                    const tr = track.getBoundingClientRect();
                    return JSON.stringify({
                        startX: sr.left + sr.width / 2,
                        startY: sr.top + sr.height / 2,
                        endX: tr.right - 10,
                        endY: sr.top + sr.height / 2,
                        sliderWidth: sr.width,
                        trackWidth: tr.width
                    });
                })()
            """);
            positionScript.put("awaitPromise", true);
            positionScript.put("returnByValue", true);

            Map<String, Object> positionResult = sendCommand(socket, "Runtime.evaluate", positionScript);
            Object runtimeValue = extractRuntimeValue(positionResult);
            if (runtimeValue == null || "null".equals(String.valueOf(runtimeValue))) {
                log.warn("[CDP-AUTH] slider position not found");
                return;
            }

            JsonNode node = MAPPER.readTree(String.valueOf(runtimeValue));
            double startX = node.path("startX").asDouble(100);
            double startY = node.path("startY").asDouble(100);
            double endX = node.path("endX").asDouble(startX + 300);
            double endY = node.path("endY").asDouble(startY);
            log.info("[CDP-AUTH] Drag slider from ({}, {}) to ({}, {})", startX, startY, endX, endY);

            dispatchMouse(socket, "mouseMoved", startX, startY, "none", 0);
            dispatchMouse(socket, "mousePressed", startX, startY, "left", 1);
            int steps = 45;
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double eased = 1 - Math.pow(1 - t, 2);
                double x = startX + (endX - startX) * eased + (RANDOM.nextDouble() - 0.5) * 2.5;
                double y = startY + (RANDOM.nextDouble() - 0.5) * 1.5;
                dispatchMouse(socket, "mouseMoved", x, y, "left", 1);
                Thread.sleep(12 + RANDOM.nextInt(18));
            }
            dispatchMouse(socket, "mouseReleased", endX, endY, "left", 0);

        } catch (Exception e) {
            log.error("[CDP-AUTH] Slider drag failed: {}", e.getMessage(), e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private Object extractRuntimeValue(Map<String, Object> commandResult) {
        if (commandResult == null || !commandResult.containsKey("result")) return null;
        Object resultObj = commandResult.get("result");
        if (!(resultObj instanceof Map)) return null;
        Object innerObj = ((Map<?, ?>) resultObj).get("result");
        if (!(innerObj instanceof Map)) return null;
        return ((Map<?, ?>) innerObj).get("value");
    }

    private void dispatchMouse(Socket socket, String type, double x, double y, String button, int buttons) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", type);
        params.put("x", x);
        params.put("y", y);
        params.put("button", button);
        params.put("buttons", buttons);
        if ("mousePressed".equals(type) || "mouseReleased".equals(type)) {
            params.put("clickCount", 1);
        }
        sendCommand(socket, "Input.dispatchMouseEvent", params);
    }

    /**
     * 等待人工完成滑块。成功标准：页面离开 punish/captcha、页面文本不再要求拖动，或 cookie 中出现 x5sec。
     */
    private boolean waitForCaptchaPassed(String cdpEndpoint, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            Socket socket = null;
            try {
                socket = openWebSocket(cdpEndpoint);
                Map<String, Object> script = new LinkedHashMap<>();
                script.put("expression", """
                    (() => {
                        const text = (document.body && document.body.innerText || '');
                        const href = location.href;
                        const cookie = document.cookie || '';
                        const hasSlider = !!document.querySelector('#nc_1_n1z, .btn_slide, .nc_iconfont, #nocaptcha');
                        const failed = /验证失败|点击框体重试|请刷新|重试/.test(text);
                        const passedByCookie = /(?:^|;\\s*)x5sec=/.test(cookie);
                        const stillPunish = /punish|captcha/.test(href) || /请按住滑块|拖动到最右边|验证码拦截/.test(text);
                        return JSON.stringify({href, text: text.slice(0, 300), hasSlider, failed, passedByCookie, stillPunish, cookie: cookie.slice(0, 1000)});
                    })()
                """);
                script.put("awaitPromise", true);
                script.put("returnByValue", true);
                Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
                if (value != null) {
                    JsonNode state = MAPPER.readTree(String.valueOf(value));
                    boolean passedByCookie = state.path("passedByCookie").asBoolean(false);
                    boolean stillPunish = state.path("stillPunish").asBoolean(true);
                    boolean failed = state.path("failed").asBoolean(false);
                    if (passedByCookie || !stillPunish) {
                        log.info("[CDP-AUTH] Captcha appears passed, state={}", state.toString());
                        return true;
                    }
                    if (failed) {
                        log.warn("[CDP-AUTH] Captcha failed on page, please click the box and drag manually again. state={}", state.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("[CDP-AUTH] wait captcha status failed: {}", e.getMessage());
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
            Thread.sleep(1000);
        }
        return false;
    }

    /**
     * 检查滑块验证是否成功
     */
    private boolean checkCaptchaSuccess(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (function() {
                    var successSelectors = ['.success', '.captcha-success', '[class*="success"]'];
                    for (var selector of successSelectors) {
                        if (document.querySelector(selector)) {
                            return true;
                        }
                    }
                    var errorSelectors = ['.error', '.captcha-error', '[class*="error"]'];
                    for (var selector of errorSelectors) {
                        if (document.querySelector(selector)) {
                            return false;
                        }
                    }
                    return true;
                })()
            """);
            script.put("awaitPromise", true);
            script.put("returnByValue", true);

            Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", script);

            if (result.containsKey("result")) {
                Map<String, Object> inner = (Map<String, Object>) result.get("result");
                return Boolean.TRUE.equals(Boolean.parseBoolean(String.valueOf(inner.get("value"))));
            }

            return false;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to check captcha success: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 从当前页面提取 cookie
     */
    private String extractCookie(String cdpEndpoint, String url) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            return extractCookies(socket);
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to extract cookie: {}", e.getMessage());
            return null;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
