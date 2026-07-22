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

    /**
     * 上层按账号指定 CDP 端点（per-account Chrome 容器）。
     * <p>调用 {@link #solve(String, String)} 时传入 {@code http://127.0.0.1:<port>}，
     * 这里把它缓存到 {@link #effectiveCdpEndpoint}，后续 {@link #getCdpHttpEndpoint()}
     * 直接返回该值，避免落到全局单点 9222。</p>
     */
    public void setEffectiveCdpEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            this.effectiveCdpEndpoint = endpoint;
        }
    }

    private static class CdpFrameContext {
        final int contextId;
        final double offsetX;
        final double offsetY;

        CdpFrameContext(int contextId, double offsetX, double offsetY) {
            this.contextId = contextId;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

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
     * 尝试通过 CDP 自动完成滑块验证（使用全局配置的 CDP 端点）。
     *
     * @param punishUrl 闲鱼风控 punish URL
     * @return 验证码处理结果，包含新 cookie（x5sec 等，IM 专用）
     */
    public CaptchaResult solve(String punishUrl) {
        return solve(punishUrl, null, null, null);
    }

    /**
     * 尝试通过 CDP 自动完成滑块验证，可按账号指定 CDP 端点。
     * <p>当 {@code accountCdpEndpoint} 非空（如 {@code http://127.0.0.1:9333}）时，
     * 后续所有 CDP HTTP/WS 调用都走该端点，避免单点 9222 与每账号独占容器的设计冲突。</p>
     *
     * @param punishUrl          闲鱼风控 punish URL
     * @param accountCdpEndpoint 该账号 Chrome 容器的 CDP HTTP 端点；null 则回落全局配置
     * @return 验证码处理结果，包含新 cookie（x5sec 等，IM 专用）
     */
    public CaptchaResult solve(String punishUrl, String accountCdpEndpoint) {
        return solve(punishUrl, accountCdpEndpoint, null, null);
    }

    /**
     * 尝试通过 CDP 自动完成滑块验证，按账号注入登录 cookie + IM cookie 后再走滑块流程。
     *
     * <p>完整链路：
     * <ol>
     *   <li>把 {@code loginCookieHeader}（账号登录态）+ {@code imCookieHeader}（IM x5sec 等）
     *       通过 CDP {@code Network.setCookies} 注入到该账号独占的 Chrome 容器</li>
     *   <li>导航到 {@code https://www.goofish.com/im}，触发 {@code pc.login.token}</li>
     *   <li>若登录态有效 → 被 punish → 走滑块 → 拿到新 {@code x5sec}</li>
     *   <li>若注入后页面仍跳到登录页 → 返回 {@link CaptchaResult#loginExpired()}，
     *       上层应推送「网页端重新登录」通知</li>
     * </ol>
     * </p>
     *
     * @param punishUrl          闲鱼风控 punish URL
     * @param accountCdpEndpoint 该账号 Chrome 容器的 CDP HTTP 端点；null 则回落全局配置
     * @param loginCookieHeader  账号登录 cookie（cookie header 形式，如 {@code k1=v1; k2=v2}）
     * @param imCookieHeader     IM 滑块验证 cookie（x5sec 等），与登录 cookie 合并注入
     * @return 验证码处理结果；{@link CaptchaResult#isLoginExpired()} 为 true 时表示登录态失效
     */
    public CaptchaResult solve(String punishUrl, String accountCdpEndpoint,
                               String loginCookieHeader, String imCookieHeader) {
        if (accountCdpEndpoint != null && !accountCdpEndpoint.isBlank()) {
            // 切换到 per-account 端点，绕过全局单点 9222
            this.effectiveCdpEndpoint = accountCdpEndpoint;
        }
        log.info("[CDP-AUTH] 开始自动滑块验证, URL: {}, cdpEndpoint={}, hasLoginCookie={}, hasImCookie={}",
                truncate(punishUrl, 200),
                accountCdpEndpoint != null ? accountCdpEndpoint : "<global>",
                loginCookieHeader != null && !loginCookieHeader.isBlank(),
                imCookieHeader != null && !imCookieHeader.isBlank());

        // 1. 获取浏览器级 CDP WebSocket，用于 Target.* 命令
        String browserEndpoint;
        try {
            browserEndpoint = getCdpEndpointFromBrowser();
        } catch (Exception e) {
            return CaptchaResult.fail("无法连接 CDP 浏览器（" + getCdpHttpEndpoint() + "）：" + describeException(e));
        }

        // 2. 获取所有页面列表
        Map<String, Object> targets;
        try {
            targets = getTargets(browserEndpoint);
        } catch (Exception e) {
            return CaptchaResult.fail("获取 CDP 目标列表失败：" + e.getMessage());
        }

        // 3. 查找或复用/创建闲鱼标签页。启动时 Chrome 命令行带 about:blank 会建一个空白标签，
        // 优先复用这个空白标签去导航，避免新建后出现 2 个窗口。
        String targetId = findGoofishTargetId(targets);
        boolean reusedBlank = false;
        if (targetId == null) {
            String blankId = findBlankTargetId(targets);
            if (blankId != null) {
                targetId = blankId;
                reusedBlank = true;
                log.info("[CDP-AUTH] 复用已有空白标签页 id={}", targetId);
            } else {
                try {
                    targetId = createNewTarget(browserEndpoint, "about:blank");
                } catch (Exception e) {
                    return CaptchaResult.fail("创建 CDP 标签页失败：" + e.getMessage());
                }
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

        // 4a. 关闭多余空白标签页，避免出现「闲鱼窗口 + 空白窗口」两个窗口
        try {
            closeExtraBlankTargets(browserEndpoint, targetId, targets);
        } catch (Exception e) {
            log.warn("[CDP-AUTH] 关闭多余空白标签页异常: {}", e.getMessage());
        }

        // 5. 消息风控不要直接打开 data.url 里的 punish 链接。
        // 实测该链接脱离 IM 页面上下文时会落到 /undefined 或错误页；正确入口是已登录的闲鱼消息页，
        // 由消息页自身触发 pc.login.token / WSS 初始化后渲染验证码，再在该页面定位滑块。
        // 5a. 导航前先清理旧 x5sec，再把账号登录 cookie 注入 Chrome 容器。
        // 旧 imCookieHeader 里的 x5sec 可能已经失效；如果注入旧 x5sec，checkAlreadyPassed 会误判本次验证已通过，
        // 但重试 pc.login.token 仍会拿到新的 punish URL。
        try {
            clearRiskCookies(cdpEndpoint);
        } catch (Exception e) {
            log.warn("[CDP-AUTH] 清理旧风控 cookie 失败，继续尝试导航: {}", e.getMessage());
        }
        if (loginCookieHeader != null && !loginCookieHeader.isBlank()) {
            try {
                injectAccountCookies(cdpEndpoint, loginCookieHeader, imCookieHeader);
                log.info("[CDP-AUTH] 已注入账号登录 cookie 到 Chrome 容器, loginCookieLen={}, imCookieLen={}",
                        loginCookieHeader.length(),
                        imCookieHeader == null ? 0 : imCookieHeader.length());
            } catch (Exception e) {
                log.warn("[CDP-AUTH] 注入登录 cookie 失败，继续尝试导航: {}", e.getMessage());
            }
        }
        try {
            navigateToAccountPage(cdpEndpoint, IM_PAGE_URL);
            Thread.sleep(5000); // 等待消息页加载、IM 初始化和验证码渲染
        } catch (Exception e) {
            return CaptchaResult.fail("导航到闲鱼消息页失败：" + e.getMessage());
        }

        // 5a. 空白页处理：第一次打开 goofish 时页面可能是空白的（无 body 内容、无 iframe），
        // 必须刷新才能让页面正常渲染，触发 pc.login.token / 滑块。
        try {
            if (isBlankImPage(cdpEndpoint)) {
                log.warn("[CDP-AUTH] 第一次打开闲鱼消息页为空白页，刷新以触发渲染");
                reloadImPage(cdpEndpoint);
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            log.warn("[CDP-AUTH] 空白页检测/刷新异常: {}", e.getMessage());
        }

        // 5b. 登录态失效检测：注入 cookie 后若 IM 页仍跳到登录页，说明账号登录 cookie 已过期。
        // 此时不应该再走滑块（滑块过了也进不了消息页），直接返回 LOGIN_EXPIRED 让上层推送通知。
        if (isLoginPage(cdpEndpoint)) {
            log.warn("[CDP-AUTH] 注入登录 cookie 后 IM 页仍跳转到登录页，账号登录态已失效");
            return CaptchaResult.loginExpired();
        }

        String readyCookie = extractCookie(cdpEndpoint, IM_PAGE_URL);
        String baselineX5 = extractX5CookieValue(readyCookie);
        boolean sliderVisible = hasSlider(cdpEndpoint);
        if (isUsableImCookie(readyCookie) && !sliderVisible) {
            log.info("[CDP-AUTH] 闲鱼消息页已正常可用且无滑块，直接复用当前 IM cookie");
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
                if (isMainPageUsable(cdpEndpoint) && cookie != null && !cookie.isBlank()) {
                    log.info("[CDP-AUTH] 等待后仍无滑块，但消息列表已正常渲染，复用当前浏览器 cookie");
                    return CaptchaResult.ok(cookie);
                }
            }
        }

        // 6. 自动拖动滑块，最多重试 3 次。第一次迭代必须先 drag、后 check，
        // 否则刷新后 iframe 还没渲染时 checkAlreadyPassed 会误判"滑块已通过"。
        boolean success = false;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            log.info("[CDP-AUTH] 滑块验证第 {}/{} 次尝试", attempt, MAX_RETRY_ATTEMPTS);

            // 执行自动拖动（每次都先拖，拖完再检查）
            try {
                performAutoSliderDrag(cdpEndpoint, attempt);
            } catch (Exception e) {
                log.warn("[CDP-AUTH] 第 {} 次拖动执行异常: {}", attempt, e.getMessage());
            }

            // 等待验证结果渲染
            try {
                Thread.sleep(1500 + attempt * 500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // 检查是否通过（必须相对 baseline 新生成 x5sec 才算）
            try {
                if (checkAlreadyPassed(cdpEndpoint, baselineX5)) {
                    log.info("[CDP-AUTH] 第 {} 次拖动后验证通过", attempt);
                    success = true;
                    break;
                } else {
                    log.warn("[CDP-AUTH] 第 {} 次拖动后验证未通过", attempt);
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        // 递增延迟，给风控服务器冷却（参考项目 base 1.5s + 1.0s/次）
                        long cooldown = 1500L + attempt * 1000L;
                        log.info("[CDP-AUTH] 等待 {}ms 后刷新 punish 重试", cooldown);
                        Thread.sleep(cooldown);
                        // 失败后直接 Page.reload 刷新整个页面重新触发滑块渲染，
                        // 不要点「刷新按钮」——实测那个按钮点的是失败文案框本身，点完不会重新渲染滑块。
                        try {
                            reloadImPage(cdpEndpoint);
                            log.info("[CDP-AUTH] 已 Page.reload 刷新页面，等待滑块重新渲染");
                        } catch (Exception e) {
                            log.warn("[CDP-AUTH] Page.reload 失败: {}", e.getMessage());
                        }
                        // 刷新后滑块需要重新渲染，必须等滑块重新出现再进下一次拖动，
                        // 否则 performAutoSliderDrag 进来时滑块还没渲染就报「未找到滑块元素」。
                        boolean reRendered = waitForSlider(cdpEndpoint, 12000);
                        if (!reRendered) {
                            log.warn("[CDP-AUTH] 刷新后滑块未重新渲染，放弃本次重试");
                        }
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
            }
            if (isMainPageUsable(cdpEndpoint) && cookie != null && !cookie.isBlank()) {
                log.info("[CDP-AUTH] IM 页面已正常渲染但未产生 x5sec，复用当前浏览器 cookie");
                return CaptchaResult.ok(cookie);
            }
            return CaptchaResult.fail("验证后未从 IM 页面提取到 x5sec cookie，验证可能未真正通过");
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

    private String extractX5CookieValue(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) return null;
        for (String pair : cookieHeader.split(";")) {
            if (pair == null) continue;
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String name = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (isRiskCookieName(name)) {
                return value;
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
        return checkAlreadyPassed(cdpEndpoint, null);
    }

    /**
     * 检查验证是否已经通过，但必须是“本次新生成/更新”的 x5sec。
     */
    private boolean checkAlreadyPassed(String cdpEndpoint, String baselineX5) {
        // 真实 goofish 滑块通过后的页面状态（CDP 实测）：
        //   1) 主页面 bodyText 显示正常消息列表（订单/消息/交易记录）
        //   2) baxia iframe 仍存在但缩成 w=0,h=0（不可见）
        //   3) iframe 内 slider DOM 仍在但 sliderPos 也是 0
        //   4) iframe 无失败文案，也无通过文案
        //   5) 全局 CookieJar 写入新的 x5sec（域 h5api.m.goofish.com）
        // 判定优先级：a) 主页面消息列表可见 → 通过  b) 新 x5sec + iframe 缩成 0 → 通过  c) 失败文案 → 失败

        // a) 最强证据：主页面消息列表已正常渲染
        if (isMainPageUsable(cdpEndpoint)) {
            log.info("[CDP-AUTH] 主页面消息列表已正常渲染，判定滑块已通过");
            return true;
        }

        // b) 新 x5sec + iframe 缩成 0 尺寸
        String globalCookie = extractCookie(cdpEndpoint, IM_PAGE_URL);
        String currentX5 = extractX5CookieValue(globalCookie);
        boolean hasNewX5 = currentX5 != null && !currentX5.isBlank() && !currentX5.equals(baselineX5);
        boolean iframeCollapsed = isBaxiaIframeCollapsed(cdpEndpoint);
        if (hasNewX5 && iframeCollapsed) {
            log.info("[CDP-AUTH] 检测到新 x5sec 且 baxia iframe 已缩成 0 尺寸，判定本次滑块已通过");
            return true;
        }

        // c) 失败文案 → 失败
        if (isCaptchaFailureVisible(cdpEndpoint)) {
            log.warn("[CDP-AUTH] 检测到滑块失败文案，判定本次未通过");
            return false;
        }
        return false;
    }

    /**
     * 主页面消息列表是否已正常渲染（真实通过的最强证据）。
     * 通过态主页面 bodyText 会显示订单/消息/交易记录等内容。
     */
    private boolean isMainPageUsable(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            // 真实通过后主页面 bodyText 包含「订单/消息/交易/评价/通知」等消息列表关键词，
            // 且至少 50 字符（避免空白页误判）。
            script.put("expression", """
                (() => {
                    const text = (document.body && document.body.innerText || '').trim();
                    if (text.length < 50) return JSON.stringify({usable:false, len:text.length});
                    // 消息列表关键词：订单/消息/交易/评价/通知/确认收货/交易成功/交易关闭
                    const keywords = ['订单','消息','交易','评价','通知','确认收货','交易成功','交易关闭','售后','退款'];
                    const hits = keywords.filter(k => text.includes(k)).length;
                    return JSON.stringify({usable: hits >= 2, len:text.length, hits});
                })()
            """);
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            if (value != null) {
                JsonNode state = MAPPER.readTree(String.valueOf(value));
                return state.path("usable").asBoolean(false);
            }
        } catch (Exception e) {
            log.debug("[CDP-AUTH] isMainPageUsable 异常: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        return false;
    }

    /**
     * baxia iframe 是否已缩成 0 尺寸（通过态 iframe 不可见）。
     */
    private boolean isBaxiaIframeCollapsed(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (() => {
                    const f = document.querySelector('iframe#baxia-dialog-content, iframe[name="baxia-dialog-content"]');
                    if (!f) return JSON.stringify({collapsed:false, reason:'no-iframe'});
                    const r = f.getBoundingClientRect();
                    return JSON.stringify({collapsed: r.width === 0 && r.height === 0, w:r.width, h:r.height});
                })()
            """);
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            if (value != null) {
                JsonNode state = MAPPER.readTree(String.valueOf(value));
                return state.path("collapsed").asBoolean(false);
            }
        } catch (Exception e) {
            log.debug("[CDP-AUTH] isBaxiaIframeCollapsed 异常: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        return false;
    }

    private boolean isCaptchaFailureVisible(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            CdpFrameContext ctx = findCaptchaFrameContext(socket);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (() => {
                    const text = (document.body && document.body.innerText || '').replace(/\s+/g, ' ');
                    const scaleText = document.querySelector('#nc_1__scale_text, .nc-lang-cnt');
                    const clsText = Array.from(document.querySelectorAll('[class*=fail], [class*=error], [class*=err], .nc_iconfont, .nc_scale'))
                        .map(e => ((e.innerText || e.textContent || '') + ' ' + String(e.className || '')))
                        .join(' ');
                    return /验证失败|点击.*重试|请.*重试|失败|error|fail|errloading/.test(text + ' ' + clsText)
                        || (scaleText && /验证失败|重试|失败/.test(scaleText.innerText || scaleText.textContent || ''));
                })()
            """);
            if (ctx != null && ctx.contextId > 0) {
                script.put("contextId", ctx.contextId);
            }
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            return Boolean.parseBoolean(String.valueOf(value));
        } catch (Exception e) {
            log.debug("[CDP-AUTH] isCaptchaFailureVisible 异常: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private boolean hasSlider(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            CdpFrameContext ctx = findCaptchaFrameContext(socket);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", "!!document.querySelector('#nc_1_n1z, .btn_slide, .nc_iconfont, #nocaptcha, #nc_1_wrapper, .nc-container, .baxia-dialog')");
            if (ctx != null && ctx.contextId > 0) {
                script.put("contextId", ctx.contextId);
            }
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

    private boolean isBlankImPage(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", """
                (() => {
                    const bodyText = (document.body && document.body.innerText || '').trim();
                    const hasIframe = !!document.querySelector('iframe#baxia-dialog-content, iframe[name="baxia-dialog-content"]');
                    const hasSlider = !!document.querySelector('#nc_1_n1z, .btn_slide');
                    // 空白页判定：body 文本极少 + 无 iframe + 无滑块
                    return JSON.stringify({blank: bodyText.length < 20 && !hasIframe && !hasSlider, bodyLen: bodyText.length, hasIframe, hasSlider});
                })()
            """);
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            if (value != null) {
                JsonNode node = MAPPER.readTree(String.valueOf(value));
                return node.path("blank").asBoolean(false);
            }
        } catch (Exception e) {
            log.debug("[CDP-AUTH] isBlankImPage 异常: {}", e.getMessage());
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        return false;
    }

    private void reloadImPage(String cdpEndpoint) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("url", IM_PAGE_URL);
            sendCommand(socket, "Page.navigate", params);
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
            CdpFrameContext ctx = findCaptchaFrameContext(socket);

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
            if (ctx != null && ctx.contextId > 0) {
                positionScript.put("contextId", ctx.contextId);
            }
            positionScript.put("awaitPromise", true);
            positionScript.put("returnByValue", true);

            Map<String, Object> positionResult = sendCommand(socket, "Runtime.evaluate", positionScript);
            Object runtimeValue = extractRuntimeValue(positionResult);
            if (runtimeValue == null || "null".equals(String.valueOf(runtimeValue))) {
                throw new IllegalStateException("未找到滑块元素");
            }

            JsonNode node = MAPPER.readTree(String.valueOf(runtimeValue));
            double frameOffsetX = ctx != null ? ctx.offsetX : 0;
            double frameOffsetY = ctx != null ? ctx.offsetY : 0;
            double startX = node.path("startX").asDouble(100) + frameOffsetX;
            double startY = node.path("startY").asDouble(100) + frameOffsetY;
            double btnWidth = node.path("btnWidth").asDouble(40);
            double trackWidth = node.path("trackWidth").asDouble(300);

            // 滑块未渲染/不可见时坐标会全是 0，此时根本不该拖。
            // 真实通过态 iframe 会缩成 w=0,h=0，slider DOM 也在但 pos 全 0；
            // 失败态刷新中 iframe 也可能暂时 0。两种情况都不是有效拖动目标。
            if (startX <= 0 || startY <= 0 || btnWidth <= 0 || trackWidth <= 0) {
                throw new IllegalStateException("滑块未渲染或不可见（坐标/尺寸为 0），跳过本次拖动");
            }

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

            // 2. 接近阶段：带偏移地移动到滑块上方，模拟真人移动
            int approachSteps = 6 + RANDOM.nextInt(7); // 6-12
            double approachOffsetX = -30 + RANDOM.nextDouble() * 15; // -30 到 -15
            double approachOffsetY = 8 + RANDOM.nextDouble() * 14;   // 8-22
            double approachStartX = startX + approachOffsetX;
            double approachStartY = startY + approachOffsetY;
            dispatchMouse(socket, "mouseMoved", approachStartX, approachStartY, "none", 0);
            Thread.sleep(30 + RANDOM.nextInt(270)); // page_wait 50-300ms
            for (int s = 1; s <= approachSteps; s++) {
                double ax = approachOffsetX + (0 - approachOffsetX) * s / approachSteps + (RANDOM.nextDouble() - 0.5) * 4;
                double ay = approachOffsetY + (0 - approachOffsetY) * s / approachSteps + (RANDOM.nextDouble() - 0.5) * 3;
                dispatchMouse(socket, "mouseMoved", startX + ax, startY + ay, "none", 0);
                Thread.sleep(8 + RANDOM.nextInt(20));
            }

            // 3. 按下鼠标
            dispatchMouse(socket, "mousePressed", startX, startY, "left", 1);
            Thread.sleep(80 + RANDOM.nextInt(120)); // pre_down_pause 80-200ms
            Thread.sleep(80 + RANDOM.nextInt(120)); // post_down_pause 80-200ms

            // 4. 生成人类化轨迹：先小幅过冲，再回退到本次目标距离释放
            List<double[]> trajectory = generateHumanTrajectory(slideDistance, attemptIndex);

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

            // 6. 到达终点前的精微拖动（真人特征：最后在终点附近小幅度摆动）
            int precisionSteps = 6 + RANDOM.nextInt(7); // 6-12
            for (int s = 0; s < precisionSteps; s++) {
                double px = currentX + (RANDOM.nextDouble() - 0.5) * 2;
                double py = currentY + (RANDOM.nextDouble() - 0.5) * 2;
                dispatchMouse(socket, "mouseMoved", px, py, "left", 1);
                Thread.sleep(30 + RANDOM.nextInt(80));
            }

            // 7. 释放前短暂停顿（真人特征）
            Thread.sleep(20 + RANDOM.nextInt(60)); // pre_up_pause 20-80ms

            // 8. 释放鼠标
            dispatchMouse(socket, "mouseReleased", currentX, currentY, "left", 0);
            Thread.sleep(10 + RANDOM.nextInt(50)); // post_up_pause 10-60ms

            log.info("[CDP-AUTH] 滑块拖动完成，轨迹点数: {}, approachSteps: {}, precisionSteps: {}",
                    trajectory.size(), approachSteps, precisionSteps);

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
     * <p>实测验证（2026-07-21 模拟阿里 NoCaptcha 滑块）：原算法把 X 位移按 S 曲线平滑增长 +
     * 时间间隔随机化，导致每点速度（dx/dt）几乎相同，速度方差 ≈ 0.01，被判定为机器。</p>
     *
     * <p>真人特征：速度有明显的随机起伏 —— 「窜一下、停一下、再窜一下」。
     * 新算法保留 S 型宏观进度曲线作为骨架，但在每个点叠加速度扰动：
     * <ul>
     *   <li>每点位移不再是平滑 progress，而是叠加 ±15% 的局部扰动</li>
     *   <li>每点时间间隔大随机化：80% 正常 + 15% 停顿（dt×3）+ 5% 猛窜（dt×0.3）</li>
     *   <li>Y 轴抖动幅度提升到 ±3px，并加入 1-2 次「明显偏移」（真人手会偶尔偏离）</li>
     *   <li>末端过冲后回退（真人特征）</li>
     * </ul>
     * </p>
     *
     * @param distance 总拖动距离（像素）
     * @param attemptIndex 当前尝试次数（用于调整参数）
     * @return 轨迹点列表，每个点为 [dx, dy, dt]（相对位移x, 相对位移y, 时间间隔）
     */
    private List<double[]> generateHumanTrajectory(double distance, int attemptIndex) {
        List<double[]> trajectory = new ArrayList<>();

        // ===== 参考 xianyu-auto-reply-fix 黄金参数（基于成功案例分析） =====
        // 超调比例 2-15%、步数 18-35、基础延迟 4-15ms、Y 抖动 1-3.5
        double overshootRatio = 0.02 + RANDOM.nextDouble() * 0.13;
        int points = 18 + RANDOM.nextInt(18);                       // 18-35
        double baseDelay = 0.004 + RANDOM.nextDouble() * 0.011;     // 4-15ms
        double yJitter = 1.0 + RANDOM.nextDouble() * 2.5;           // 1-3.5
        double accelCurve = 1.3 + RANDOM.nextDouble() * 0.9;        // 1.3-2.2

        // 每次重试递增扰动幅度，避免 3 次轨迹高度相似
        double perturbation = 0.08 + attemptIndex * 0.12;
        baseDelay *= (1.0 + RANDOM.nextDouble() * 0.2 * perturbation);

        // 总耗时 0.8-2.0s
        double totalTime = 0.8 + RANDOM.nextDouble() * 1.2;

        // 停顿/猛窜点
        Set<Integer> pausePoints = new HashSet<>();
        pausePoints.add(8 + RANDOM.nextInt(Math.max(1, points / 4)));
        if (RANDOM.nextBoolean()) {
            pausePoints.add(points / 2 + RANDOM.nextInt(10));
        }
        Set<Integer> burstPoints = new HashSet<>();
        burstPoints.add(4 + RANDOM.nextInt(12));
        if (RANDOM.nextBoolean()) {
            burstPoints.add(points * 2 / 3 + RANDOM.nextInt(10));
        }

        double prevX = 0;
        double overshootPx = distance * overshootRatio;

        for (int i = 1; i <= points; i++) {
            double t = (double) i / points;

            // 位移曲线：带加速参数的 ease-out 曲线
            double progress = Math.pow(t, accelCurve);
            double rawX = distance * Math.min(progress, 1.0);

            // 末端过冲后回退
            if (t > 0.92) {
                double overT = (t - 0.92) / 0.08;
                rawX = distance + overshootPx * Math.sin(overT * Math.PI / 2);
            }
            if (t > 0.98) {
                rawX = distance + overshootPx * 0.3; // 回冲后保留少许过冲
            }

            // 步级扰动 ±15-30%，让相邻速度差异更大
            double stepX = rawX - prevX;
            double stepJitter = (RANDOM.nextDouble() - 0.5) * 0.3 * stepX * (1 + perturbation);
            double jitteredStep = Math.max(0, stepX + stepJitter);
            double jitteredX = prevX + jitteredStep;
            if (t > 0.92) {
                jitteredX = rawX;
            }
            prevX = jitteredX;

            // Y 轴抖动 + 偶尔明显偏移
            double dy = (RANDOM.nextDouble() - 0.5) * 2 * yJitter;
            if (t < 0.1) {
                dy = (RANDOM.nextDouble() - 0.5) * 0.8;
            }
            if (RANDOM.nextInt(points) < 2) {
                dy += (RANDOM.nextBoolean() ? 1 : -1) * (4 + RANDOM.nextDouble() * 4);
            }

            // 时间间隔：正常 / 停顿 / 猛窜三档
            double baseDt = totalTime / points;
            double dt;
            if (pausePoints.contains(i)) {
                dt = baseDt * (2.5 + RANDOM.nextDouble() * 1.5); // 停顿
            } else if (burstPoints.contains(i)) {
                dt = baseDt * (0.15 + RANDOM.nextDouble() * 0.25); // 猛窜
            } else {
                dt = baseDelay + RANDOM.nextDouble() * baseDelay * 0.8;
            }
            if (t > 0.7 && t < 0.92 && !pausePoints.contains(i) && !burstPoints.contains(i)) {
                dt *= 1.3;
            }

            trajectory.add(new double[]{jitteredX, dy, dt});
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
            CdpFrameContext ctx = findCaptchaFrameContext(socket);
            Map<String, Object> script = new LinkedHashMap<>();
            // 真实 goofish NoCaptcha：失败后的刷新图标是 #nc_1_refresh1 / .nc_iconfont.btn_refresh，
            // 不是失败文案本身。优先按 NoCaptcha 标准选择器定位刷新图标，再回退到文案模糊匹配。
            script.put("expression", """
                (() => {
                    const selectors = [
                        '#nc_1_refresh1', '.nc_iconfont.btn_refresh', '.btn_refresh',
                        '#nc_1_refresh', '.nc_1_refresh', '[id*=refresh]', '[class*=refresh]'
                    ];
                    for (const sel of selectors) {
                        const el = document.querySelector(sel);
                        if (el) {
                            el.click();
                            return {clicked: true, selector: sel, text: (el.innerText || el.textContent || '').trim().slice(0, 80)};
                        }
                    }
                    // 回退：含「刷新 / 重试」文案的可点击元素
                    const candidates = Array.from(document.querySelectorAll('i, a, span, div, button'));
                    const el = candidates.find(e => {
                        const cls = String(e.className || '');
                        const text = (e.innerText || e.textContent || '').trim();
                        return cls.includes('icon_warn') || cls.includes('nc_iconfont')
                            || text.includes('刷新') || text.includes('重试');
                    });
                    if (el) {
                        el.click();
                        return {clicked: true, selector: 'fallback', text: (el.innerText || '').trim().slice(0, 80)};
                    }
                    return {clicked: false};
                })()
            """);
            if (ctx != null && ctx.contextId > 0) {
                script.put("contextId", ctx.contextId);
            }
            script.put("awaitPromise", true);
            script.put("returnByValue", true);
            Object value = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", script));
            log.info("[CDP-AUTH] 点击刷新按钮结果: {}", value);
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
     * 查找任意一个空白页标签（url 为 about:blank 且不是闲鱼页）
     */
    private String findBlankTargetId(Map<String, Object> targets) {
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            Map<String, String> info = (Map<String, String>) entry.getValue();
            String url = info.getOrDefault("url", "");
            String type = info.getOrDefault("type", "");
            if ("page".equalsIgnoreCase(type) && "about:blank".equalsIgnoreCase(url)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 关闭多余的空白标签页（Chrome 启动时命令行带 about:blank 会建一个空白标签，
     * 导航到闲鱼页后要把这个空白标签关掉，否则会出现 2 个窗口）
     */
    private void closeExtraBlankTargets(String cdpEndpoint, String keepTargetId, Map<String, Object> targets) throws Exception {
        for (Map.Entry<String, Object> entry : targets.entrySet()) {
            String id = entry.getKey();
            if (id.equals(keepTargetId)) continue;
            Map<String, String> info = (Map<String, String>) entry.getValue();
            String type = info.getOrDefault("type", "");
            if (!"page".equalsIgnoreCase(type)) continue;
            String url = info.getOrDefault("url", "");
            // 只关 about:blank 空白页，避免误关其他业务标签
            if (!"about:blank".equalsIgnoreCase(url)) continue;
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(getCdpHttpEndpoint() + "/json/close/" + id))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("[CDP-AUTH] 已关闭多余空白标签页 id={}, status={}", id, response.statusCode());
            } catch (Exception e) {
                log.warn("[CDP-AUTH] 关闭空白标签页失败 id={}, 原因: {}", id, e.getMessage());
            }
        }
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

    private CdpFrameContext findCaptchaFrameContext(Socket socket) {
        try {
            Map<String, Object> frameTreeResult = sendCommand(socket, "Page.getFrameTree", new LinkedHashMap<>());
            JsonNode frameTree = MAPPER.valueToTree(frameTreeResult.get("result")).path("frameTree");
            String frameId = findCaptchaFrameId(frameTree);
            if (frameId == null || frameId.isBlank()) {
                return null;
            }

            Map<String, Object> offsetScript = new LinkedHashMap<>();
            offsetScript.put("expression", """
                (() => {
                    const iframe = document.querySelector('iframe#baxia-dialog-content, iframe[name="baxia-dialog-content"]');
                    if (!iframe) return JSON.stringify({x:0,y:0});
                    const rect = iframe.getBoundingClientRect();
                    return JSON.stringify({x: rect.left, y: rect.top});
                })()
            """);
            offsetScript.put("returnByValue", true);
            Object offsetValue = extractRuntimeValue(sendCommand(socket, "Runtime.evaluate", offsetScript));
            double offsetX = 0;
            double offsetY = 0;
            if (offsetValue != null) {
                JsonNode offset = MAPPER.readTree(String.valueOf(offsetValue));
                offsetX = offset.path("x").asDouble(0);
                offsetY = offset.path("y").asDouble(0);
            }

            Map<String, Object> worldParams = new LinkedHashMap<>();
            worldParams.put("frameId", frameId);
            worldParams.put("worldName", "xianyu-captcha");
            worldParams.put("grantUniversalAccess", true);
            Map<String, Object> worldResult = sendCommand(socket, "Page.createIsolatedWorld", worldParams);
            int contextId = MAPPER.valueToTree(worldResult.get("result")).path("executionContextId").asInt(0);
            if (contextId <= 0) {
                return null;
            }
            return new CdpFrameContext(contextId, offsetX, offsetY);
        } catch (Exception e) {
            log.debug("[CDP-AUTH] 查找验证码 iframe 上下文失败: {}", e.getMessage());
            return null;
        }
    }

    private String findCaptchaFrameId(JsonNode frameTree) {
        if (frameTree == null || frameTree.isMissingNode() || frameTree.isNull()) return null;
        JsonNode frame = frameTree.path("frame");
        String url = frame.path("url").asText("");
        String name = frame.path("name").asText("");
        if (url.contains("_____tmd_____/punish") || name.contains("baxia-dialog-content")) {
            return frame.path("id").asText("");
        }
        JsonNode children = frameTree.path("childFrames");
        if (children.isArray()) {
            for (JsonNode child : children) {
                String id = findCaptchaFrameId(child);
                if (id != null && !id.isBlank()) return id;
            }
        }
        return null;
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

            Map<String, String> x5Cookies = new LinkedHashMap<>();
            Map<String, String> allRelatedCookies = new LinkedHashMap<>();
            for (JsonNode cookie : cookies) {
                String name = cookie.path("name").asText("");
                String value = cookie.path("value").asText("");
                String domain = cookie.path("domain").asText("");
                if (name.isEmpty()) continue;
                boolean relatedDomain = domain.contains("goofish.com")
                        || domain.contains("taobao.com")
                        || domain.contains("aliyun")
                        || domain.contains("alicdn");
                boolean isX5 = isRiskCookieName(name);
                if (isX5) {
                    x5Cookies.put(name, value);
                }
                if (relatedDomain) {
                    // CDP 会返回同名 cookie 的多域副本。HTTP Cookie header 里重复 key 会污染后续 MTOP 请求，
                    // 这里按 name 去重，保留最后读取到的值。
                    allRelatedCookies.put(name, value);
                }
            }
            if (!x5Cookies.isEmpty()) {
                // 返回完整消息域 cookie，而不是只返回 x5sec。
                // pc.login.token / WSS 真实请求依赖 goofish/taobao 消息页的一整组 cookie；
                // 这些 cookie 存入 imCookieHeader，与登录 cookie 分开管理，发送时再合并。
                if (!allRelatedCookies.isEmpty()) return buildCookieHeader(allRelatedCookies);
                return buildCookieHeader(x5Cookies);
            }
            return !allRelatedCookies.isEmpty() ? buildCookieHeader(allRelatedCookies) : null;
        } catch (Exception e) {
            log.warn("[CDP-AUTH] 提取 cookie 失败: {}", e.getMessage());
            return null;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 通过 CDP {@code Network.setCookies} 把账号登录 cookie + IM cookie 注入到 Chrome 容器。
     * <p>关键用途：新启动的 Chrome 容器是空白 profile，没有闲鱼登录态；
     * 打开 {@code https://www.goofish.com/im} 会跳到登录页，{@code pc.login.token} 永远拿不到 token。
     * 注入登录 cookie 后网页版一开始就处于已登录状态，IM 页才能正常触发风控/滑块流程。</p>
     *
     * <p>实现：把 {@code loginCookieHeader} 与 {@code imCookieHeader}（cookie header 形式）
     * 解析成 CDP cookie 数组，对 goofish.com / taobao.com / aliyun.com 等相关域统一注入。</p>
     *
     * @param cdpEndpoint      页面级 CDP WebSocket 端点
     * @param loginCookieHeader 账号登录 cookie（如 {@code _m_h5_tk=xxx; cookie2=yyy}）
     * @param imCookieHeader   IM 滑块验证 cookie（x5sec 等），可空
     */
    private void injectAccountCookies(String cdpEndpoint, String loginCookieHeader, String imCookieHeader)
            throws Exception {
        // 合并登录 cookie + IM cookie，IM cookie 优先（含最新 x5sec）
        Map<String, String> merged = parseCookieHeader(loginCookieHeader);
        if (imCookieHeader != null && !imCookieHeader.isBlank()) {
            Map<String, String> imCookies = parseCookieHeader(imCookieHeader);
            imCookies.entrySet().removeIf(entry -> isRiskCookieName(entry.getKey()));
            merged.putAll(imCookies);
        }
        if (merged.isEmpty()) {
            log.warn("[CDP-AUTH] 注入 cookie 为空，跳过注入");
            return;
        }

        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            sendCommand(socket, "Network.enable", new LinkedHashMap<>());

            // 对所有相关域注入同一组 cookie，确保 goofish.com / taobao.com 等子域都能读到
            List<String> domains = List.of(
                    ".goofish.com", "goofish.com",
                    ".taobao.com", "taobao.com",
                    ".tmall.com", "tmall.com",
                    ".aliyun.com", "aliyun.com",
                    ".alicdn.com", "alicdn.com"
            );
            List<Map<String, Object>> cookieList = new ArrayList<>();
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (name == null || name.isBlank() || value == null) continue;
                for (String domain : domains) {
                    Map<String, Object> cookie = new LinkedHashMap<>();
                    cookie.put("name", name);
                    cookie.put("value", value);
                    cookie.put("domain", domain);
                    cookie.put("path", "/");
                    cookie.put("httpOnly", false);
                    cookie.put("secure", true);
                    cookie.put("sameSite", "None");
                    cookieList.add(cookie);
                }
            }

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("cookies", cookieList);
            sendCommand(socket, "Network.setCookies", params);
            log.info("[CDP-AUTH] 注入 {} 个 cookie（{} 个唯一键，跨 {} 个域）",
                    cookieList.size(), merged.size(), domains.size() / 2);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 解析 cookie header 字符串（{@code k1=v1; k2=v2}）为 name→value 映射。
     */
    private Map<String, String> parseCookieHeader(String cookieHeader) {
        Map<String, String> result = new LinkedHashMap<>();
        if (cookieHeader == null || cookieHeader.isBlank()) return result;
        for (String pair : cookieHeader.split(";")) {
            if (pair == null) continue;
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String name = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (!name.isEmpty()) result.put(name, value);
        }
        return result;
    }

    private String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 清理旧的风控 cookie，避免旧 x5sec 让本次验证误判为已通过。
     */
    private void clearRiskCookies(String cdpEndpoint) throws Exception {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            sendCommand(socket, "Network.enable", new LinkedHashMap<>());
            String[] names = {"x5sec", "x5sectag", "bx-cookie-test"};
            String[] domains = {
                    ".goofish.com", "goofish.com",
                    ".taobao.com", "taobao.com",
                    ".h5api.m.goofish.com", "h5api.m.goofish.com"
            };
            int deleted = 0;
            for (String name : names) {
                for (String domain : domains) {
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("name", name);
                    params.put("domain", domain);
                    params.put("path", "/");
                    sendCommand(socket, "Network.deleteCookies", params);
                    deleted++;
                }
            }
            log.info("[CDP-AUTH] 已清理旧风控 cookie, deleteCommands={}", deleted);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private boolean isRiskCookieName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("x5sec")
                || lower.equals("x5sectag")
                || lower.equals("bx-cookie-test")
                || lower.startsWith("x5");
    }

    /**
     * 检测当前 IM 页面是否跳到了闲鱼登录页（说明账号登录 cookie 已失效）。
     * <p>判定逻辑：通过 CDP {@code Runtime.evaluate} 读取 {@code location.href}，
     * 若包含 {@code login} / {@code passport} / {@code /login.htm} 等关键字则视为登录页。</p>
     *
     * @return true 表示当前页面是登录页，账号登录态已失效
     */
    private boolean isLoginPage(String cdpEndpoint) {
        Socket socket = null;
        try {
            socket = openWebSocket(cdpEndpoint);
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("expression", "location.href");
            script.put("returnByValue", true);
            Map<String, Object> result = sendCommand(socket, "Runtime.evaluate", script);
            Object value = extractRuntimeValue(result);
            if (value == null) return false;
            String href = String.valueOf(value).toLowerCase();
            log.info("[CDP-AUTH] 当前页面 location.href={}", truncate(href, 200));
            // 闲鱼/淘宝登录页 URL 特征
            return href.contains("login")
                    || href.contains("passport")
                    || href.contains("login.taobao.com")
                    || href.contains("login.m.taobao.com")
                    || href.contains("/login.htm")
                    || href.contains("qrlogin");
        } catch (Exception e) {
            log.warn("[CDP-AUTH] 检测登录页失败: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

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

    private static String describeException(Exception e) {
        if (e == null) return "未知异常";
        String message = e.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }
}
