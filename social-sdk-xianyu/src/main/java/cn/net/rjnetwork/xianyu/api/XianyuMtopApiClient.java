package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

    /** IM/滑块验证获取的 cookie（x5sec 等），与登录 cookie 合并使用 */
    private String imCookieHeader;

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
     * 上传 multipart/form-data 文件到闲鱼 CDN（stream-upload.goofish.com）。
     * <p>真实抓包验证（XianYuApis upload_media + xianyu-auto-reply image_uploader）：</p>
     * <ul>
     *   <li>URL: https://stream-upload.goofish.com/api/upload.api?floderId=0&appkey=xy_chat&_input_charset=utf-8</li>
     *   <li>method: POST，Content-Type: multipart/form-data; boundary=...</li>
     *   <li>form 字段名: "file"，含 filename + Content-Type（image/png 或 video/mp4）</li>
     *   <li>headers: 同 mtop 调用（origin/referer/sec-* 等），不要 sign（流上传不走 mtop 签名）</li>
     *   <li>返回: {object: {url: "http://img.alicdn.com/...", pix: "WxH"}}（图片有 pix，视频无 pix 但有 url）</li>
     * </ul>
     *
     * @param url        完整上传 URL（含 query params）
     * @param fileBytes  文件二进制内容
     * @param filename   文件名（如 "1.jpg"）
     * @param contentType 文件 Content-Type（如 "image/png" / "video/mp4"）
     * @return 闲鱼返回 JSON，含 object.url（CDN 地址）和 object.pix（"宽x高"，视频可能无）
     */
    public JsonNode uploadMultipart(String url, byte[] fileBytes, String filename, String contentType) {
        try {
            // 拼 multipart body —— boundary + file part + end
            String boundary = "----xianyu-sdk-boundary-" + System.currentTimeMillis();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            String CRLF = "\r\n";

            // file part header
            buf.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            buf.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
            buf.write(("Content-Type: " + contentType + CRLF).getBytes(StandardCharsets.UTF_8));
            buf.write(CRLF.getBytes(StandardCharsets.UTF_8));
            // file binary
            buf.write(fileBytes);
            buf.write(CRLF.getBytes(StandardCharsets.UTF_8));
            // end
            buf.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));

            byte[] body = buf.toByteArray();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "*/*")
                    .header("Origin", ORIGIN)
                    .header("Referer", REFERER)
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", cookie != null ? cookie : "")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            this.cookie = mergeCookieFromResponse(cookie, response);
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            System.err.println("[MTOP uploadMultipart Error] " + e.getMessage());
            return null;
        }
    }

    /**
     * 调用 MTOP 接口的统一方法。
     *
     * @param api      接口名，如 mtop.taobao.idleuser.info.get
     * @param dataJson 业务数据 JSON 字符串
     * @return 响应 JSON
     */
    public JsonNode callMtop(String api, String dataJson) {
        return callMtop(api, "1.0", dataJson);
    }

    /**
     * 调用 MTOP 接口（可指定版本号）。
     * 部分接口要求特定 v，如上下架 mtop.taobao.idle.item.downshelf 要 v="2.0"。
     *
     * @param api      接口名
     * @param version  接口版本号，如 "1.0" / "2.0"；传 null 或空则用默认 "1.0"
     * @param dataJson 业务数据 JSON 字符串
     * @return 响应 JSON
     */
    public JsonNode callMtop(String api, String version, String dataJson) {
        primeTokenIfNeeded();
        String v = (version != null && !version.isBlank()) ? version : "1.0";

        try {
            XianyuMtopRequestBuilder builder = new XianyuMtopRequestBuilder(api)
                    .setCookie(getMergedCookie())
                    .setVersion(v)
                    .setDataJson(dataJson != null ? dataJson : "{}");

            String url = builder.buildUrl();
            String body = builder.buildPostBody();

            JsonNode resp = send(url, "POST", body);
            rememberBusinessError(resp);

            // 处理 token 过期：返回 ret[0]=FAIL_SYS_TOKEN_EXOIRED 时，重新预热后重试一次
            if (isTokenExpired(resp)) {
                tokenPrimed = false;
                primeTokenIfNeeded();
                XianyuMtopRequestBuilder retry = new XianyuMtopRequestBuilder(api)
                        .setCookie(getMergedCookie())
                        .setVersion(v)
                        .setDataJson(dataJson != null ? dataJson : "{}");
                JsonNode retryResp = send(retry.buildUrl(), "POST", retry.buildPostBody());
                rememberBusinessError(retryResp);
                return retryResp;
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
            builder.header("Cookie", getMergedCookie());

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
            lastErrorResponse = "Error: " + e.getMessage();
            System.err.println("[MTOP " + method + " Error] " + e.getMessage());
            return null;
        }
    }

    private void rememberBusinessError(JsonNode resp) {
        if (resp == null) return;
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) {
            String r0 = ret.get(0).asText("");
            if (r0.startsWith("FAIL_") || r0.contains("RGV587") || r0.contains("punish") || r0.contains("captcha")) {
                lastErrorResponse = resp.toString();
            }
        }
    }

    /** 获取最后错误响应内容 */
    public String getLastErrorResponse() {
        return lastErrorResponse != null ? lastErrorResponse : "";
    }

    private String lastErrorResponse;

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
                    .setCookie(getMergedCookie())
                    .setDataJson(dataJson);
            String url = builder.buildUrl();
            String body = builder.buildPostBody();

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            for (Map.Entry<String, String> e : baseHeaders.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
            rb.header("Cookie", getMergedCookie())
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

    /** 检查是否触发风控（验证码/滑块/挤爆） */
    public boolean isRiskControlTriggered(JsonNode resp) {
        if (resp == null) return false;
        JsonNode ret = resp.path("ret");
        if (!ret.isArray() || ret.size() == 0) return false;
        String r0 = ret.get(0).asText("");
        return r0.contains("FAIL_SYS_USER_VALIDATE")
                || r0.contains("RGV587")
                || r0.contains("FAIL_SYS_ILLEGAL_ACCESS")
                || r0.contains("FAIL_BIZ_WUA_IS_MACHINE")
                || r0.contains("WUA_IS_MACHINE")
                || r0.contains("哎哟喂")
                || r0.contains("挤爆")
                || r0.contains("punish")
                || r0.contains("captcha")
                || r0.contains("validate");
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

    public String getImCookieHeader() {
        return imCookieHeader;
    }

    public void setImCookieHeader(String imCookieHeader) {
        this.imCookieHeader = imCookieHeader;
    }

    /**
     * 获取合并后的 cookie：登录 cookie + IM/滑块验证 cookie（x5sec 等）。
     * 用于 WebSocket Upgrade 请求的 Cookie 头，确保风控 cookie 一并发送。
     */
    public String getMergedCookie() {
        if (imCookieHeader == null || imCookieHeader.isBlank()) {
            return cookie;
        }
        if (cookie == null || cookie.isBlank()) {
            return imCookieHeader;
        }
        // 合并：imCookieHeader 中的字段覆盖同名字段
        Map<String, String> merged = new HashMap<>();
        for (String pair : cookie.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) merged.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        for (String pair : imCookieHeader.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) merged.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }
}
