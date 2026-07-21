package cn.net.rjnetwork.xianyu.proxy.provider.kuaidaili;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyProperties;
import cn.net.rjnetwork.xianyu.proxy.util.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 快代理 私密代理 Provider（动态短效代理，按 IP 付费）。
 *
 * <p>每次 acquire 调快代理 API（dps.kdlapi.com/api/getdps）提取一个新鲜 IP，
 * SDK 拿到真实 ip:port 和可用时长，业务方在有效期内使用，过期自动废弃。</p>
 *
 * <h3>API 接口</h3>
 * <ul>
 *   <li>获取代理 IP：{@code https://dps.kdlapi.com/api/getdps}</li>
 *   <li>检测代理有效性：{@code https://dps.kdlapi.com/api/checkdpsvalid}</li>
 *   <li>获取代理可用时长：{@code https://dps.kdlapi.com/api/getdpsvalidtime}</li>
 *   <li>获取订单 IP 提取余额：{@code https://dps.kdlapi.com/api/getipbalance}</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * proxy:
 *   providers:
 *     kuaidaili:
 *       enabled: true
 *       secretId: your_secret_id
 *       secretKey: your_secret_key
 *       authType: token
 *       private:
 *         enabled: true
 *         num: 1
 *         pt: 1
 *         distinct: true
 *         format: json
 * }</pre>
 *
 * <p>参考文档：https://help.kuaidaili.com/api/getdps/</p>
 */
public class KuaidailiPrivateProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(KuaidailiPrivateProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 私密代理 API 域名 */
    private static final String PRIVATE_API_BASE = "https://dps.kdlapi.com";
    private static final String GET_DPS_PATH = "/api/getdps";
    private static final String CHECK_DPS_VALID_PATH = "/api/checkdpsvalid";
    private static final String GET_DPS_VALID_TIME_PATH = "/api/getdpsvalidtime";
    private static final String GET_IP_BALANCE_PATH = "/api/getipbalance";

    private final KuaidailiAuth auth;
    private final ProxyProperties.KuaidailiPrivateSubConfig privateConfig;

    public KuaidailiPrivateProvider(KuaidailiAuth auth, ProxyProperties.KuaidailiPrivateSubConfig privateConfig) {
        if (auth == null) {
            throw new IllegalArgumentException("KuaidailiAuth 不能为 null");
        }
        if (privateConfig == null) {
            throw new IllegalArgumentException("KuaidailiPrivateSubConfig 不能为 null");
        }
        this.auth = auth;
        this.privateConfig = privateConfig;
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) throws ProxyException {
        log.info("[PROXY-KDL-PRIVATE] 提取私密代理, accountId={}", request.getAccountId());

        try {
            // 构建签名参数
            Map<String, String> params = new HashMap<>();
            params.put("num", String.valueOf(privateConfig.getNum() > 0 ? privateConfig.getNum() : 1));
            params.put("pt", String.valueOf(privateConfig.getPt() > 0 ? privateConfig.getPt() : 1));
            params.put("format", privateConfig.getFormat() != null ? privateConfig.getFormat() : "json");

            if (privateConfig.isDistinct()) {
                params.put("distinct", "1");
            }
            if (privateConfig.getAreaCode() != null && !privateConfig.getAreaCode().isBlank()) {
                params.put("area_code", privateConfig.getAreaCode());
            }
            if (privateConfig.getIsp() > 0) {
                params.put("isp", String.valueOf(privateConfig.getIsp()));
            }

            String url = auth.buildSignedUrl(PRIVATE_API_BASE, GET_DPS_PATH, params);
            log.debug("[PROXY-KDL-PRIVATE] 请求 URL: {}", url);

            String resp = HttpUtils.get(url);
            if (resp == null || resp.isBlank()) {
                throw new ProxyException("KDL_PRIVATE_EMPTY_RESP",
                        "快代理私密代理 API 响应为空", null, ProviderType.KUAILAILI_PRIVATE, true);
            }

            JsonNode root = MAPPER.readTree(resp);
            int code = root.has("code") ? root.get("code").asInt() : -1;
            if (code != 0) {
                String msg = root.has("msg") ? root.get("msg").asText() : "未知错误";
                throw new ProxyException("KDL_PRIVATE_API_ERROR",
                        "快代理私密代理 API 返回错误: code=" + code + ", msg=" + msg,
                        null, ProviderType.KUAILAILI_PRIVATE, isRetryable(code));
            }

            JsonNode data = root.get("data");
            if (data == null || !data.has("proxy_list")) {
                throw new ProxyException("KDL_PRIVATE_NO_DATA",
                        "快代理私密代理 API 未返回 proxy_list", null, ProviderType.KUAILAILI_PRIVATE, true);
            }

            JsonNode proxyList = data.get("proxy_list");
            if (proxyList == null || !proxyList.isArray() || proxyList.isEmpty()) {
                throw new ProxyException("KDL_PRIVATE_EMPTY_LIST",
                        "快代理私密代理 API 返回空列表", null, ProviderType.KUAILAILI_PRIVATE, true);
            }

            String proxyStr = proxyList.get(0).asText();
            if (proxyStr == null || proxyStr.isBlank()) {
                throw new ProxyException("KDL_PRIVATE_BAD_PROXY",
                        "快代理私密代理返回格式异常", null, ProviderType.KUAILAILI_PRIVATE, true);
            }

            // 解析 host:port
            String[] parts = proxyStr.split(":");
            if (parts.length != 2) {
                throw new ProxyException("KDL_PRIVATE_BAD_FORMAT",
                        "快代理私密代理返回格式异常: " + proxyStr, null, ProviderType.KUAILAILI_PRIVATE, true);
            }

            String host = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ProxyException("KDL_PRIVATE_BAD_PORT",
                        "快代理私密代理端口格式异常: " + parts[1], null, ProviderType.KUAILAILI_PRIVATE, true);
            }

            // 计算过期时间（私密代理默认 1-3 分钟有效期）
            LocalDateTime expireAt = LocalDateTime.now().plusSeconds(
                    privateConfig.getKeepAliveSec() > 0 ? privateConfig.getKeepAliveSec() : 120);

            log.info("[PROXY-KDL-PRIVATE] 提取私密代理成功, accountId={}, host:port={}:{}, expireAt={}",
                    request.getAccountId(), host, port, expireAt);

            return ProxyInfo.builder()
                    .providerType(ProviderType.KUAILAILI_PRIVATE)
                    .proxyType(ProxyType.RESIDENTIAL)
                    .host(host)
                    .port(port)
                    .username(auth.getSecretId())
                    .password(auth.getSecretKey())
                    .sessionType(SessionType.ROTATING)
                    .boundAccountId(request.getAccountId())
                    .acquiredAt(LocalDateTime.now())
                    .expireAt(expireAt)
                    .captchaPassed(false)
                    .consecutiveFailures(0)
                    .metadata("{\"provider\":\"kuaidaili-private\",\"apiHost\":\"dps.kdlapi.com\"}")
                    .build();

        } catch (ProxyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PROXY-KDL-PRIVATE] 提取私密代理异常, accountId={}", request.getAccountId(), e);
            throw new ProxyException("KDL_PRIVATE_EXCEPTION",
                    "快代理私密代理异常: " + e.getMessage(),
                    e, ProviderType.KUAILAILI_PRIVATE, true);
        }
    }

    @Override
    public void release(ProxyInfo proxy) {
        log.debug("[PROXY-KDL-PRIVATE] 释放私密代理（自然过期）, ip={}:{}", proxy.getHost(), proxy.getPort());
    }

    @Override
    public String queryBalance() {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("format", "json");
            String url = auth.buildSignedUrl(PRIVATE_API_BASE, GET_IP_BALANCE_PATH, params);
            String resp = HttpUtils.get(url);
            if (resp == null || resp.isBlank()) {
                return "FAIL: 响应为空";
            }
            JsonNode root = MAPPER.readTree(resp);
            int code = root.has("code") ? root.get("code").asInt() : -1;
            if (code == 0) {
                JsonNode data = root.get("data");
                if (data != null && data.has("balance")) {
                    return "余额: " + data.get("balance").asDouble() + " 元";
                }
                return "OK";
            }
            return "FAIL: code=" + code;
        } catch (Exception e) {
            return "FAIL: " + e.getMessage();
        }
    }

    @Override
    public String getProviderName() {
        return "KuaidailiPrivate(快代理-私密代理)";
    }

    /**
     * 判断错误码是否可重试。
     */
    private boolean isRetryable(int code) {
        // 2=订单过期, 3=暂无可用代理, 4=未实名认证, -5=不能提取私密代理 → 不可重试
        // -1=无效请求, -3=参数错误, -4=提取失败 → 可重试
        return code != 2 && code != 3 && code != 4 && code != -5
                && code != -11 && code != -12 && code != -13 && code != -14 && code != -15 && code != -16;
    }
}
