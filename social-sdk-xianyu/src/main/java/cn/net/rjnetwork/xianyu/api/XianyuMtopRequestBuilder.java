package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼 MTOP API 请求构建器
 * 负责构建 MTOP API 请求的 URL、参数、签名和 POST body。
 *
 * <p>闲鱼/淘宝 MTOP 风控要求：</p>
 * <ul>
 *   <li>URL query 只放公共参数：jsv / appKey / t / sign / v / type / accountSite / dataType / timeout / api</li>
 *   <li>POST body form-encoded：t / sign / appKey / api / v / type / dataType / timeout / data=&lt;业务JSON&gt;</li>
 *   <li>sign = md5(token + "&" + t + "&" + appKey + "&" + data)，token 取自 _m_h5_tk cookie 的下划线前半部分</li>
 *   <li>必须先预热 _m_h5_tk cookie（首次请求会下发该 cookie）</li>
 * </ul>
 */
public class XianyuMtopRequestBuilder {

    private static final String API_HOST = "h5api.m.goofish.com";
    private static final String API_BASE_PATH = "/h5/";
    private static final String APP_KEY = "34839810";
    private static final String V = "1.0";
    private static final String TYPE = "originaljson";
    private static final String ACCOUNT_SITE = "xianyu";
    private static final String DATA_TYPE = "json";
    private static final String TIMEOUT = "20000";
    private static final String JSV = "2.7.2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String api;
    private final Map<String, String> params = new LinkedHashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    /** 业务数据 JSON 字符串（最终放进 body 的 data 字段） */
    private String dataJson = "{}";
    /** 由外部显式传入的 token；不传则从 cookie 提取 */
    private String token;
    private String cookie;
    /** 接口版本号，默认 "1.0"，部分接口（如上下架）要 "2.0" */
    private String version = V;

    public XianyuMtopRequestBuilder(String api) {
        this.api = api;
        this.headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
        this.headers.put("Accept", "application/json");
        this.headers.put("Content-Type", "application/x-www-form-urlencoded");
    }

    public XianyuMtopRequestBuilder addParam(String key, String value) {
        params.put(key, value);
        return this;
    }

    /** 设置业务数据 JSON（会作为 POST body 的 data 字段） */
    public XianyuMtopRequestBuilder setDataJson(String dataJson) {
        this.dataJson = dataJson != null ? dataJson : "{}";
        return this;
    }

    /** 显式设置接口版本号（如上下架接口要 "2.0"），不传默认 "1.0" */
    public XianyuMtopRequestBuilder setVersion(String version) {
        this.version = (version != null && !version.isBlank()) ? version : V;
        return this;
    }

    /** 用 Map 设置业务数据，自动转 JSON */
    public XianyuMtopRequestBuilder setDataMap(Map<String, ?> dataMap) {
        try {
            this.dataJson = MAPPER.writeValueAsString(dataMap != null ? dataMap : new LinkedHashMap<>());
        } catch (Exception e) {
            this.dataJson = "{}";
        }
        return this;
    }

    public XianyuMtopRequestBuilder addHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public XianyuMtopRequestBuilder setCookie(String cookie) {
        this.cookie = cookie;
        this.headers.put("Cookie", cookie != null ? cookie : "");
        return this;
    }

    /** 显式设置 token（不传则从 cookie 提取 _m_h5_tk 的前半部分） */
    public XianyuMtopRequestBuilder setToken(String token) {
        this.token = token;
        return this;
    }

    /**
     * 构建完整的请求 URL（只含公共参数，业务参数走 body）
     */
    public String buildUrl() {
        long t = System.currentTimeMillis();
        String sign = computeSign(t);

        StringBuilder url = new StringBuilder();
        url.append("https://").append(API_HOST).append(API_BASE_PATH)
                .append(api).append("/").append(version).append("/");

        Map<String, String> qs = new LinkedHashMap<>();
        qs.put("jsv", JSV);
        qs.put("appKey", APP_KEY);
        qs.put("t", String.valueOf(t));
        qs.put("sign", sign);
        qs.put("v", version);
        qs.put("type", TYPE);
        qs.put("accountSite", ACCOUNT_SITE);
        qs.put("dataType", DATA_TYPE);
        qs.put("timeout", TIMEOUT);
        qs.put("api", api);
        url.append("?").append(buildFormBody(qs));
        return url.toString();
    }

    /**
     * 构建 POST body（form-encoded，包含公共参数 + data 业务 JSON）
     */
    public String buildPostBody() {
        long t = System.currentTimeMillis();
        String sign = computeSign(t);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("jsv", JSV);
        body.put("appKey", APP_KEY);
        body.put("t", String.valueOf(t));
        body.put("sign", sign);
        body.put("api", api);
        body.put("v", version);
        body.put("type", TYPE);
        body.put("dataType", DATA_TYPE);
        body.put("timeout", TIMEOUT);
        body.put("data", dataJson);
        // 保留兼容：业务参数也带上（部分接口允许）
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!body.containsKey(e.getKey())) {
                body.put(e.getKey(), e.getValue());
            }
        }
        return buildFormBody(body);
    }

    /**
     * 计算 MTOP sign = md5(token + "&" + t + "&" + appKey + "&" + data)
     */
    private String computeSign(long t) {
        String tk = token != null ? token : extractTokenFromCookie(cookie);
        String raw = tk + "&" + t + "&" + APP_KEY + "&" + dataJson;
        return md5Hex(raw);
    }

    /**
     * 从 cookie 中提取 _m_h5_tk 的 token 部分（下划线前半段）
     */
    public static String extractTokenFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return "";
        String h5tk = getCookieValue(cookie, "_m_h5_tk");
        if (h5tk == null || h5tk.isEmpty()) {
            h5tk = getCookieValue(cookie, "m_h5_tk");
        }
        if (h5tk == null || h5tk.isEmpty()) return "";
        int idx = h5tk.indexOf('_');
        return idx > 0 ? h5tk.substring(0, idx) : h5tk;
    }

    /**
     * 构造 form-encoded body（value 进行 URL 编码）
     */
    public static String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }

    public static JsonNode parseResponse(String responseBody) {
        try {
            return MAPPER.readTree(responseBody);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, String> extractParams(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    int eq = param.indexOf('=');
                    if (eq > 0) {
                        result.put(param.substring(0, eq), param.substring(eq + 1));
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static String getCookieValue(String cookieStr, String name) {
        if (cookieStr == null || cookieStr.isEmpty()) return null;
        for (String cookie : cookieStr.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(name)) {
                return parts[1];
            }
        }
        return null;
    }

    private static String md5Hex(String input) {
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
}
