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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

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
        Socket socket = null;
        try {
            log.info("[CDP-AUTH] Connecting to {}...", config.getCdpEndpoint());

            // 1. 连接 CDP WebSocket
            String wsUrl = "ws://" + config.getHost() + ":" + config.getPort() + "/devtools/browser";
            socket = openWebSocket(wsUrl);

            // 2. 发送 getTargets 命令获取页面列表
            Map<String, Object> targets = sendCommand(socket, "Target.getTargets", null);
            String targetId = findGoofishTargetId(targets);

            if (targetId == null) {
                return CaptchaResult.fail("未找到闲鱼目标页面");
            }

            // 3. 导航到闲鱼账户页面触发滑块
            String accountPageUrl = "https://www.goofish.com/user/account-info";
            log.info("[CDP-AUTH] Navigate to: {}", accountPageUrl);

            Map<String, Object> navigateParams = new LinkedHashMap<>();
            navigateParams.put("url", accountPageUrl);
            sendCommand(socket, "Page.enable", null);
            sendCommand(socket, "Runtime.enable", null);
            Map<String, Object> navResult = sendCommand(socket, "Page.navigate", navigateParams);

            // 等待页面加载
            Thread.sleep(5000);

            // 4. 触发滑块验证
            triggerSliderCaptcha(socket);

            // 5. 等待滑块出现
            waitForElement(socket, ".slider-btn, .btn_slide, #slider-btn, [class*='slider']", 10000);

            // 6. 自动拖拽滑块
            performAutoSliderDrag(socket);

            // 7. 等待验证完成
            Thread.sleep(3000);

            // 8. 检查是否成功
            boolean success = checkCaptchaSuccess(socket);
            if (!success) {
                return CaptchaResult.fail("滑块验证未通过");
            }

            // 9. 提取 cookie
            String cookie = extractCookie(socket, accountPageUrl);
            if (cookie != null && !cookie.isEmpty()) {
                log.info("[CDP-AUTH] Cookie extracted successfully");
                return CaptchaResult.ok(cookie);
            }

            return CaptchaResult.fail("未获取到新 cookie");

        } catch (Exception e) {
            log.error("[CDP-AUTH] Captcha solving failed: {}", e.getMessage(), e);
            return CaptchaResult.fail("验证码处理异常：" + e.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 打开 WebSocket 连接
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
     * 触发滑块验证
     */
    private void triggerSliderCaptcha(Socket socket) {
        try {
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
        }
    }

    /**
     * 等待元素出现
     */
    private void waitForElement(Socket socket, String selector, long timeoutMs) throws InterruptedException {
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
    }

    /**
     * 自动拖拽滑块
     */
    private void performAutoSliderDrag(Socket socket) {
        try {
            // 获取滑块位置
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

            // 发送鼠标事件模拟拖拽
            Map<String, Object> dragScript = new LinkedHashMap<>();
            dragScript.put("expression", String.format("""
                (function() {
                    var startX = %f, startY = %f;
                    var endX = %f, endY = %f;

                    // mousedown
                    var downEvent = new MouseEvent('mousedown', {
                        bubbles: true, cancelable: true, clientX: startX, clientY: startY
                    });
                    document.dispatchEvent(downEvent);

                    // mousemove events
                    var moveCount = 20;
                    for (var i = 0; i <= moveCount; i++) {
                        var t = i / moveCount;
                        var x = startX + (endX - startX) * t + (Math.random() - 0.5) * 3;
                        var y = startY + (Math.random() - 0.5) * 2;

                        var moveEvent = new MouseEvent('mousemove', {
                            bubbles: true, cancelable: true, clientX: x, clientY: y
                        });
                        document.dispatchEvent(moveEvent);
                        Thread.sleep(50);
                    }

                    // mouseup
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
        }
    }

    /**
     * 检查滑块验证是否成功
     */
    private boolean checkCaptchaSuccess(Socket socket) {
        try {
            Map<String, Object> checkScript = new LinkedHashMap<>();
            checkScript.put("expression", """
                (function() {
                    // 检查滑块是否还存在
                    var sliderSelectors = ['.slider-btn', '.btn_slide', '#slider-btn', '[class*="slider"]'];
                    for (var selector of sliderSelectors) {
                        if (document.querySelector(selector)) {
                            return false;
                        }
                    }

                    // 检查是否有成功标记
                    var successSelectors = ['.captcha-success', '[class*="success"]', '[id*="success"]'];
                    for (var selector of successSelectors) {
                        if (document.querySelector(selector)) {
                            return true;
                        }
                    }

                    // 检查 URL 是否跳转到成功页面
                    var url = window.location.href;
                    return url.includes('captcha') && !url.includes('fail');
                })()
            """);
            checkScript.put("awaitPromise", true);
            checkScript.put("returnByValue", true);

            Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", checkScript);

            if (result.containsKey("result")) {
                Map<String, Object> inner = (Map<String, Object>) result.get("result");
                return Boolean.TRUE.equals(Boolean.parseBoolean(String.valueOf(inner.get("value"))));
            }

            return false;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to check captcha success: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 CDP 提取 cookie
     */
    private String extractCookie(Socket socket, String pageUrl) {
        try {
            Map<String, Object> cookieScript = new LinkedHashMap<>();
            cookieScript.put("expression", """
                function getCookies() {
                    var cookies = document.cookie.split(';');
                    var cookieObj = {};
                    for (var cookie of cookies) {
                        var parts = cookie.trim().split('=');
                        if (parts.length === 2) {
                            cookieObj[parts[0]] = parts[1];
                        }
                    }
                    return JSON.stringify(cookieObj);
                }
                getCookies()
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
        }
    }
}
