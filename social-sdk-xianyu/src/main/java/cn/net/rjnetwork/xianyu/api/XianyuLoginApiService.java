package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 闲鱼登录 API 服务
 * 支持三种登录方式：
 * 1. 二维码登录（推荐）— 生成 QR Code，轮询等待扫码确认
 * 2. Cookie 登录 — 传入已获取的 Cookie 直接登录
 * 3. 登录态检测 — 验证当前会话是否有效
 *
 * <p>注意：二维码登录使用独立 HttpClient（不与 MTOP API 共享），
 * 因为登录流程涉及 passport.goofish.com 的多步 Cookie 传递。</p>
 */
public class XianyuLoginApiService {

    // ==================== 常量 ====================
    private static final String PASSPORT_BASE = "https://passport.goofish.com";
    private static final String MINI_LOGIN_URL = "https://passport.goofish.com/mini_login.htm";
    private static final String GENERATE_QR_URL = "https://passport.goofish.com/newlogin/qrcode/generate.do";
    private static final String QUERY_QR_URL = "https://passport.goofish.com/newlogin/qrcode/query.do";
    private static final String H5_TK_API = "https://h5api.m.goofish.com/h5/mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get/1.0/";
    private static final String APP_KEY = "34839810";
    private static final int QR_EXPIRE_SECONDS = 300;
    private static final int POLL_INTERVAL_MS = 3000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 字段 ====================
    private final HttpClient httpClient;
    private final String mtCookie;
    private final ConcurrentMap<String, InternalQrSession> qrSessions = new ConcurrentHashMap<>();

    public XianyuLoginApiService(String cookie) {
        this.mtCookie = cookie;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();
    }

    // ==================== 二维码登录 ====================

