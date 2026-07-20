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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

/**
 * 闲鱼滑块验证自动化工具 - 通过 CDP 远程控制浏览器全自动完成滑块验证
 *
 * <p>核心算法参考自 E:\codes\xianyu-auto-reply 项目：
 * <ul>
 *   <li>Bezier 曲线 + 抖动生成人类化轨迹（加速-减速-末端微回退）</li>
 *   <li>通过 CDP Input.dispatchMouseEvent 逐点派发鼠标事件</li>
 *   <li>自动重试，每次重试抖动轨迹参数</li>
 *   <li>验证成功后提取 x5sec cookie</li>
 * </ul>
 *
 * <p><b>重要：</b>滑块验证获取的 cookie（x5sec 等）是 IM 专用 cookie，
 * 与登录 cookie 不同，必须分开存储，不能覆盖登录 cookie。
 */
@Service
public class XianyuCaptchaSolver {

    private static final Logger log = LoggerFactory.getLogger(XianyuCaptchaSolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CdpCaptchaConfig config;

    /** 滑块轨迹生成参数：人类拖动先加速、再减速、末端可能微回退 */
    private static final int TRAJECTORY_BASE_POINTS = 80;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    /** 每次拖动前的思考时间范围（毫秒） */
    private static final long PRE_DRAG_DELAY_MIN = 200;
    private static final long PRE_DRAG_DELAY_MAX = 800;

    public XianyuCaptchaSolver(CdpCaptchaConfig config) {
        this.config = config;
    }

    /**
     * 尝试通过 CDP 自动完成滑块验证
     *
     * @param punishUrl 闲鱼风控 punish URL
     * @return 验证码处理结果，包含新 cookie（x5sec 等，IM 专用）
     */
    public CaptchaResult solve(String punishUrl) {
        log.info("[CDP-AUTH] 开始自动滑块验证, URL: {}", truncate(punishUrl, 200));

        // 1. 获取浏览器级 CDP WebSocket，用于 Target.* 命令
        String browserEndpoint;
        try {
            browserEndpoint = getCdpEndpointFromBrowser();
        } catch (Exception e) {
            return CaptchaResult.fail("无法连接 CDP 浏览器：" + e.getMessage());
        }

        // 2. 获取所有页面列表
        Map<String, Object> targets;
        try {
            targets = getTargets(browserEndpoint);
        } catch (Exception e) {
            return CaptchaResult.fail("获取 CDP 目标列表失败：" + e.getMessage());
        }

        // 3. 查找或创建闲鱼标签页
        String targetId = findGoofishTargetId(targets);
        if (targetId == null) {
            try {
                targetId = createNewTarget(browserEndpoint, "about:blank");
            } catch (Exception e) {
                return CaptchaResult.fail("创建 CDP 标签页失败：" + e.getMessage());
            }
        }
        if (targetId == null || targetId.isBlank()) {
            return CaptchaResult.fail("未找到或创建 CDP 页面目标");
        }

        // 4. 获取页面级 CDP WebSocket。Page/Runtime/Input 命令必须发到 page 端点
        String cdpEndpoint;
        try {
            cdpEndpoint = getPageWebSocketEndpoint(targetId);
        } catch (Exception e) {
            return CaptchaResult.fail("获取页面 CDP 端点失败：" + e.getMessage());
        }

        // 5. 打开 punish URL 触发滑块
        try {
            navigateToAccountPage(cdpEndpoint, punishUrl);
            Thread.sleep(3000); // 等待页面加载和滑块渲染
        } catch (Exception e) {
            return CaptchaResult.fail("导航到验证页面失败：" + e.getMessage());
        }

        // 6. 自动拖动滑块，最多重试 3 次
        boolean success = false;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            log.info("[CDP-AUTH] 滑块验证第 {}/{} 次尝试", attempt, MAX_RETRY_ATTEMPTS);

            // 检查是否已经通过（人工已完成）
            try {
                if (checkAlreadyPassed(cdpEndpoint)) {
                    log.info("[CDP-AUTH] 检测到验证已通过（可能人工已完成）");
                    success = true;
                    break;
                }
            } catch (Exception e) {
                // 忽略，继续自动拖动
            }

            // 执行自动拖动
            try {
                performAutoSliderDrag(cdpEndpoint, attempt);
            } catch (Exception e) {
                log.warn("[CDP-AUTH] 第 {} 次拖动执行异常: {}", attempt, e.getMessage());
            }

            // 等待验证结果
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 检查是否通过
            try {
                if (checkAlreadyPassed(cdpEndpoint)) {
                    log.info("[CDP-AUTH] 第 {} 次拖动后验证通过", attempt);
                    success = true;
                    break;
                } else {
                    log.warn("[CDP-AUTH] 第 {} 次拖动后验证未通过", attempt);
                    // 点击刷新按钮准备重试
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        clickRefreshButton(cdpEndpoint);
                        Thread.sleep(1500);
                    }
                }
            } catch (Exception e) {
                log.warn("[CDP-AUTH] 检查验证状态异常: {}", e.getMessage());
            }
        }

