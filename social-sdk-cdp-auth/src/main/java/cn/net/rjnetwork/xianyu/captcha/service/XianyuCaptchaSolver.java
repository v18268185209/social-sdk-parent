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
import java.util.regex.Matcher;

/**
 * 闲鱼滑块验证自动化工具 - 通过 CDP 远程控制浏览器完全自动化完成滑块验证
 */
@Service
public class XianyuCaptchaSolver {

    private static final Logger log = LoggerFactory.getLogger(XianyuCaptchaSolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

            // 1. 连接 CDP WebSocket
            String cdpEndpoint = getCdpEndpointFromBrowser();

            // 2. 获取所有页面列表
            Map<String, Object> targets = getTargets(cdpEndpoint);

            // 3. 查找或创建闲鱼标签页
            String targetId = findGoofishTargetId(targets);
            if (targetId == null) {
                targetId = createNewTarget(cdpEndpoint, "https://www.goofish.com/user/account-info");
            }

            // 4. 附加到目标页面
            attachToTarget(cdpEndpoint, targetId);

            // 5. 导航到闲鱼账户页面触发滑块
            navigateToAccountPage(cdpEndpoint, "https://www.goofish.com/user/account-info");
            Thread.sleep(5000);

            // 6. 检查是否有滑块验证界面
            boolean hasSlider = checkSliderExists(cdpEndpoint);

            if (!hasSlider) {
                triggerSliderCaptcha(cdpEndpoint);
                waitForElement(cdpEndpoint, ".slider-btn, .btn_slide, #slider-btn, [class*='slider']", 10000);
            }

            // 7. 自动拖拽滑块
            performAutoSliderDrag(cdpEndpoint);

            // 8. 等待验证完成
            Thread.sleep(3000);

            // 9. 检查是否成功
            boolean success = checkCaptchaSuccess(cdpEndpoint);
            if (!success) {
                return CaptchaResult.fail("滑块验证未通过");
            }

            // 10. 提取 cookie
            String cookie = extractCookie(cdpEndpoint, "https://www.goofish.com/user/account-info");
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
     * 连接 CDP WebSocket
     */
    private Socket openWebSocket(String wsUrl) throws Exception {
        URL url = new URL(wsUrl);
        String host = url.getHost();
        int port = url.getPort() > 0 ? url.getPort() : (url.getProtocol().equals("wss") ? 443 : 80);

        Socket socket = new Socket(host, port);
        socket.setSoTimeout(config.getTimeoutSeconds() * 1000);

        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        InputStream input = socket.getInputStream();

        // 生成 WebSocket Key
        byte[] keyBytes = new SecureRandom().generateSeed(16);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        // 发送 WebSocket 握手请求
        String upgradeRequest = "GET " + url.getPath() + " HTTP/1.1\r\n" +
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

        // 跳过响应头
        int headerEnd = response.indexOf("\r\n\r\n");
        if (headerEnd >= 0) {
            // 继续读取可能的附加头
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

    /**
     * 发送 CDP 命令
     */
    private Map<String, Object> sendCommand(Socket socket, String method, Map<String, Object> params) throws Exception {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        InputStream input = socket.getInputStream();

        // 构造请求 JSON
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", System.nanoTime());
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }

        String json = MAPPER.writeValueAsString(request);

        // 构造 WebSocket 文本帧
        byte[] textBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[2 + textBytes.length];
        frame[0] = (byte) 0x81; // 文本帧
        frame[1] = (byte) (textBytes.length & 0xFF);
        System.arraycopy(textBytes, 0, frame, 2, textBytes.length);

        OutputStream output = socket.getOutputStream();
        output.write(frame);
        output.flush();

        // 读取 WebSocket 响应帧
        byte[] firstByte = new byte[1];
        int read = input.read(firstByte);
        if (read == -1) return new LinkedHashMap<>();

        boolean isFinal = (firstByte[0] & 0x80) != 0;
        int opcode = firstByte[0] & 0x0F;

        // 处理扩展位
        int maskBit = firstByte[0] & 0x80;
        int length = firstByte[1] & 0x7F;
        byte[] payload;

        if (length < 126) {
            payload = new byte[length];
            readBytes(input, payload);
        } else if (length == 126) {
            byte[] ext = new byte[2];
            readBytes(input, ext);
            length = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
            payload = new byte[length];
            readBytes(input, payload);
        } else {
            byte[] ext = new byte[8];
            readBytes(input, ext);
            long len = 0;
            for (byte b : ext) len = (len << 8) | (b & 0xFF);
            payload = new byte[(int) len];
            readBytes(input, payload);
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

            if (result.containsKey("result")) {
                Map<String, Object> inner = (Map<String, Object>) result.get("result");
                String json = String.valueOf(inner.get("value"));
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
     * 自动拖拽滑块（模拟鼠标拖动）
     */
    private void performAutoSliderDrag(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);

            Map<String, Object> positionScript = new LinkedHashMap<>();
            positionScript.put("expression", """
                (function() {
                    var selectors = ['input[type="range"]', '.goofish-slider-track', '[class*="slider"]'];
                    for (var selector of selectors) {
                        var el = document.querySelector(selector);
                        if (el) {
                            var rect = el.getBoundingClientRect();
                            return JSON.stringify({
                                x: rect.left + rect.width / 2,
                                y: rect.top + rect.height / 2,
                                width: rect.width,
                                height: rect.height
                            });
                        }
                    }
                    return null;
                })()
            """);
            positionScript.put("awaitPromise", true);
            positionScript.put("returnByValue", true);

            Map<String, Object> positionResult = sendCommand(socket, "Runtime.evaluate", positionScript);

            double startX = 100, startY = 100;
            double endX = 300, endY = 100;

            try {
                if (positionResult.containsKey("result")) {
                    Map<String, Object> inner = (Map<String, Object>) positionResult.get("result");
                    String posStr = String.valueOf(inner.get("value"));
                    if (posStr != null && !posStr.equals("null")) {
                        JsonNode node = MAPPER.readTree(posStr);
                        startX = node.path("x").asDouble(startX);
                        startY = node.path("y").asDouble(startY);
                        endX = startX + node.path("width").asDouble(200);
                    }
                }
            } catch (Exception e) {
                log.warn("[CDP-AUTH] Failed to parse position: {}", e.getMessage());
            }

            Map<String, Object> dragScript = new LinkedHashMap<>();
            dragScript.put("expression", String.format("""
                (function() {
                    var startX = %f, startY = %f;
                    var endX = %f, endY = %f;

                    var downEvent = new MouseEvent('mousedown', {
                        bubbles: true, cancelable: true, clientX: startX, clientY: startY
                    });
                    document.dispatchEvent(downEvent);

                    var moveCount = 20;
                    for (var i = 0; i <= moveCount; i++) {
                        var t = i / moveCount;
                        var x = startX + (endX - startX) * t + (Math.random() - 0.5) * 3;
                        var y = startY + (Math.random() - 0.5) * 2;

                        var moveEvent = new MouseEvent('mousemove', {
                            bubbles: true, cancelable: true, clientX: x, clientY: y
                        });
                        document.dispatchEvent(moveEvent);
                    }

                    var upEvent = new MouseEvent('mouseup', {
                        bubbles: true, cancelable: true, clientX: endX, clientY: endY
                    });
                    document.dispatchEvent(upEvent);

                    return true;
                })()
            """, startX, startY, endX, endY));
            dragScript.put("awaitPromise", true);
            dragScript.put("returnByValue", true);

            sendCommand(socket, "Runtime.evaluate", dragScript);

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