    /**
     * 创建二维码登录会话
     *
     * @return QR 登录会话信息（包含 base64 二维码图片）
     */
    public QrLoginResult createQrLoginSession() {
        try {
            System.err.println("[SDK] createQrLoginSession START");
            InternalQrSession session = new InternalQrSession();
            session.sessionId = UUID.randomUUID().toString();
            session.status = "WAITING";

            // 第0步：先访问 passport.goofish.com 首页建立基础会话
            System.err.println("[SDK] Step 0: Visiting passport homepage to establish session...");
            visitPassportHomepage(session);

            // 第1步：预热 _m_h5_tk Cookie
            fetchMh5Tk(session);

            // 第2步：获取登录表单参数
            Map<String, String> loginParams = fetchLoginFormData(session);
            session.params.putAll(loginParams);

            // 第3步：生成二维码
            generateQrCode(session);

            // 二维码生成成功后，再设置 createdAt
            session.createdAt = Instant.now();
            System.err.println("[SDK] QR code generated, createdAt=" + session.createdAt);

            qrSessions.put(session.sessionId, session);
            System.err.println("[SDK] Session stored. Total sessions: " + qrSessions.size());
            return toPublicResult(session);
        } catch (Exception e) {
            System.err.println("[SDK] createQrLoginSession FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            QrLoginResult error = new QrLoginResult();
            error.success = false;
            error.message = "Failed to create QR login session: " + e.getMessage();
            return error;
        }
    }

    /**
     * 访问 passport 首页建立基础 Cookie 会话
     */
    private void visitPassportHomepage(InternalQrSession session) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PASSPORT_BASE + "/"))
                .GET()
                .header("User-Agent", userAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);
        System.err.println("[SDK] visitPassportHomepage - status: " + response.statusCode() + ", cookies: " + session.cookies);
    }

    /**
     * 轮询二维码状态
     *
     * @param sessionId 会话 ID
     * @return 二维码状态结果
     */
    public QrLoginResult pollQrStatus(String sessionId) {
        System.err.println("[SDK] pollQrStatus called, sessionId=" + sessionId);
        InternalQrSession session = qrSessions.get(sessionId);
        if (session == null) {
            System.err.println("[SDK] Session NOT FOUND in qrSessions map. Available keys: " + qrSessions.keySet());
            QrLoginResult result = new QrLoginResult();
            result.success = false;
            result.status = "NOT_FOUND";
            result.message = "QR login session not found";
            return result;
        }
        System.err.println("[SDK] Session found, current status=" + session.status + ", t=" + session.t + ", ck=" + session.ck);

        // 检查过期
        if (isExpired(session) && !"SUCCESS".equals(session.status)
                && !"CANCELLED".equals(session.status) && !"EXPIRED".equals(session.status)) {
            session.status = "EXPIRED";
            return toPublicResult(session);
        }

        // 轮询状态
        try {
            pollQrStatusInternal(session);
        } catch (Exception e) {
            session.status = "ERROR";
            session.message = "Poll failed: " + e.getMessage();
        }

        return toPublicResult(session);
    }

    /**
     * 等待二维码登录完成（阻塞直到扫码成功/过期/取消）
     *
     * @param sessionId 会话 ID
     * @param timeoutSeconds 超时秒数
     * @return 登录结果（包含 Cookie）
     */
    public QrLoginResult waitForLogin(String sessionId, long timeoutSeconds) {
        InternalQrSession session = qrSessions.get(sessionId);
        if (session == null) {
            QrLoginResult result = new QrLoginResult();
            result.success = false;
            result.message = "Session not found";
            return result;
        }

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            QrLoginResult status = pollQrStatus(sessionId);

            if ("SUCCESS".equals(status.status)) {
                return status;
            }
            if ("EXPIRED".equals(status.status) || "CANCELLED".equals(status.status)
                    || "ERROR".equals(status.status) || "VERIFICATION_REQUIRED".equals(status.status)) {
                return status;
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        session.status = "EXPIRED";
        QrLoginResult result = toPublicResult(session);
        result.message = "Login timed out after " + timeoutSeconds + " seconds";
        return result;
    }

    /**
     * 取消二维码登录会话
     */
    public boolean cancelQrLogin(String sessionId) {
        InternalQrSession session = qrSessions.get(sessionId);
        if (session != null) {
            session.status = "CANCELLED";
            qrSessions.remove(sessionId);
            return true;
        }
        return false;
    }

    /**
     * 清理过期的二维码会话
     */
    public void cleanupExpiredSessions() {
        Iterator<Map.Entry<String, InternalQrSession>> it = qrSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, InternalQrSession> entry = it.next();
            if (isExpired(entry.getValue())) {
                entry.getValue().status = "EXPIRED";
                it.remove();
            }
        }
    }

    // ==================== Cookie 登录 ====================

    /**
     * Cookie 登录 — 将传入的 Cookie 设置到 MTOP API 客户端
     *
     * @param cookieHeader Cookie 字符串
     * @return 登录结果
     */
    public LoginResult cookieLogin(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            LoginResult result = new LoginResult();
            result.success = false;
            result.message = "Cookie cannot be empty";
            return result;
        }

        LoginResult result = new LoginResult();
        result.success = true;
        result.cookieHeader = cookieHeader;
        result.message = "Cookie login successful";

        // 立即验证登录态
        boolean verified = verifyLoginWithCookie(cookieHeader);
        if (!verified) {
            result.message = "Cookie login succeeded but login verification failed";
        }

        return result;
    }

    /**
     * 用指定 Cookie 验证登录态
     */
    public boolean verifyLoginWithCookie(String cookieHeader) {
        try {
            // 调用 MTOP API 获取用户信息来验证
            String url = "https://h5api.m.goofish.com/h5/mtop.alibaba.xianyu.user.userInfo.get/1.0/";
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", APP_KEY);
            params.put("t", String.valueOf(System.currentTimeMillis()));
            params.put("v", "1.0");
            params.put("type", "originaljson");
            params.put("dataType", "json");

            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "?" + buildQuery(params)))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Cookie", cookieHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(response.body());

            // 检查返回是否有错误码（mtop.permission.login-error 表示未登录）
            JsonNode errorCode = root.path("errorCode");
            if (errorCode.isTextual() && errorCode.asText().contains("login")) {
                return false;
            }

            // 如果能拿到用户信息，说明登录成功
            return root.has("data") && !root.path("data").isNull();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 登录态检测 ====================

    /**
     * 检测当前会话是否已登录
     *
     * @return 登录检测结果
     */
    public LoginStatusResult checkLoginStatus() {
        return checkLoginStatus(mtCookie);
    }

    /**
     * 检测指定 Cookie 的登录状态
     */
    public LoginStatusResult checkLoginStatus(String cookieHeader) {
        LoginStatusResult result = new LoginStatusResult();

        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            result.loggedIn = false;
            result.message = "Empty cookie";
            return result;
        }

        try {
            // 方法1：调用用户信息接口
            String url = "https://h5api.m.goofish.com/h5/mtop.alibaba.xianyu.user.userInfo.get/1.0/";
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", APP_KEY);
            params.put("t", String.valueOf(System.currentTimeMillis()));
            params.put("v", "1.0");
            params.put("type", "originaljson");
            params.put("dataType", "json");

            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "?" + buildQuery(params)))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Cookie", cookieHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(response.body());

            JsonNode errorCode = root.path("errorCode");
            if (errorCode.isTextual() && errorCode.asText().contains("login")) {
                result.loggedIn = false;
                result.message = "Not logged in (login error detected)";
                return result;
            }

            JsonNode data = root.path("data");
            if (data.isObject()) {
                result.loggedIn = true;
                result.userId = data.path("userId").asText("");
                String nick = data.path("nickName").asText("");
                if (nick.isEmpty()) {
                    nick = data.path("nickname").asText("");
                }
                result.nickname = nick;
                result.avatar = data.path("avatar").asText("");
                result.message = "Logged in";
                result.rawData = root;
            } else {
                result.loggedIn = false;
                result.message = "Login status unknown";
            }
        } catch (Exception e) {
            result.loggedIn = false;
            result.message = "Check failed: " + e.getMessage();
        }

        return result;
    }

    // ==================== 私有方法 ====================

    private void fetchMh5Tk(InternalQrSession session) throws Exception {
        String dataJson = "{\"bizScene\":\"home\"}";
        primeMh5TokenCookies(session, dataJson);

        String tokenCookie = resolveMh5TokenCookie(session.cookies);
        if (tokenCookie == null || tokenCookie.trim().isEmpty()) {
            throw new RuntimeException("Missing _m_h5_tk cookie after warmup");
        }

        String token = extractTokenFromCookie(tokenCookie);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5Hex(token + "&" + timestamp + "&" + APP_KEY + "&" + dataJson);

        Map<String, String> params = buildMtopParams(dataJson, APP_KEY, timestamp, sign);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(H5_TK_API + "?" + buildQuery(params)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", userAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", PASSPORT_BASE)
                .header("Referer", PASSPORT_BASE + "/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);
    }

    private void primeMh5TokenCookies(InternalQrSession session, String dataJson) throws Exception {
        String warmupTs = String.valueOf(System.currentTimeMillis());
        String warmupSign = md5Hex("&" + warmupTs + "&" + APP_KEY + "&" + dataJson);
        Map<String, String> warmupParams = buildMtopParams(dataJson, APP_KEY, warmupTs, warmupSign);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(H5_TK_API + "?" + buildQuery(warmupParams)))
                .GET()
                .header("User-Agent", userAgent())
                .header("Origin", PASSPORT_BASE)
                .header("Referer", PASSPORT_BASE + "/")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        if (resolveMh5TokenCookie(session.cookies) != null) {
            return;
        }

        // 备用方案
        HttpRequest legacyRequest = HttpRequest.newBuilder(URI.create(H5_TK_API))
                .GET()
                .header("User-Agent", userAgent())
                .header("Origin", PASSPORT_BASE)
                .header("Referer", PASSPORT_BASE + "/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();

        HttpResponse<String> legacyResponse = httpClient.send(legacyRequest, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, legacyResponse);
    }

    private Map<String, String> fetchLoginFormData(InternalQrSession session) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("lang", "zh_cn");
        params.put("appName", "xianyu");
        params.put("appEntrance", "web");
        params.put("styleType", "vertical");
        params.put("bizParams", "");
        params.put("notLoadSsoView", "false");
        params.put("notKeepLogin", "false");
        params.put("isMobile", "false");
        params.put("qrCodeFirst", "false");
        params.put("stie", "77");
        params.put("rnd", String.valueOf(Math.random()));

        String loginUrl = MINI_LOGIN_URL + "?" + buildQuery(params);
        System.err.println("[SDK] fetchLoginFormData - URL: " + loginUrl);
        System.err.println("[SDK] fetchLoginFormData - cookies before: " + session.cookies);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(loginUrl))
                .GET()
                .header("User-Agent", userAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9")
                .header("Origin", PASSPORT_BASE)
                .header("Referer", PASSPORT_BASE + "/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        System.err.println("[SDK] fetchLoginFormData - response status: " + response.statusCode());
        System.err.println("[SDK] fetchLoginFormData - response body length: " + response.body().length());
        System.err.println("[SDK] fetchLoginFormData - cookies after: " + session.cookies);

        String viewDataJson = extractViewDataJson(response.body());
        if (viewDataJson == null) {
            throw new RuntimeException("Parse mini_login viewData failed");
        }

        System.err.println("[SDK] fetchLoginFormData - viewDataJson: " + viewDataJson);

        JsonNode root = MAPPER.readTree(viewDataJson);
        JsonNode loginFormData = root.path("loginFormData");
        if (!loginFormData.isObject()) {
            throw new RuntimeException("Missing loginFormData in viewData");
        }

        Map<String, Object> raw = MAPPER.convertValue(loginFormData,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, v.toString());
            }
        });
        out.put("umidTag", "SERVER");
        
        System.err.println("[SDK] fetchLoginFormData - extracted loginFormData params: " + out);
        return out;
    }

    private void generateQrCode(InternalQrSession session) throws Exception {
        Map<String, String> allParams = new LinkedHashMap<>(session.params);
        allParams.put("lang", "zh_cn");
        allParams.put("appName", "xianyu");
        allParams.put("appEntrance", "web");

        String queryString = buildQuery(allParams);
        System.err.println("[SDK] generateQrCode - URL: " + GENERATE_QR_URL);
        System.err.println("[SDK] generateQrCode - params: " + queryString);
        System.err.println("[SDK] generateQrCode - cookies: " + session.cookies);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(GENERATE_QR_URL + "?" + queryString))
                .GET()
                .header("User-Agent", userAgent())
                .header("Origin", PASSPORT_BASE)
                .header("Referer", PASSPORT_BASE + "/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        System.err.println("[SDK] generateQrCode - response status: " + response.statusCode());
        System.err.println("[SDK] generateQrCode - response body: " + response.body());

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode content = root.path("content");
        if (!content.path("success").asBoolean(false)) {
            throw new RuntimeException("Generate QR failed: " + response.body());
        }

        JsonNode data = content.path("data");
        session.t = data.path("t").asText(null);
        session.ck = data.path("ck").asText(null);
        session.qrContent = data.path("codeContent").asText(null);

        // 提取 lgToken（从 codeContent URL 中）
        String lgToken = null;
        if (session.qrContent != null && session.qrContent.contains("lgToken=")) {
            int start = session.qrContent.indexOf("lgToken=") + 8;
            int end = session.qrContent.indexOf("&", start);
            if (end > 0) {
                lgToken = session.qrContent.substring(start, end);
            } else {
                lgToken = session.qrContent.substring(start);
            }
        }
        session.lgToken = lgToken;
        System.err.println("[SDK] Extracted lgToken: " + lgToken);

        if (session.qrContent == null) {
            throw new RuntimeException("QR codeContent is null");
        }

        // 生成 base64 二维码图片
        session.qrCodeDataUrl = generateBase64QrImage(session.qrContent);
    }

    private void pollQrStatusInternal(InternalQrSession session) throws Exception {
        // 轮询二维码状态需要 t、ck 和 lgToken 三个关键参数
        Map<String, String> pollParams = new LinkedHashMap<>();
        if (session.t != null) pollParams.put("t", session.t);
        if (session.ck != null) pollParams.put("ck", session.ck);
        if (session.lgToken != null) pollParams.put("lgToken", session.lgToken);

        String pollQueryString = buildQuery(pollParams);
        System.err.println("[SDK] pollQrStatusInternal - body params: " + pollQueryString);

        // 关键：轮询 URL 必须带 appName 和 fromSite query 参数（与浏览器行为一致）
        String pollUrl = QUERY_QR_URL + "?appName=xianyu&fromSite=77";
        System.err.println("[SDK] pollQrStatusInternal - URL: " + pollUrl);

        HttpRequest request = HttpRequest.newBuilder(URI.create(pollUrl))
                .POST(HttpRequest.BodyPublishers.ofString(pollQueryString))
                .header("User-Agent", userAgent())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", PASSPORT_BASE)
                .header("Referer", PASSPORT_BASE + "/mini_login.htm")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        // 调试：打印完整响应
        System.err.println("[QR POLL] Status=" + response.statusCode() + " Body=" + response.body());

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode data = root.path("content").path("data");
        String qrStatus = data.path("qrCodeStatus").asText(null);

        if (qrStatus == null) {
            session.status = "ERROR";
            session.message = "Missing qrCodeStatus";
            return;
        }

        switch (qrStatus) {
            case "NEW":
                session.status = "WAITING";
                break;
            case "SCANED":
                session.status = "SCANNED";
                break;
            case "EXPIRED":
                session.status = "EXPIRED";
                break;
            case "CONFIRMED":
                boolean iframeRedirect = data.path("iframeRedirect").asBoolean(false);
                if (iframeRedirect) {
                    session.status = "VERIFICATION_REQUIRED";
                    session.verificationUrl = data.path("iframeRedirectUrl").asText(null);
                } else {
                    session.status = "SUCCESS";
                    session.unb = session.cookies.get("unb");
                    session.cookieHeader = buildCookieHeader(session.cookies);
                }
                break;
            default:
                session.status = "CANCELLED";
                break;
        }
    }

    // ==================== 工具方法 ====================

    private boolean isExpired(InternalQrSession session) {
        return session == null || session.createdAt == null
                || Instant.now().isAfter(session.createdAt.plusSeconds(QR_EXPIRE_SECONDS));
    }

    private void mergeSetCookies(Map<String, String> target, HttpResponse<?> response) {
        if (target == null || response == null) return;
        try {
            for (String setCookie : response.headers().allValues("set-cookie")) {
                if (setCookie == null || setCookie.trim().isEmpty()) continue;
                int semi = setCookie.indexOf(';');
                String pair = semi > 0 ? setCookie.substring(0, semi) : setCookie;
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String key = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                if (!key.isEmpty() && value != null) {
                    target.put(key, value);
                }
            }
        } catch (Exception ignore) {}
    }

    private String resolveMh5TokenCookie(Map<String, String> cookies) {
        if (cookies == null) return null;
        return firstNonBlank(cookies.get("m_h5_tk"), cookies.get("_m_h5_tk"));
    }

    private String extractTokenFromCookie(String tokenCookie) {
        if (tokenCookie == null) return null;
        int idx = tokenCookie.indexOf('_');
        return idx > 0 ? tokenCookie.substring(0, idx) : tokenCookie;
    }

    private String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (e.getKey() != null && !e.getKey().isEmpty() && e.getValue() != null) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
        }
        return sb.toString();
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append("=").append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private Map<String, String> buildMtopParams(String dataJson, String appKey, String timestamp, String sign) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", appKey);
        params.put("t", timestamp);
        params.put("sign", sign);
        params.put("v", "1.0");
        params.put("type", "originaljson");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", "mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get");
        params.put("data", dataJson);
        return params;
    }

    private String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 failed", e);
        }
    }

    private String extractViewDataJson(String html) {
        if (html == null) return null;
        int markerIdx = html.indexOf("window.viewData");
        if (markerIdx < 0) return null;
        int eqIdx = html.indexOf("=", markerIdx);
        if (eqIdx < 0) return null;
        int startBrace = html.indexOf('{', eqIdx);
        if (startBrace < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = startBrace; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escape) { escape = false; }
                else if (c == '\\') { escape = true; }
                else if (c == '"') { inString = false; }
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') { depth++; continue; }
            if (c == '}') {
                depth--;
                if (depth == 0) return html.substring(startBrace, i + 1);
            }
        }
        return null;
    }

    private String generateBase64QrImage(String content) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300, hints);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

    private String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    }

    private QrLoginResult toPublicResult(InternalQrSession src) {
        QrLoginResult dst = new QrLoginResult();
        dst.success = "SUCCESS".equals(src.status);
        dst.sessionId = src.sessionId;
        dst.status = src.status;
        dst.qrContent = src.qrContent;
        dst.qrCodeDataUrl = src.qrCodeDataUrl;
        dst.cookieHeader = src.cookieHeader;
        dst.unb = src.unb;
        dst.verificationUrl = src.verificationUrl;
        dst.message = src.message;
        dst.createdAt = src.createdAt != null ? src.createdAt.toEpochMilli() : null;
        return dst;
    }

    // ==================== 内部会话 ====================

    private static class InternalQrSession {
        String sessionId;
        String status = "WAITING";
        String qrContent;
        String qrCodeDataUrl;
        String verificationUrl;
        String cookieHeader;
        String unb;
        String t;
        String ck;
        String lgToken;
        String message;
        Instant createdAt;
        final Map<String, String> cookies = new LinkedHashMap<>();
        final Map<String, String> params = new LinkedHashMap<>();
    }

    // ==================== 公开结果 ====================

    /**
     * 二维码登录结果
     */
    public static class QrLoginResult {
        public boolean success;
        public String sessionId;
        public String status;       // WAITING / SCANNED / SUCCESS / EXPIRED / CANCELLED / ERROR / VERIFICATION_REQUIRED
        public String qrContent;    // 二维码原始内容
        public String qrCodeDataUrl; // base64 PNG 二维码图片
        public String cookieHeader;  // 登录成功后 Cookie
        public String unb;           // 登录成功后 unb cookie
        public String verificationUrl; // 需要验证时的跳转 URL
        public String message;
        public Long createdAt;

        public QrLoginResult() {}
    }

    /**
     * 登录检测结果
     */
    public static class LoginStatusResult {
        public boolean loggedIn;
        public String userId;
        public String nickname;
        public String avatar;
        public String message;
        public JsonNode rawData;

        public LoginStatusResult() {}
    }

    /**
     * Cookie 登录结果
     */
    public static class LoginResult {
        public boolean success;
        public String cookieHeader;
        public String message;

        public LoginResult() {}
    }
}


