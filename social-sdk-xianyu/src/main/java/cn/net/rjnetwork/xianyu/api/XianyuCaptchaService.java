package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 闲鱼验证码解题服务
 * <p>提供风控验证码相关的工具方法：</p>
 * <ul>
 *   <li>判断 URL 是否仍在风控 punish 页</li>
 *   <li>计算滑块需滑动的距离（普通滑块 / 刮刮乐）</li>
 *   <li>判断是否为刮刮乐验证码</li>
 *   <li>判断验证是否通过（x5sec 从无到有）</li>
 *   <li>重新请求 token 拿新鲜验证链接</li>
 * </ul>
 * <p>构造函数参数可为 null，纯工具方法（isPunishUrl / calculateSlideDistance / isScratchCaptcha / isVerificationPassed）
 * 无需 cookie 即可调用；requestFreshCaptchaUrl 需要有效 cookie 才能发起真实请求。</p>
 */
public class XianyuCaptchaService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";

    private final String cookie;
    private final HttpClient httpClient;

    public XianyuCaptchaService(String cookie) {
        this.cookie = cookie;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // ==================== 风控检测 / 处理 ====================

    /**
     * 风控触发检测：判断接口响应是否被 punish 拦截
     */
    public boolean isRiskControlTriggered(JsonNode response) {
        if (response == null) return false;
        JsonNode ret = response.path("ret");
        if (ret.isArray()) {
            for (JsonNode r : ret) {
                String s = r.asText("");
                if (s.contains("FAIL_SYS_USER_VALIDATE")
                        || s.contains("ILLEGAL_REQUEST")
                        || s.contains("punish")
                        || s.contains("captcha")) {
                    return true;
                }
            }
        }
        // data 层 punishUrl / verifyUrl
        JsonNode data = response.path("data");
        return data.has("punishUrl") || data.has("verifyUrl") || data.has("captchaUrl");
    }

    /**
     * 从风控拦截响应里提取 punish 验证 URL
     */
    public String extractPunishUrl(JsonNode response) {
        if (response == null) return null;
        JsonNode data = response.path("data");
        if (data.has("punishUrl")) return data.get("punishUrl").asText("");
        if (data.has("verifyUrl")) return data.get("verifyUrl").asText("");
        if (data.has("captchaUrl")) return data.get("captchaUrl").asText("");
        return null;
    }

    /**
     * 风控触发时的统一处理入口
     */
    public CaptchaRefetchResult handleRiskControl(JsonNode response, String deviceId) {
        String punishUrl = extractPunishUrl(response);
        if (punishUrl != null) {
            CaptchaRefetchResult r = new CaptchaRefetchResult();
            r.setSuccess(false);
            r.setUrl(punishUrl);
            r.setMessage("风控拦截，需验证");
            return r;
        }
        return requestFreshCaptchaUrl(deviceId);
    }

    // ==================== 纯工具方法（无需 cookie） ====================

    /**
     * 判断 URL 是否仍在风控 punish 页
     *
     * @param url 待检测的 URL
     * @return true 表示仍在风控页
     */
    public boolean isPunishUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        return lower.contains("punish")
                || lower.contains("captcha")
                || lower.contains("x5step")
                || lower.contains("verify")
                || lower.contains("challenge");
    }

    /**
     * 计算滑块需滑动的距离
     *
     * @param track   轨道总宽度（像素）
     * @param btn     滑块按钮宽度（像素）
     * @param scratch 是否为刮刮乐类型
     * @return 需要滑动的距离（像素）
     */
    public double calculateSlideDistance(double track, double btn, boolean scratch) {
        if (scratch) {
            return Math.max(track - btn, 50.0);
        }
        return Math.max(track - btn, 0.0);
    }

    /**
     * 判断是否为刮刮乐验证码
     *
     * @param htmlContent 页面 HTML 内容
     * @return true 表示是刮刮乐
     */
    public boolean isScratchCaptcha(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) return false;
        String lower = htmlContent.toLowerCase();
        return lower.contains("scratch")
                || lower.contains("scratch-captcha")
                || lower.contains("guaguale")
                || lower.contains("刮刮");
    }

    /**
     * 判断验证是否通过
     *
     * @param originalUrl 原始请求 URL
     * @param oldX5sec    旧的 x5sec 值（可为 null）
     * @param newX5sec    新的 x5sec 值
     * @param expectX5sec 是否期望 x5sec 出现
     * @return true 表示验证通过
     */
    public boolean isVerificationPassed(String originalUrl, String oldX5sec, String newX5sec, boolean expectX5sec) {
        if (expectX5sec) {
            return newX5sec != null && !newX5sec.isBlank()
                    && (oldX5sec == null || !newX5sec.equals(oldX5sec));
        }
        return newX5sec != null && !newX5sec.isBlank();
    }

    // ==================== 需要 cookie 的方法 ====================

    /**
     * 重新请求 token 拿新鲜验证链接
     *
     * @param deviceId 设备 id（可选，可为 null）
     * @return 新鲜验证结果
     */
    public CaptchaRefetchResult requestFreshCaptchaUrl(String deviceId) {
        CaptchaRefetchResult result = new CaptchaRefetchResult();

        if (cookie == null || cookie.isBlank()) {
            result.setSuccess(false);
            result.setMessage("Cookie 为空，无法请求新鲜验证链接");
            return result;
        }

        try {
            String url = "https://www.goofish.com/personal";
            if (deviceId != null && !deviceId.isBlank()) {
                url += "?deviceId=" + deviceId;
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Cookie", cookie)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            result.setTimestamp(System.currentTimeMillis());
            result.setStatusCode(response.statusCode());

            String body = response.body();
            if (body != null && !body.isBlank()) {
                if (isPunishUrl(response.uri().toString()) || body.contains("captcha")) {
                    result.setSuccess(false);
                    result.setUrl(response.uri().toString());
                    result.setMessage("命中风控验证码页");
                } else {
                    result.setSuccess(true);
                    result.setUrl(response.uri().toString());
                    result.setMessage("请求成功，未命中风控");
                }
            } else {
                result.setSuccess(false);
                result.setMessage("响应体为空");
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("请求失败: " + e.getMessage());
        }

        return result;
    }

    // ==================== 内部 DTO ====================

    public static class CaptchaRefetchResult {
        private boolean success;
        private String url;
        private int statusCode;
        private String message;
        private long timestamp;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