        if (!success) {
            return CaptchaResult.fail("滑块验证失败，已尝试 " + MAX_RETRY_ATTEMPTS + " 次");
        }

        // 7. 提取 cookie（x5sec 等）
        try {
            Thread.sleep(1000); // 等待 cookie 落盘
            String cookie = extractCookie(cdpEndpoint, punishUrl);
            if (cookie != null && !cookie.isEmpty() && cookie.contains("x5sec")) {
                log.info("[CDP-AUTH] Cookie 提取成功，包含 x5sec");
                return CaptchaResult.ok(cookie);
            } else if (cookie != null && !cookie.isEmpty()) {
                log.warn("[CDP-AUTH] Cookie 提取成功但未包含 x5sec，可能验证未真正通过");
                return CaptchaResult.fail("验证后 cookie 中未找到 x5sec，验证可能未真正通过");
            } else {
                return CaptchaResult.fail("未获取到新 cookie");
            }
        } catch (Exception e) {
            return CaptchaResult.fail("提取 cookie 异常：" + e.getMessage());
        }
    }

    /**
     * 检查验证是否已经通过（页面已离开 punish 或 cookie 中有 x5sec）
     */
    private boolean checkAlreadyPassed(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (() => {
                    const href = location.href || '';
                    const cookie = document.cookie || '';
                    const hasSlider = !!document.querySelector('#nc_1_n1z, .btn_slide, .nc_iconfont, #nocaptcha');
                    const passedByCookie = /(?:^|;\\s*)x5sec=/.test(cookie);
                    const stillPunish = /punish|captcha/.test(href);
                    return JSON.stringify({passedByCookie, hasSlider, stillPunish});
                })()
            """);
            script.put("awaitPromise", true);
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            if (value != null) {
                JsonNode state = MAPPER.readTree(String.valueOf(value));
                boolean passedByCookie = state.path("passedByCookie").asBoolean(false);
                boolean hasSlider = state.path("hasSlider").asBoolean(true);
                boolean stillPunish = state.path("stillPunish").asBoolean(true);
                return passedByCookie || (!hasSlider && !stillPunish);
            }
        } catch (Exception e) {
            log.debug("[CDP-AUTH] checkAlreadyPassed 异常: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        return false;
    }

    /**
     * 自动拖拽滑块 - 使用人类化轨迹算法
     *
     * <p>轨迹生成参考自 xianyu-auto-reply 项目的 TrajectoryGenerator：
     * <ul>
     *   <li>总位移 = 滑块轨道宽度 - 滑块宽度 + 随机过冲</li>
     *   <li>速度曲线：先加速（0-60%）、匀速（60-80%）、减速（80-100%）</li>
     *   <li>末端加入微回退（真人特征）</li>
     *   <li>Y 轴加入随机抖动</li>
     * </ul>
     */
    private void performAutoSliderDrag(String cdpEndpoint, int attemptIndex) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);

            // 1. 获取滑块和轨道位置
            Map<String, Object> positionScript = new LinkedHashMap<>();
            positionScript.put("expression", """
                (() => {
                    const slider = document.querySelector('#nc_1_n1z, .btn_slide, .nc_iconfont');
                    const track = document.querySelector('#nc_1_nocaptcha, #nocaptcha, .nc-container');
                    if (!slider || !track) return null;
                    const sr = slider.getBoundingClientRect();
                    const tr = track.getBoundingClientRect();
                    return JSON.stringify({
                        startX: sr.left + sr.width / 2,
                        startY: sr.top + sr.height / 2,
                        sliderWidth: sr.width,
                        trackWidth: tr.width,
                        trackLeft: tr.left,
                        trackRight: tr.right
                    });
                })()
            """);
            positionScript.put("awaitPromise", true);
            positionScript.put("returnByValue", true);

            Map<String, Object> positionResult = sendCommand(socket, "Runtime.evaluate", positionScript);
            Object runtimeValue = extractRuntimeValue(positionResult);
            if (runtimeValue == null || "null".equals(String.valueOf(runtimeValue))) {
                throw new IllegalStateException("未找到滑块元素");
            }

            JsonNode node = MAPPER.readTree(String.valueOf(runtimeValue));
            double startX = node.path("startX").asDouble(100);
            double startY = node.path("startY").asDouble(100);
            double sliderWidth = node.path("sliderWidth").asDouble(40);
            double trackWidth = node.path("trackWidth").asDouble(300);

            // 计算拖动距离：轨道宽度 - 滑块宽度 + 随机过冲（真人会拖过一点）
            double dragDistance = trackWidth - sliderWidth + (RANDOM.nextDouble() * 4 - 2);
            log.info("[CDP-AUTH] 滑块位置: ({}, {}), 拖动距离: {}px", startX, startY, dragDistance);

            // 2. 生成人类化轨迹
            List<double[]> trajectory = generateHumanTrajectory(dragDistance, attemptIndex);

            // 3. 移动到滑块位置（带随机偏移，模拟真人移动）
            double approachX = startX + (RANDOM.nextDouble() * 20 - 10);
            double approachY = startY + (RANDOM.nextDouble() * 10 - 5);
            dispatchMouse(socket, "mouseMoved", approachX, approachY, "none", 0);
            Thread.sleep(100 + RANDOM.nextInt(200));

            // 4. 按下鼠标
            dispatchMouse(socket, "mousePressed", startX, startY, "left", 1);
            Thread.sleep(50 + RANDOM.nextInt(100));

            // 5. 按轨迹逐点移动
            double currentX = startX;
            double currentY = startY;
            for (double[] point : trajectory) {
                currentX = startX + point[0];
                currentY = startY + point[1];
                dispatchMouse(socket, "mouseMoved", currentX, currentY, "left", 1);
                Thread.sleep((long) (point[2] * 1000)); // point[2] 是时间间隔（秒）
            }

            // 6. 释放前短暂停顿（真人特征）
            Thread.sleep(200 + RANDOM.nextInt(150));

            // 7. 释放鼠标
            dispatchMouse(socket, "mouseReleased", currentX, currentY, "left", 0);

            log.info("[CDP-AUTH] 滑块拖动完成，轨迹点数: {}", trajectory.size());

        } catch (Exception e) {
            log.error("[CDP-AUTH] 滑块拖动失败: {}", e.getMessage(), e);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 生成人类化拖动轨迹
     *
     * <p>轨迹特征：
     * <ul>
     *   <li>加速阶段（0-40%）：加速度逐渐减小</li>
     *   <li>匀速阶段（40-70%）：近似匀速</li>
     *   <li>减速阶段（70-95%）：减速度逐渐增大</li>
     *   <li>末端过冲+回退（95-100%）：真人特征</li>
     * </ul>
     *
     * @param distance 总拖动距离（像素）
     * @param attemptIndex 当前尝试次数（用于调整参数）
     * @return 轨迹点列表，每个点为 [dx, dy, dt]（相对位移x, 相对位移y, 时间间隔）
     */
    private List<double[]> generateHumanTrajectory(double distance, int attemptIndex) {
        List<double[]> trajectory = new ArrayList<>();
        int points = TRAJECTORY_BASE_POINTS + RANDOM.nextInt(20);

        // 根据尝试次数微调参数（模拟真人每次尝试的差异）
        double speedFactor = 0.8 + RANDOM.nextDouble() * 0.4; // 0.8-1.2
        double overshoot = 2 + RANDOM.nextDouble() * 3; // 过冲 2-5px

        double totalTime = 0.4 + RANDOM.nextDouble() * 0.3; // 总时间 0.4-0.7s
        double accumulatedX = 0;

        for (int i = 1; i <= points; i++) {
            double t = (double) i / points; // 归一化进度 0-1

            // 位移曲线：S 型曲线（加速-匀速-减速）
            double progress;
            if (t < 0.4) {
                // 加速阶段
                progress = t * t * 1.25;
            } else if (t < 0.7) {
                // 匀速阶段
                progress = 0.2 + (t - 0.4) * 1.333;
            } else {
                // 减速阶段 + 末端过冲
                double decelT = (t - 0.7) / 0.3;
                progress = 0.6 + 0.4 * (1 - Math.pow(1 - decelT, 2));
            }

            // 末端过冲（真人会拖过一点再回退）
            if (t > 0.95) {
                progress = 1.0 + overshoot * (t - 0.95) / 0.05;
            }
            if (t > 0.98) {
                progress = 1.0 + overshoot * (1 - (t - 0.98) / 0.02);
            }

            double targetX = distance * Math.min(progress, 1.0);
            double dx = targetX - accumulatedX;
            accumulatedX = targetX;

            // Y 轴抖动（模拟真人手抖）
            double dy = (RANDOM.nextDouble() - 0.5) * 1.5;
            if (t < 0.1) {
                dy = (RANDOM.nextDouble() - 0.5) * 0.5; // 起始阶段抖动小
            }

            // 时间间隔：加速阶段间隔大，减速阶段间隔小
            double baseDt = totalTime / points / speedFactor;
            double dt = baseDt * (0.5 + RANDOM.nextDouble() * 0.5);
            if (t > 0.7 && t < 0.95) {
                dt *= 1.3; // 减速阶段更慢
            }

            trajectory.add(new double[]{dx, dy, dt});
        }

        return trajectory;
    }

    /**
     * 点击滑块刷新按钮（验证失败后重试）
     */
    private void clickRefreshButton(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (() => {
                    const selectors = ['#nc_1_refresh1', '.nc_iconfont.btn_refresh', '.errloading', '[class*="refresh"]'];
                    for (const sel of selectors) {
                        const el = document.querySelector(sel);
                        if (el && el.offsetParent !== null) {
                            el.click();
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
            log.debug("[CDP-AUTH] 点击刷新按钮异常: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ======================== CDP 基础通信 ========================

    /**
     * 通过浏览器远程调试接口获取 CDP WebSocket 端点
     */
    private String getCdpEndpointFromBrowser() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getCdpEndpoint() + "/json/version"))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = MAPPER.readTree(response.body());
        String wsUrl = json.path("webSocketDebuggerUrl").asText("");
        if (wsUrl.isEmpty()) {
            throw new IllegalStateException("No webSocketDebuggerUrl in /json/version response");
        }
        return wsUrl;
    }

    /**
     * 获取页面级 CDP WebSocket 端点（用于 Page/Runtime/Input 命令）
     */
    private String getPageWebSocketEndpoint(String targetId) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getCdpEndpoint() + "/json/list"))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode list = MAPPER.readTree(response.body());
        if (list.isArray()) {
            for (JsonNode target : list) {
                String id = target.path("id").asText("");
                if (id.equals(targetId)) {
                    String wsUrl = target.path("webSocketDebuggerUrl").asText("");
                    if (!wsUrl.isEmpty()) return wsUrl;
                }
            }
        }
        throw new IllegalStateException("No page webSocketDebuggerUrl for target " + targetId);
    }

    /**
     * 建立 CDP WebSocket 连接
     */
    private Socket openWebSocket(String wsUrl) throws Exception {
        URI uri = URI.create(wsUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        String path = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

        Socket socket = new Socket(host, port);
        socket.setSoTimeout(10000);

        // WebSocket 握手
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        String handshake = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n";

        OutputStream out = socket.getOutputStream();
        out.write(handshake.getBytes(StandardCharsets.UTF_8));
        out.flush();

        // 读取响应头
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (true) {
            int n = in.read(buf);
            if (n == -1) throw new IOException("WebSocket handshake: connection closed");
            headerBuf.write(buf, 0, n);
            byte[] all = headerBuf.toByteArray();
            int headerEnd = findHeaderEnd(all);
            if (headerEnd >= 0) {
                String response = new String(all, 0, headerEnd, StandardCharsets.UTF_8);
                if (!response.contains("101")) {
                    throw new IOException("WebSocket handshake failed: " + response.substring(0, Math.min(200, response.length())));
                }
                break;
            }
        }
        return socket;
    }

    /**
     * 获取所有 CDP 目标
     */
    private Map<String, Object> getTargets(String cdpEndpoint) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getCdpEndpoint() + "/json/list"))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode list = MAPPER.readTree(response.body());
        Map<String, Object> result = new LinkedHashMap<>();
        if (list.isArray()) {
            for (JsonNode target : list) {
                String id = target.path("id").asText("");
                String title = target.path("title").asText("");
                String url = target.path("url").asText("");
                String type = target.path("type").asText("");
                Map<String, String> info = new LinkedHashMap<>();
                info.put("title", title);
                info.put("url", url);
                info.put("type", type);
                result.put(id, info);
            }
        }
        return result;
    }

    /**
     * 查找闲鱼相关标签页
     */
    private String findGoofishTargetId(Map<String, Object> targets) {
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            Map<String, String> info = (Map<String, String>) entry.getValue();
            String url = info.getOrDefault("url", "");
            String title = info.getOrDefault("title", "");
            if (url.contains("goofish") || url.contains("xianyu") || title.contains("闲鱼") || title.contains("咸鱼")) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 创建新标签页
     */
    private String createNewTarget(String cdpEndpoint, String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getCdpEndpoint() + "/json/new?" + url))
                .timeout(java.time.Duration.ofSeconds(5))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = MAPPER.readTree(response.body());
        return json.path("id").asText("");
    }

    /**
     * 导航到指定页面
     */
    private void navigateToAccountPage(String cdpEndpoint, String url) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", url);
            sendCommand(socket, "Page.navigate", params);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 发送 CDP 命令（带掩码的 WebSocket 帧）
     */
    private Map<String, Object> sendCommand(Socket socket, String method, Map<String, Object> params) throws Exception {
        int id = (int) (System.currentTimeMillis() % 100000);
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("id", id);
        command.put("method", method);
        command.put("params", params != null ? params : new LinkedHashMap<>());

        String json = MAPPER.writeValueAsString(command);
        writeFrame(socket, json);

        // 读取响应
        return readResponse(socket, id);
    }

    /**
     * 读取 CDP 响应（匹配 id）
     */
    private Map<String, Object> readResponse(Socket socket, int expectedId) throws Exception {
        InputStream in = socket.getInputStream();
        byte[] buf = new byte[65536];
        ByteArrayOutputStream frameBuf = new ByteArrayOutputStream();

        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            int n = in.read(buf);
            if (n == -1) throw new IOException("Connection closed while reading response");
            frameBuf.write(buf, 0, n);

            // 尝试解析帧
            byte[] data = frameBuf.toByteArray();
            int offset = 0;
            while (data.length - offset >= 2) {
                int opcode = data[offset] & 0x0F;
                int secondByte = data[offset + 1] & 0xFF;
                long payloadLen = secondByte & 0x7F;
                offset += 2;

                if (payloadLen == 126) {
                    if (data.length < offset + 2) break;
                    payloadLen = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                    offset += 2;
                } else if (payloadLen == 127) {
                    if (data.length < offset + 8) break;
                    payloadLen = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLen = (payloadLen << 8) | (data[offset + i] & 0xFF);
                    }
                    offset += 8;
                }

                boolean masked = (secondByte & 0x80) != 0;
                if (masked) offset += 4;

                if (data.length < offset + payloadLen) break;

                byte[] payload = new byte[(int) payloadLen];
                System.arraycopy(data, offset, payload, 0, (int) payloadLen);
                offset += (int) payloadLen;

                if (opcode == 1) {
                    String text = new String(payload, StandardCharsets.UTF_8);
                    if (text.startsWith("{")) {
                        try {
                            JsonNode json = MAPPER.readTree(text);
                            if (json.has("id") && json.path("id").asInt(-1) == expectedId) {
                                return MAPPER.convertValue(json, Map.class);
                            }
                        } catch (Exception e) {
                            // 忽略非 JSON 或 id 不匹配的帧
                        }
                    }
                }

                // 移除已处理的数据
                frameBuf.reset();
                if (data.length > offset) {
                    frameBuf.write(data, offset, data.length - offset);
                }
                data = frameBuf.toByteArray();
                offset = 0;
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * 写入带掩码的 WebSocket 帧
     */
    private void writeFrame(Socket socket, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] maskKey = new byte[4];
        RANDOM.nextBytes(maskKey);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x81); // FIN + text opcode
        int len = payload.length;
        if (len < 126) {
            out.write(0x80 | len);
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            for (int i = 56; i >= 0; i -= 8) {
                out.write((len >> i) & 0xFF);
            }
        }
        out.write(maskKey);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ maskKey[i % 4]);
        }
        socket.getOutputStream().write(out.toByteArray());
        socket.getOutputStream().flush();
    }

    /**
     * 发送 CDP Input.dispatchMouseEvent
     */
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
     * 从当前页面提取 cookie
     */
    private String extractCookie(String cdpEndpoint, String url) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> cookieScript = new LinkedHashMap<>();
            cookieScript.put("expression", """
                (() => {
                    const cookies = document.cookie.split(';');
                    const result = {};
                    for (const c of cookies) {
                        const parts = c.trim().split('=');
                        if (parts.length >= 2) {
                            result[parts[0]] = parts.slice(1).join('=');
                        }
                    }
                    return JSON.stringify(result);
                })()
            """);
            cookieScript.put("awaitPromise", true);
            cookieScript.put("returnByValue", true);

            Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", cookieScript);
            Object value = extractRuntimeValue(result);
            if (value != null) {
                String json = String.valueOf(value);
                if (!json.equals("null")) {
                    // 只保留 x5* 相关 cookie（风控验证后的关键 cookie）
                    JsonNode node = MAPPER.readTree(json);
                    StringBuilder sb = new StringBuilder();
                    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String name = entry.getKey();
                        String val = entry.getValue().asText("");
                        if (name.toLowerCase().startsWith("x5") || name.toLowerCase().contains("x5sec")) {
                            if (sb.length() > 0) sb.append("; ");
                            sb.append(name).append("=").append(val);
                        }
                    }
                    // 如果没有 x5* cookie，返回所有 cookie
                    if (sb.length() == 0) {
                        fields = node.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            if (sb.length() > 0) sb.append("; ");
                            sb.append(entry.getKey()).append("=").append(entry.getValue().asText(""));
                        }
                    }
                    return sb.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] 提取 cookie 失败: {}", e.getMessage());
            return null;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ======================== 工具方法 ========================

    private int findHeaderEnd(byte[] data) {
        for (int i = 0; i <= data.length - 4; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    private Object extractRuntimeValue(Map<String, Object> commandResult) {
        if (commandResult == null || !commandResult.containsKey("result")) return null;
        Object resultObj = commandResult.get("result");
        if (!(resultObj instanceof Map)) return null;
        Object innerObj = ((Map<?, ?>) resultObj).get("result");
        if (!(innerObj instanceof Map)) return null;
        return ((Map<?, ?>) innerObj).get("value");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
