package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 闲鱼 MTOP API 客户端
 * 通过 HTTP 直接调用 MTOP API，不需要浏览器。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>自动预热 _m_h5_tk cookie（首次调用前打一次空请求让服务端下发该 cookie）</li>
 *   <li>每次请求自动计算 sign = md5(token + "&" + t + "&" + appKey + "&" + data)</li>
 *   <li>统一设置 Referer / Origin / x-sgext 等闲鱼风控必需头</li>
 *   <li>维护当前 cookie，可在登录成功后更新</li>
 * </ul>
 */
public class XianyuMtopApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String APP_KEY = "34839810";
    private static final String REFERER = "https://www.goofish.com/";
    private static final String ORIGIN = "https://www.goofish.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private String cookie;
    private boolean tokenPrimed = false;
    private final Map<String, String> baseHeaders = new HashMap<>();

    public XianyuMtopApiClient(String cookie) {
        this.cookie = cookie != null ? cookie : "";
        this.baseHeaders.put("User-Agent", USER_AGENT);
        this.baseHeaders.put("Accept", "application/json");
        this.baseHeaders.put("Content-Type", "application/x-www-form-urlencoded");
        this.baseHeaders.put("Referer", REFERER);
        this.baseHeaders.put("Origin", ORIGIN);
    }

    /** 发送 GET 请求 */
    public JsonNode get(String url) {
        return send(url, "GET", null);
    }

    /** 发送 POST 请求（form-encoded body） */
    public JsonNode post(String url, String body) {
        return send(url, "POST", body);
    }

    /** 发送 POST 请求（JSON body） */
    public JsonNode postJson(String url, String jsonBody) {
        return send(url, "POST_JSON", jsonBody);
    }

    /**
     * 调用 MTOP 接口的统一方法。
     *
     * @param api      接口名，如 mtop.taobao.idleuser.info.get
     * @param dataJson 业务数据 JSON 字符串
     * @return 响应 JSON
     */
    public JsonNode callMtop(String api, String dataJson) {
        primeTokenIfNeeded();

        try {
            XianyuMtopRequestBuilder builder = new XianyuMtopRequestBuilder(api)
                    .setCookie(cookie)
                    .setDataJson(dataJson != null ? dataJson : "{}");

            String url = builder.buildUrl();
            String body = builder.buildPostBody();

            JsonNode resp = send(url, "POST", body);

            // 处理 token 过期：返回 ret[0]=FAIL_SYS_TOKEN_EXOIRED 时，重新预热后重试一次
            if (isTokenExpired(resp)) {
                tokenPrimed = false;
                primeTokenIfNeeded();
                XianyuMtopRequestBuilder retry = new XianyuMtopRequestBuilder(api)
                        .setCookie(cookie)
                        .setDataJson(dataJson != null ? dataJson : "{}");
                return send(retry.buildUrl(), "POST", retry.buildPostBody());
            }
            return resp;
        } catch (Exception e) {
            System.err.println("[MTOP callMtop Error] api=" + api + ", err=" + e.getMessage());
            return null;
        }
    }

    private JsonNode send(String url, String method, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            for (Map.Entry<String, String> e : baseHeaders.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
            builder.header("Cookie", cookie != null ? cookie : "");

            if ("GET".equals(method)) {
                builder.GET();
            } else if ("POST".equals(method)) {
                builder.header("Content-Type", "application/x-www-form-urlencoded");
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else if ("POST_JSON".equals(method)) {
                builder.header("Content-Type", "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            // 更新 cookie（吸收 Set-Cookie）
            this.cookie = mergeCookieFromResponse(cookie, response);
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            System.err.println("[MTOP " + method + " Error] " + e.getMessage());
            return null;
        }
    }

    /**
     * 预热 _m_h5_tk cookie：调用一次轻量接口让服务端下发该 cookie。
     * 闲鱼所有 MTOP 接口都需要 _m_h5_tk 中的 token 来计算 sign。
     */
    public synchronized void primeTokenIfNeeded() {
        if (tokenPrimed) return;
        if (cookie != null && XianyuMtopRequestBuilder.extractTokenFromCookie(cookie) != null
                && !XianyuMtopRequestBuilder.extractTokenFromCookie(cookie).isEmpty()) {
            tokenPrimed = true;
            return;
        }
        try {
            // 用一个通用的 Gaia 接口做预热，不依赖登录态
            String dataJson = "{\"bizScene\":\"home\"}";
            XianyuMtopRequestBuilder builder = new XianyuMtopRequestBuilder(
                    "mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get")
                    .setCookie(cookie != null ? cookie : "")
                    .setDataJson(dataJson);
            String url = builder.buildUrl();
            String body = builder.buildPostBody();

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            for (Map.Entry<String, String> e : baseHeaders.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
            rb.header("Cookie", cookie != null ? cookie : "")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            this.cookie = mergeCookieFromResponse(cookie, response);
            tokenPrimed = true;
        } catch (Exception e) {
            System.err.println("[MTOP primeTokenIfNeeded Error] " + e.getMessage());
        }
    }

    /** 检查响应是否为 token 过期 */
    private boolean isTokenExpired(JsonNode resp) {
        if (resp == null) return false;
        JsonNode ret = resp.path("ret");
        if (!ret.isArray() || ret.size() == 0) return false;
        String r0 = ret.get(0).asText("");
        return r0.contains("FAIL_SYS_TOKEN_EXOIRED")
                || r0.contains("FAIL_SYS_ILLEGAL_REQUEST")
                || r0.contains("FAIL_SYS_TOKEN_EMPTY");
    }

    /** 从 Set-Cookie 头合并到当前 cookie 字符串 */
    private static String mergeCookieFromResponse(String currentCookie, HttpResponse<?> response) {
        Map<String, String> cookies = new HashMap<>();
        if (currentCookie != null) {
            for (String pair : currentCookie.split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        for (String sc : response.headers().allValues("set-cookie")) {
            if (sc == null || sc.isEmpty()) continue;
            int semi = sc.indexOf(';');
            String pair = semi > 0 ? sc.substring(0, semi) : sc;
            int eq = pair.indexOf('=');
            if (eq > 0) {
                cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    public String getCookie() {
        return cookie;
    }

    public void updateCookie(String newCookie) {
        this.cookie = newCookie != null ? newCookie : "";
        this.tokenPrimed = false;
    }
}
