package cn.net.rjnetwork.xianyu.proxy.provider.kuaidaili;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 快代理 API 签名工具。
 *
 * <p>支持两种鉴权方式：</p>
 * <ul>
 *   <li><b>token</b>（默认）：调用 {@code auth.kdlapi.com/api/get_secret_token} 获取 secret_token，
 *       直接作为 signature 使用</li>
 *   <li><b>hmacsha1</b>：用 secret_key 对请求串做 HMAC-SHA1 + Base64 签名</li>
 * </ul>
 *
 * <p>参考文档：https://help.kuaidaili.com/api/auth/</p>
 */
public class KuaidailiAuth {

    private static final Logger log = LoggerFactory.getLogger(KuaidailiAuth.class);

    /** 获取 secret_token 的 API 域名 */
    private static final String AUTH_API = "https://auth.kdlapi.com/api/get_secret_token";

    private final String secretId;
    private final String secretKey;
    private final String authType;

    /** token 模式下的缓存 */
    private volatile String cachedToken;
    private volatile long tokenExpireAtMs;

    public KuaidailiAuth(ProxyProperties.KuaidailiConfig config) {
        this(config.getSecretId(), config.getSecretKey(), config.getAuthType());
    }

    public KuaidailiAuth(String secretId, String secretKey, String authType) {
        if (secretId == null || secretId.isBlank()) {
            throw new IllegalArgumentException("快代理 secretId 不能为空");
        }
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.authType = (authType == null || authType.isBlank()) ? "token" : authType.toLowerCase();
    }

    public String getSecretId() {
        return secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getAuthType() {
        return authType;
    }

    /**
     * 构建完整的带签名请求参数。
     *
     * @param path 请求路径（如 /api/gettps）
     * @param params 业务参数（不含 signature）
     * @return 包含 signature 的完整参数
     */
    public Map<String, String> buildSignedParams(String path, Map<String, String> params) {
        Map<String, String> fullParams = new TreeMap<>();
        fullParams.put("secret_id", secretId);
        if (params != null) {
            fullParams.putAll(params);
        }

        if ("hmacsha1".equals(authType)) {
            fullParams.put("sign_type", "hmacsha1");
            fullParams.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            fullParams.put("nonce", String.valueOf(ThreadLocalRandom.current().nextInt(1, 100_000_000)));
            String signature = signHmacSha1("GET", path, fullParams);
            fullParams.put("signature", signature);
        } else {
            // token 模式
            fullParams.put("sign_type", "token");
            fullParams.put("signature", getOrRefreshToken());
        }
        return fullParams;
    }

    /**
     * 获取或刷新 secret_token。
     */
    public synchronized String getOrRefreshToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireAtMs) {
            return cachedToken;
        }
        return refreshToken();
    }

    /**
     * 强制刷新 secret_token。
     */
    public synchronized String refreshToken() {
        // 实际 HTTP 调用由上层 Provider 通过 HttpUtils 发起，这里仅返回占位
        // 真实实现：POST https://auth.kdlapi.com/api/get_secret_token  body: secret_id=xxx&secret_key=xxx
        log.debug("[KUAILAILI-AUTH] 刷新 secret_token, secretId={}", secretId);
        return cachedToken != null ? cachedToken : "";
    }

    /**
     * 设置缓存的 token（由 Provider 调用 API 后回填）。
     */
    public void setCachedToken(String token, long expireSeconds) {
        this.cachedToken = token;
        // 提前 3 分钟刷新
        this.tokenExpireAtMs = System.currentTimeMillis() + (expireSeconds - 180) * 1000;
    }

    /**
     * HMAC-SHA1 签名。
     *
     * @param method GET / POST
     * @param path 请求路径（如 /api/gettps）
     * @param params 已排序的参数（不含 signature）
     * @return Base64 编码的签名字符串
     */
    public String signHmacSha1(String method, String path, Map<String, String> params) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("hmacsha1 模式需要 secretKey");
        }
        StringBuilder queryStr = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            queryStr.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        if (queryStr.length() > 0) {
            queryStr.setLength(queryStr.length() - 1);
        }
        String rawStr = method.toUpperCase() + path + "?" + queryStr;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(rawStr.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA1 签名失败", e);
        }
    }

    /**
     * 构建完整 URL（含签名参数）。
     */
    public String buildSignedUrl(String baseUrl, String path, Map<String, String> params) {
        Map<String, String> signedParams = buildSignedParams(path, params);
        StringBuilder url = new StringBuilder(baseUrl).append(path).append("?");
        for (Map.Entry<String, String> entry : signedParams.entrySet()) {
            url.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        url.setLength(url.length() - 1);
        return url.toString();
    }

    /**
     * 获取 token API 的 URL（供 Provider 调用）。
     */
    public static String getAuthApiUrl() {
        return AUTH_API;
    }

    /**
     * 构建获取 token 的表单参数。
     */
    public Map<String, String> buildTokenRequestParams() {
        Map<String, String> params = new HashMap<>();
        params.put("secret_id", secretId);
        params.put("secret_key", secretKey);
        return params;
    }
}
