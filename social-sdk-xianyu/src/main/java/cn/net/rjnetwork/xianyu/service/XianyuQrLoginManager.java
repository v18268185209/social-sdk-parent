package cn.net.rjnetwork.xianyu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import cn.net.rjnetwork.xianyu.model.XianyuQrLoginSession;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 闲鱼二维码登录管理器（基于 passport.goofish.com）
 */
public class XianyuQrLoginManager {

    private static final String MINI_LOGIN_URL = "https://passport.goofish.com/mini_login.htm";
    private static final String GENERATE_QR_URL = "https://passport.goofish.com/newlogin/qrcode/generate.do";
    private static final String QUERY_QR_URL = "https://passport.goofish.com/newlogin/qrcode/query.do";
    private static final String H5_TK_API = "https://h5api.m.goofish.com/h5/mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get/1.0/";

    private static final int EXPIRE_SECONDS = 300;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, InternalQrSession> sessions = new ConcurrentHashMap<>();

    public XianyuQrLoginManager() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public XianyuQrLoginSession createSession() throws IOException, InterruptedException {
        cleanupExpiredSessions();

        InternalQrSession session = new InternalQrSession();
        session.sessionId = UUID.randomUUID().toString();
        session.createdAt = Instant.now();
        session.status = XianyuQrLoginSession.STATUS_WAITING;

        fetchMh5Tk(session);
        Map<String, String> loginParams = fetchLoginFormData(session);
        session.params.putAll(loginParams);

        QrPayload payload = fetchQrPayload(session);
        session.params.put("t", payload.t);
        session.params.put("ck", payload.ck);
        session.qrContent = payload.codeContent;
        session.qrCodeDataUrl = buildQrDataUrl(payload.codeContent);

        sessions.put(session.sessionId, session);
        return toPublicSession(session);
    }

    public XianyuQrLoginSession getSessionStatus(String sessionId) {
        InternalQrSession session = sessions.get(sessionId);
        if (session == null) {
            XianyuQrLoginSession result = new XianyuQrLoginSession();
            result.setSessionId(sessionId);
            result.setStatus(XianyuQrLoginSession.STATUS_NOT_FOUND);
            result.setMessage("qr login session not found");
            return result;
        }

        if (isExpired(session) && !XianyuQrLoginSession.STATUS_SUCCESS.equals(session.status)) {
            session.status = XianyuQrLoginSession.STATUS_EXPIRED;
            return toPublicSession(session);
        }

        if (XianyuQrLoginSession.STATUS_SUCCESS.equals(session.status)
                || XianyuQrLoginSession.STATUS_EXPIRED.equals(session.status)
                || XianyuQrLoginSession.STATUS_CANCELLED.equals(session.status)
                || XianyuQrLoginSession.STATUS_VERIFICATION_REQUIRED.equals(session.status)) {
            return toPublicSession(session);
        }

        try {
            pollStatus(session);
        } catch (Exception e) {
            session.status = XianyuQrLoginSession.STATUS_ERROR;
            session.message = "poll qr status failed: " + e.getMessage();
        }
        return toPublicSession(session);
    }

    public String resolveCookieHeader(String sessionId) {
        XianyuQrLoginSession session = getSessionStatus(sessionId);
        if (session == null || !XianyuQrLoginSession.STATUS_SUCCESS.equals(session.getStatus())) {
            return null;
        }
        return session.getCookieHeader();
    }

    public boolean invalidateSession(String sessionId) {
        return sessions.remove(sessionId) != null;
    }

    public void cleanupExpiredSessions() {
        for (Map.Entry<String, InternalQrSession> entry : sessions.entrySet()) {
            if (isExpired(entry.getValue())) {
                sessions.remove(entry.getKey());
            }
        }
    }

