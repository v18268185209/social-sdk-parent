package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 闲鱼 MTOP API 请求构建器
 * 负责构建 MTOP API 请求的 URL、参数和签名
 */
public class XianyuMtopRequestBuilder {

    private static final String API_HOST = "h5api.m.goofish.com";
    private static final String API_BASE_PATH = "/h5/";
    private static final String APP_KEY = "34839810";
    private static final String T = "1.0";
    private static final String TYPE = "originaljson";
    private static final String ACCOUNT_SITE = "xianyu";
    private static final String DATA_TYPE = "json";
    private static final String TIMEOUT = "20000";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String api;
    private final Map<String, String> params = new LinkedHashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private String sign;
    private String cookie;

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

    public XianyuMtopRequestBuilder addHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    public XianyuMtopRequestBuilder setCookie(String cookie) {
        this.cookie = cookie;
        this.headers.put("Cookie", cookie);
        return this;
    }

    public XianyuMtopRequestBuilder setSign(String sign) {
        this.sign = sign;
        return this;
    }

    /**
     * 构建完整的请求 URL
     */
    public String buildUrl() {
        StringBuilder url = new StringBuilder();
        url.append("https://").append(API_HOST).append(API_BASE_PATH).append(api).append("/").append(T).append("/");

        // 添加查询参数
        url.append("?jsv=2.7.2");
        url.append("&appKey=").append(APP_KEY);
        url.append("&t=").append(System.currentTimeMillis());
        if (sign != null) {
            url.append("&sign=").append(sign);
        }
        url.append("&v=").append(T);
        url.append("&type=").append(TYPE);
        url.append("&accountSite=").append(ACCOUNT_SITE);
        url.append("&dataType=").append(DATA_TYPE);
        url.append("&timeout=").append(TIMEOUT);
        url.append("&api=").append(api);

        // 添加自定义参数
        for (Map.Entry<String, String> entry : params.entrySet()) {
            url.append("&").append(entry.getKey()).append("=").append(entry.getValue());
        }

        return url.toString();
    }

    /**
     * 获取请求头
     */
    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }

    /**
     * 解析响应 JSON
     */
    public static JsonNode parseResponse(String responseBody) {
        try {
            return MAPPER.readTree(responseBody);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 URL 中提取 query params
     */
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

    /**
     * 从 Cookie 字符串中提取指定 cookie 值
     */
    public static String getCookieValue(String cookieStr, String name) {
        if (cookieStr == null || cookieStr.isEmpty()) {
            return null;
        }
        for (String cookie : cookieStr.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(name)) {
                return parts[1];
            }
        }
        return null;
    }
}
