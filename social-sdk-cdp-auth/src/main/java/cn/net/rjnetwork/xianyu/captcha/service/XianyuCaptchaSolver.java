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
    private static final String IM_PAGE_URL = "https://www.goofish.com/im";

    /** 滑块轨迹生成参数：人类拖动先加速、再减速、末端可能微回退 */
    private static final int TRAJECTORY_BASE_POINTS = 80;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    /** 每次拖动前的思考时间范围（毫秒） */
    private static final long PRE_DRAG_DELAY_MIN = 200;
    private static final long PRE_DRAG_DELAY_MAX = 800;

    public XianyuCaptchaSolver(CdpCaptchaConfig config) {
        this.config = config;
    }

    private volatile String effectiveCdpEndpoint;

    public String getCdpHttpEndpoint() {
        String cached = effectiveCdpEndpoint;
        if (cached != null && !cached.isBlank()) return cached;
        String configured = config.getCdpEndpoint();
        for (String candidate : List.of(configured, "http://127.0.0.1:9222")) {
            if (candidate == null || candidate.isBlank()) continue;
            if (isCdpAlive(candidate)) {
                effectiveCdpEndpoint = candidate;
                if (!candidate.equals(configured)) {
                    log.warn("[CDP-AUTH] configured CDP {} unavailable, fallback to {}", configured, candidate);
                }
                return candidate;
            }
        }
        return configured;
    }

    private boolean isCdpAlive(String endpoint) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/json/version"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300
                    && response.body() != null && response.body().contains("webSocketDebuggerUrl");
        } catch (Exception e) {
            return false;
        }
    }

    public String getManualVerificationPageUrl() {
        return IM_PAGE_URL;
    }

    public Map<String, Object> getControlSnapshot() throws Exception {
        String pageEndpoint = ensureGoofishImPageEndpoint();
        try (Socket socket = openWebSocket(pageEndpoint)) {
            sendCommand(socket, "Page.enable", new LinkedHashMap<>());
            sendCommand(socket, "Runtime.enable", new LinkedHashMap<>());
            sendCommand(socket, "Network.enable", new LinkedHashMap<>());
            installAntiDetect(socket);

            Map<String, Object> eval = new LinkedHashMap<>();
            eval.put("expression", "JSON.stringify({url: location.href, title: document.title, width: innerWidth, height: innerHeight, dpr: devicePixelRatio})");
            eval.put("returnByValue", true);
            Object stateVal = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", eval));
            JsonNode state = stateVal != null ? MAPPER.readTree(String.valueOf(stateVal)) : MAPPER.createObjectNode();

            Map<String, Object> shotParams = new LinkedHashMap<>();
            shotParams.put("format", "jpeg");
            shotParams.put("quality", 82);
            shotParams.put("fromSurface", true);
            Map<String, Object> shot = sendCommand(socket, "Page.captureScreenshot", shotParams);
            String data = MAPPER.valueToTree(shot.get("result")).path("data").asText("");

            String cookie = extractCookie(pageEndpoint, IM_PAGE_URL);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", state.path("url").asText(""));
            result.put("title", state.path("title").asText(""));
            result.put("width", state.path("width").asInt(0));
            result.put("height", state.path("height").asInt(0));
            result.put("devicePixelRatio", state.path("dpr").asDouble(1));
            result.put("image", data);
            result.put("imageType", "image/jpeg");
            result.put("cdpEndpoint", getCdpHttpEndpoint());
            result.put("imPageUrl", IM_PAGE_URL);
            result.put("cookieUsable", isUsableImCookie(cookie));
            return result;
        }
    }

    public void dispatchControlMouse(String eventType, double x, double y) throws Exception {
        String pageEndpoint = ensureGoofishImPageEndpoint();
        try (Socket socket = openWebSocket(pageEndpoint)) {
            String type = switch (String.valueOf(eventType)) {
                case "down", "mousePressed" -> "mousePressed";
                case "up", "mouseReleased" -> "mouseReleased";
                default -> "mouseMoved";
            };
            String button = "mouseMoved".equals(type) ? "none" : "left";
            int buttons = "mouseReleased".equals(type) ? 0 : ("mouseMoved".equals(type) ? 0 : 1);
            dispatchMouse(socket, type, x, y, button, buttons);
        }
    }

    public String extractCurrentImCookie() throws Exception {
        return extractCookie(ensureGoofishImPageEndpoint(), IM_PAGE_URL);
    }

    private String ensureGoofishImPageEndpoint() throws Exception {
        String browserEndpoint = getCdpEndpointFromBrowser();
        Map<String, Object> targets = getTargets(browserEndpoint);
        String targetId = findGoofishTargetId(targets);
        if (targetId == null || targetId.isBlank()) {
            targetId = createNewTarget(browserEndpoint, "about:blank");
        }
        String pageEndpoint = getPageWebSocketEndpoint(targetId);
        boolean shouldNavigate = true;
        Object infoObj = targets.get(targetId);
        if (infoObj instanceof Map<?, ?> info) {
            Object urlObj = info.get("url");
            String url = urlObj != null ? String.valueOf(urlObj) : "";
            shouldNavigate = !url.contains("goofish.com/im");
        }
        if (shouldNavigate) {
            navigateToAccountPage(pageEndpoint, IM_PAGE_URL);
            Thread.sleep(2500);
        }
        return pageEndpoint;
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

        // 5. 消息风控不要直接打开 data.url 里的 punish 链接。
        // 实测该链接脱离 IM 页面上下文时会落到 /undefined 或错误页；正确入口是已登录的闲鱼消息页，
        // 由消息页自身触发 pc.login.token / WSS 初始化后渲染验证码，再在该页面定位滑块。
        try {
            navigateToAccountPage(cdpEndpoint, IM_PAGE_URL);
            Thread.sleep(5000); // 等待消息页加载、IM 初始化和验证码渲染
        } catch (Exception e) {
            return CaptchaResult.fail("导航到闲鱼消息页失败：" + e.getMessage());
        }

        String readyCookie = extractCookie(cdpEndpoint, IM_PAGE_URL);
        boolean sliderVisible = hasSlider(cdpEndpoint);
        if (isUsableImCookie(readyCookie) && !sliderVisible) {
            log.info("[CDP-AUTH] 闲鱼消息页已正常可用且无滑块，直接复用当前 IM cookie");
            return CaptchaResult.ok(readyCookie);
        }
        if (hasX5Cookie(readyCookie) && checkAlreadyPassed(cdpEndpoint)) {
            log.info("[CDP-AUTH] 闲鱼消息页验证已通过，已提取 x5sec cookie");
            return CaptchaResult.ok(readyCookie);
        }
        if (!sliderVisible) {
            log.warn("[CDP-AUTH] 闲鱼消息页未发现滑块元素，继续尝试触发/等待验证码渲染");
            triggerImCaptchaRender(cdpEndpoint);
            sliderVisible = waitForSlider(cdpEndpoint, 8000);
            if (!sliderVisible) {
                String cookie = extractCookie(cdpEndpoint, IM_PAGE_URL);
                if (isUsableImCookie(cookie)) {
                    log.info("[CDP-AUTH] 等待后仍无滑块，IM 页面可用，复用当前 IM cookie");
                    return CaptchaResult.ok(cookie);
                }
            }
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
            log.warn("[CDP-AUTH] 自动滑块失败，保留 CDP 页面等待人工完成验证，最长等待 {} 秒", config.getTimeoutSeconds());
            String manualCookie = waitForManualCaptchaAndCookie(cdpEndpoint, IM_PAGE_URL);
            if (hasX5Cookie(manualCookie)) {
                log.info("[CDP-AUTH] 人工验证完成，已提取 IM x5sec cookie");
                return CaptchaResult.ok(manualCookie);
            }
            return CaptchaResult.fail("滑块验证失败，已尝试 " + MAX_RETRY_ATTEMPTS + " 次，且等待人工验证超时");
        }

        // 7. 提取本次 punish 验证产生的 x5sec，存入 imCookieHeader
        try {
            Thread.sleep(1000); // 等待 cookie 落盘
            String cookie = extractCookie(cdpEndpoint, IM_PAGE_URL);
            if (hasX5Cookie(cookie)) {
                log.info("[CDP-AUTH] IM x5sec cookie 提取成功");
                return CaptchaResult.ok(cookie);
            } else {
                return CaptchaResult.fail("验证后未从 IM 页面提取到 x5sec cookie，验证可能未真正通过");
            }
        } catch (Exception e) {
            return CaptchaResult.fail("提取 cookie 异常：" + e.getMessage());
        }
    }

    /**
     * 判断提取到的 cookie 是否足以支撑 IM 链路（pc.login.token / WSS）。
     * CDP 实测：消息页即使已有 _m_h5_tk + 登录态 cookie，pc.login.token 仍会跳 punish；
     * 真正通过后会写入 x5sec，因此这里必须严格要求 x5sec，避免把未验证 cookie 当成成功。
     */
    private boolean isUsableImCookie(String cookie) {
        return hasX5Cookie(cookie);
    }

    private boolean hasX5Cookie(String cookie) {
        return cookie != null && cookie.toLowerCase().contains("x5sec");
    }

    /**
     * 自动拖动失败后，保留当前 CDP 页面等待人工完成滑块。
     * 人工通过后同一个页面会写入 x5sec；这里轮询提取并返回给消息同步链路。
     */
    private String waitForManualCaptchaAndCookie(String cdpEndpoint, String pageUrl) {
        long deadline = System.currentTimeMillis() + Math.max(30, config.getTimeoutSeconds()) * 1000L;
        int tick = 0;
        while (System.currentTimeMillis() < deadline) {
            try {
                String cookie = extractCookie(cdpEndpoint, pageUrl);
                if (isUsableImCookie(cookie)) {
                    return cookie;
                }
                if (checkAlreadyPassed(cdpEndpoint)) {
                    cookie = extractCookie(cdpEndpoint, pageUrl);
                    if (isUsableImCookie(cookie)) {
                        return cookie;
                    }
                }
                if (++tick % 10 == 0) {
                    log.info("[CDP-AUTH] 等待人工滑块中，请在 CDP 浏览器当前闲鱼消息页({})完成滑块...", pageUrl);
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.debug("[CDP-AUTH] 等待人工验证轮询异常: {}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            }
        }
        return null;
    }

    /**
     * 检查验证是否已经通过。
     * 消息页连接中断时 location 仍是 /im 且页面无滑块，但 pc.login.token 仍会被 punish；
     * 因此不能再用“已离开 punish 且无滑块”判定通过，必须看到 x5sec。
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
                return passedByCookie;
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

    private boolean hasSlider(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", "!!document.querySelector('#nc_1_n1z, .btn_slide, .nc_iconfont, #nocaptcha, #nc_1_wrapper, .nc-container')");
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            return Boolean.parseBoolean(String.valueOf(value));
        } catch (Exception e) {
            log.debug("[CDP-AUTH] hasSlider 异常: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void triggerImCaptchaRender(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (() => {
                    try { window.scrollTo(0, 0); } catch (e) {}
                    try { document.dispatchEvent(new Event('visibilitychange')); } catch (e) {}
                    try { window.dispatchEvent(new Event('focus')); } catch (e) {}
                    try {
                        const btn = Array.from(document.querySelectorAll('button, a, div, span'))
                            .find(e => /消息|聊天|重试|刷新|验证/.test(e.innerText || e.textContent || ''));
                        if (btn) btn.click();
                    } catch (e) {}
                    return JSON.stringify({href: location.href, title: document.title});
                })()
            """);
            script.put("awaitPromise", true);
            script.put("returnByValue", true);
            sendCommand(socket, "Runtime.evaluate", script);
        } catch (Exception e) {
            log.debug("[CDP-AUTH] 触发 IM 验证渲染异常: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private boolean waitForSlider(String cdpEndpoint, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMillis);
        while (System.currentTimeMillis() < deadline) {
            if (hasSlider(cdpEndpoint)) return true;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
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
            // 使用参考项目验证过的选择器：按钮 #nc_1_n1z；轨道 #nc_1_n1t 或 .nc_scale（容器比轨道宽，不能用外层容器）
            Map<String, Object> positionScript = new LinkedHashMap<>();
            positionScript.put("expression", """
                (() => {
                    const btn = document.querySelector('#nc_1_n1z, .btn_slide');
                    const track = document.querySelector('#nc_1_n1t, .nc_scale');
                    if (!btn || !track) return null;
                    const br = btn.getBoundingClientRect();
                    const tr = track.getBoundingClientRect();
                    return JSON.stringify({
                        startX: br.left + br.width / 2,
                        startY: br.top + br.height / 2,
                        btnWidth: br.width,
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
            double btnWidth = node.path("btnWidth").asDouble(40);
            double trackWidth = node.path("trackWidth").asDouble(300);

            // 不同版本 NoCaptcha 对“到达终点”的判定不完全一致：
            // 1) 按钮中心到轨道终点内侧：trackWidth - btnWidth
            // 2) 按钮中心到轨道右边界内侧：trackWidth - btnWidth / 2
            // 3) 鼠标释放点到轨道右边界：trackWidth
            // 真实测试固定 258px 失败，因此每次重试递进距离，避免 3 次重复同一错误距离。
            double baseDistance = trackWidth - btnWidth;
            double slideDistance;
            if (attemptIndex <= 1) {
                slideDistance = baseDistance;
            } else if (attemptIndex == 2) {
                slideDistance = trackWidth - btnWidth / 2.0;
            } else {
                slideDistance = trackWidth;
            }
            log.info("[CDP-AUTH] 滑块位置: ({}, {}), trackWidth={}px, btnWidth={}px, 本次距离={}px",
                    startX, startY, String.format("%.1f", trackWidth), String.format("%.1f", btnWidth), String.format("%.1f", slideDistance));

            // 2. 生成人类化轨迹：先小幅过冲，再回退到本次目标距离释放
            List<double[]> trajectory = generateHumanTrajectory(slideDistance, attemptIndex);

            // 3. 移动到滑块位置（带随机偏移，模拟真人移动）
            double approachX = startX + (RANDOM.nextDouble() * 20 - 10);
            double approachY = startY + (RANDOM.nextDouble() * 10 - 5);
            dispatchMouse(socket, "mouseMoved", approachX, approachY, "none", 0);
            Thread.sleep(100 + RANDOM.nextInt(200));

            // 4. 按下鼠标
            dispatchMouse(socket, "mousePressed", startX, startY, "left", 1);
            Thread.sleep(50 + RANDOM.nextInt(100));

            // 5. 按轨迹逐点移动
            // point[0] = 累计 X 位移（从起点算），point[1] = Y 抖动（相对起点），point[2] = 时间间隔
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

            double rawX = distance * Math.min(progress, 1.0);

            // 末端过冲（真人会拖过一点再回退）：overshoot 是像素，不是进度比例。
            // 旧逻辑把 2-5px 当成 2-5 倍 progress，导致鼠标被甩到轨道外几百/上千像素。
            if (t > 0.95) {
                rawX = distance + overshoot * (t - 0.95) / 0.05;
            }
            if (t > 0.98) {
                rawX = distance + overshoot * (1 - (t - 0.98) / 0.02);
            }
            accumulatedX = rawX;

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

            // point[0] = 累计绝对位移（从起点算起），point[1] = Y 增量，point[2] = 时间间隔
            trajectory.add(new double[]{rawX, dy, dt});
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
                    const candidates = Array.from(document.querySelectorAll('i, a, span, div, button'));
                    const el = candidates.find(e => {
                        const id = e.id || '';
                        const cls = e.className || '';
                        const text = e.innerText || e.textContent || '';
                        return id.includes('refresh')
                            || id.includes('nc_1_refresh')
                            || String(cls).includes('refresh')
                            || String(cls).includes('icon_warn')
                            || text.includes('刷新')
                            || text.includes('重试');
                    });
                    if (el) {
                        el.click();
                    }
                    // 点击失败框有时只触发一次短暂状态切换，直接重载 punish 页最稳定。
                    location.reload();
                    return !!el;
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
                .uri(URI.create(getCdpHttpEndpoint() + "/json/version"))
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
                .uri(URI.create(getCdpHttpEndpoint() + "/json/list"))
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

        // 不发送 Origin 头：当前 CDP 实例会拒绝 http://192.168.1.127:9333 这类 Origin，
        // 但允许无 Origin 的原始 WebSocket 握手（等价于 websocket-client suppress_origin=true）。
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
                .uri(URI.create(getCdpHttpEndpoint() + "/json/list"))
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
                .uri(URI.create(getCdpHttpEndpoint() + "/json/new?" + url))
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
            sendCommand(socket, "Page.enable", new LinkedHashMap<>());
            sendCommand(socket, "Network.enable", new LinkedHashMap<>());
            installAntiDetect(socket);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", url);
            sendCommand(socket, "Page.navigate", params);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void installAntiDetect(Socket socket) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("source", SliderAntiDetect.INIT_SCRIPT);
            sendCommand(socket, "Page.addScriptToEvaluateOnNewDocument", params);

            Map<String, Object> runtimeParams = new LinkedHashMap<>();
            runtimeParams.put("expression", SliderAntiDetect.INIT_SCRIPT);
            runtimeParams.put("awaitPromise", false);
            runtimeParams.put("returnByValue", true);
            sendCommand(socket, "Runtime.evaluate", runtimeParams);
            log.info("[CDP-AUTH] 已注入滑块反检测脚本");
        } catch (Exception e) {
            log.debug("[CDP-AUTH] 注入滑块反检测脚本失败: {}", e.getMessage());
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
     * 从浏览器全局 CookieJar 提取 cookie。
     * <p>滑块验证通过后页面可能跳到 taobao.com，此时 document.cookie 只能读当前域，
     * 读不到 h5api.m.goofish.com 写入的 x5sec；必须使用 CDP Network.getAllCookies。</p>
     */
    private String extractCookie(String cdpEndpoint, String url) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            sendCommand(socket, "Network.enable", new LinkedHashMap<>());
            Map<String, Object> result = sendCommand(socket, "Network.getAllCookies", new LinkedHashMap<>());
            Object cookiesObj = result.get("result");
            if (cookiesObj == null) return null;

            JsonNode root = MAPPER.valueToTree(cookiesObj);
            JsonNode cookies = root.path("cookies");
            if (!cookies.isArray()) return null;

            StringBuilder x5 = new StringBuilder();
            StringBuilder allRelated = new StringBuilder();
            for (JsonNode cookie : cookies) {
                String name = cookie.path("name").asText("");
                String value = cookie.path("value").asText("");
                String domain = cookie.path("domain").asText("");
                if (name.isEmpty()) continue;
                boolean relatedDomain = domain.contains("goofish.com")
                        || domain.contains("taobao.com")
                        || domain.contains("aliyun")
                        || domain.contains("alicdn");
                boolean isX5 = name.toLowerCase().startsWith("x5") || name.toLowerCase().contains("x5sec");
                if (isX5) {
                    if (x5.length() > 0) x5.append("; ");
                    x5.append(name).append("=").append(value);
                }
                if (relatedDomain) {
                    if (allRelated.length() > 0) allRelated.append("; ");
                    allRelated.append(name).append("=").append(value);
                }
            }
            if (x5.length() > 0) {
                // 返回完整消息域 cookie，而不是只返回 x5sec。
                // pc.login.token / WSS 真实请求依赖 goofish/taobao 消息页的一整组 cookie；
                // 这些 cookie 存入 imCookieHeader，与登录 cookie 分开管理，发送时再合并。
                if (allRelated.length() > 0) return allRelated.toString();
                return x5.toString();
            }
            return allRelated.length() > 0 ? allRelated.toString() : null;
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