    private void fetchMh5Tk(InternalQrSession session) throws IOException, InterruptedException {
        String dataJson = "{\"bizScene\":\"home\"}";
        String appKey = "34839810";
        primeMh5TokenCookies(session, dataJson, appKey);

        String tokenCookie = resolveMh5TokenCookie(session.cookies);
        String token = extractCookieToken(tokenCookie);
        if (isBlank(token)) {
            throw new IOException("qr login warmup missing _m_h5_tk token");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5Hex(token + "&" + timestamp + "&" + appKey + "&" + dataJson);
        Map<String, String> params = buildMtopParams(dataJson, appKey, timestamp, sign);

        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(H5_TK_API + "?" + buildQuery(params)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", defaultUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://passport.goofish.com")
                .header("Referer", "https://passport.goofish.com/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();

        HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, tokenResponse);
    }

    private void primeMh5TokenCookies(InternalQrSession session, String dataJson, String appKey)
            throws IOException, InterruptedException {
        String warmupTimestamp = String.valueOf(System.currentTimeMillis());
        String warmupSign = md5Hex("&" + warmupTimestamp + "&" + appKey + "&" + dataJson);
        Map<String, String> warmupParams = buildMtopParams(dataJson, appKey, warmupTimestamp, warmupSign);

        HttpRequest warmupRequest = HttpRequest.newBuilder(URI.create(H5_TK_API + "?" + buildQuery(warmupParams)))
                .GET()
                .header("User-Agent", defaultUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://passport.goofish.com")
                .header("Referer", "https://passport.goofish.com/")
                .build();
        HttpResponse<String> warmupResponse = httpClient.send(warmupRequest, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, warmupResponse);

        if (hasMh5Token(session.cookies)) {
            return;
        }

        // Compatibility fallback for environments still expecting the legacy warmup shape.
        HttpRequest legacyWarmupRequest = HttpRequest.newBuilder(URI.create(H5_TK_API))
                .GET()
                .header("User-Agent", defaultUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://passport.goofish.com")
                .header("Referer", "https://passport.goofish.com/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();
        HttpResponse<String> legacyWarmupResponse = httpClient.send(legacyWarmupRequest, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, legacyWarmupResponse);
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

    private String resolveMh5TokenCookie(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        return firstNonBlank(cookies.get("m_h5_tk"), cookies.get("_m_h5_tk"));
    }

    private boolean hasMh5Token(Map<String, String> cookies) {
        return !isBlank(extractCookieToken(resolveMh5TokenCookie(cookies)));
    }

    private Map<String, String> fetchLoginFormData(InternalQrSession session) throws IOException, InterruptedException {
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

        HttpRequest request = HttpRequest.newBuilder(URI.create(MINI_LOGIN_URL + "?" + buildQuery(params)))
                .GET()
                .header("User-Agent", defaultUserAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://passport.goofish.com")
                .header("Referer", "https://passport.goofish.com/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        String viewDataJson = extractViewDataJson(response.body());
        if (isBlank(viewDataJson)) {
            throw new IOException("parse mini_login viewData failed");
        }

        JsonNode root = objectMapper.readTree(viewDataJson);
        JsonNode loginFormData = root.path("loginFormData");
        if (!loginFormData.isObject()) {
            throw new IOException("mini_login missing loginFormData");
        }

        Map<String, Object> raw = objectMapper.convertValue(loginFormData, new TypeReference<Map<String, Object>>() {
        });
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, v.toString());
            }
        });
        out.put("umidTag", "SERVER");
        return out;
    }

    private QrPayload fetchQrPayload(InternalQrSession session) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GENERATE_QR_URL + "?" + buildQuery(session.params)))
                .GET()
                .header("User-Agent", defaultUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://passport.goofish.com")
                .header("Referer", "https://passport.goofish.com/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("content");
        boolean success = content.path("success").asBoolean(false);
        JsonNode data = content.path("data");
        if (!success || !data.isObject()) {
            throw new IOException("generate qr failed: " + response.body());
        }

        QrPayload payload = new QrPayload();
        payload.t = data.path("t").asText(null);
        payload.ck = data.path("ck").asText(null);
        payload.codeContent = data.path("codeContent").asText(null);

        if (isBlank(payload.t) || isBlank(payload.ck) || isBlank(payload.codeContent)) {
            throw new IOException("generate qr response missing t/ck/codeContent");
        }
        return payload;
    }

    private void pollStatus(InternalQrSession session) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(QUERY_QR_URL))
                .POST(HttpRequest.BodyPublishers.ofString(buildQuery(session.params)))
                .header("User-Agent", defaultUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://passport.goofish.com")
                .header("Referer", "https://passport.goofish.com/")
                .header("Cookie", buildCookieHeader(session.cookies))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        mergeSetCookies(session.cookies, response);

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("content").path("data");
        String qrStatus = data.path("qrCodeStatus").asText(null);
        if (isBlank(qrStatus)) {
            session.status = XianyuQrLoginSession.STATUS_ERROR;
            session.message = "qr status missing";
            return;
        }

        switch (qrStatus) {
            case "NEW":
                session.status = XianyuQrLoginSession.STATUS_WAITING;
                break;
            case "SCANED":
                session.status = XianyuQrLoginSession.STATUS_SCANNED;
                break;
            case "EXPIRED":
                session.status = XianyuQrLoginSession.STATUS_EXPIRED;
                break;
            case "CONFIRMED":
                boolean iframeRedirect = data.path("iframeRedirect").asBoolean(false);
                if (iframeRedirect) {
                    session.status = XianyuQrLoginSession.STATUS_VERIFICATION_REQUIRED;
                    session.verificationUrl = data.path("iframeRedirectUrl").asText(null);
                } else {
                    session.status = XianyuQrLoginSession.STATUS_SUCCESS;
                    session.unb = session.cookies.get("unb");
                    session.cookieHeader = buildCookieHeader(session.cookies);
                }
                break;
            default:
                session.status = XianyuQrLoginSession.STATUS_CANCELLED;
                break;
        }
    }

    private XianyuQrLoginSession toPublicSession(InternalQrSession source) {
        XianyuQrLoginSession session = new XianyuQrLoginSession();
        session.setSessionId(source.sessionId);
        session.setStatus(source.status);
        session.setQrContent(source.qrContent);
        session.setQrCodeDataUrl(source.qrCodeDataUrl);
        session.setVerificationUrl(source.verificationUrl);
        session.setCookieHeader(source.cookieHeader);
        session.setUnb(source.unb);
        session.setCreatedAt(source.createdAt);
        session.setExpiresInSeconds(EXPIRE_SECONDS);
        session.setMessage(source.message);
        return session;
    }

    private boolean isExpired(InternalQrSession session) {
        return session == null
                || session.createdAt == null
                || Instant.now().isAfter(session.createdAt.plusSeconds(EXPIRE_SECONDS));
    }

    private void mergeSetCookies(Map<String, String> targetCookies, HttpResponse<?> response) {
        if (targetCookies == null || response == null) {
            return;
        }
        for (String setCookie : response.headers().allValues("set-cookie")) {
            if (isBlank(setCookie)) {
                continue;
            }
            int semicolon = setCookie.indexOf(';');
            String pair = semicolon > 0 ? setCookie.substring(0, semicolon) : setCookie;
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (!isBlank(key) && value != null) {
                targetCookies.put(key, value);
            }
        }
    }

    private String extractViewDataJson(String html) {
        if (isBlank(html)) {
            return null;
        }
        String marker = "window.viewData";
        int markerIdx = html.indexOf(marker);
        if (markerIdx < 0) {
            return null;
        }
        int eqIdx = html.indexOf("=", markerIdx);
        if (eqIdx < 0) {
            return null;
        }
        int startBrace = html.indexOf("{", eqIdx);
        if (startBrace < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = startBrace; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return html.substring(startBrace, i + 1);
                }
            }
        }
        return null;
    }

    private String buildQrDataUrl(String qrContent) throws IOException {
        if (isBlank(qrContent)) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = new MultiFormatWriter()
                    .encode(qrContent, BarcodeFormat.QR_CODE, 300, 300, hints);
            BufferedImage image = toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            throw new IOException("generate qr image failed: " + e.getMessage(), e);
        }
    }

    private BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }

    private String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 calculation failed", e);
        }
    }

    private String extractCookieToken(String tokenCookie) {
        if (isBlank(tokenCookie)) {
            return null;
        }
        int idx = tokenCookie.indexOf('_');
        if (idx > 0) {
            return tokenCookie.substring(0, idx);
        }
        return tokenCookie;
    }

    private String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String defaultUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class QrPayload {
        private String t;
        private String ck;
        private String codeContent;
    }

    private static final class InternalQrSession {
        private String sessionId;
        private String status;
        private String qrContent;
        private String qrCodeDataUrl;
        private String verificationUrl;
        private String cookieHeader;
        private String unb;
        private String message;
        private Instant createdAt;
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private final Map<String, String> params = new LinkedHashMap<>();
    }
}
