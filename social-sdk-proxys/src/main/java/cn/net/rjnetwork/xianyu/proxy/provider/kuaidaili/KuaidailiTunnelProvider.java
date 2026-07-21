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
 * 快代理 隧道代理 Provider。
 *
 * <p>隧道代理的核心能力：快代理提供一个固定的入口 host:port（如 tps121.kdlapi.com:15818），
 * SDK 侧只需要记住这唯一一个 host:port，快代理云端自动把请求路由到不同出口 IP。
 * SDK 不需要关心出口 IP 的轮换。</p>
 *
 * <h3>API 接口</h3>
 * <ul>
 *   <li>获取隧道代理 IP：{@code https://tps.kdlapi.com/api/gettps}</li>
 *   <li>立即更换隧道 IP：{@code https://tps.kdlapi.com/api/changetpsip}</li>
 *   <li>查询隧道当前 IP：{@code https://tps.kdlapi.com/api/tpscurrentip}</li>
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
 *       tunnel:
 *         enabled: true
 *         num: 1
 *         format: json
 * }</pre>
 *
 * <p>参考文档：https://help.kuaidaili.com/api/gettps/</p>
 */
public class KuaidailiTunnelProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(KuaidailiTunnelProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 隧道代理 API 域名 */
    private static final String TUNNEL_API_BASE = "https://tps.kdlapi.com";
    private static final String GET_TPS_PATH = "/api/gettps";
    private static final String CHANGE_TPS_IP_PATH = "/api/changetpsip";
    private static final String TPS_CURRENT_IP_PATH = "/api/tpscurrentip";

    private final KuaidailiAuth auth;
    private final ProxyProperties.KuaidailiTunnelSubConfig tunnelConfig;

    public KuaidailiTunnelProvider(KuaidailiAuth auth, ProxyProperties.KuaidailiTunnelSubConfig tunnelConfig) {
        if (auth == null) {
            throw new IllegalArgumentException("KuaidailiAuth 不能为 null");
        }
        if (tunnelConfig == null) {
            throw new IllegalArgumentException("KuaidailiTunnelSubConfig 不能为 null");
        }
        this.auth = auth;
        this.tunnelConfig = tunnelConfig;
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) throws ProxyException {
        log.info("[PROXY-KDL-TUNNEL] 获取隧道代理, accountId={}", request.getAccountId());

        try {
            // 构建签名参数
            Map<String, String> params = new HashMap<>();
            params.put("num", String.valueOf(tunnelConfig.getNum() > 0 ? tunnelConfig.getNum() : 1));
            params.put("format", tunnelConfig.getFormat() != null ? tunnelConfig.getFormat() : "json");

            String url = auth.buildSignedUrl(TUNNEL_API_BASE, GET_TPS_PATH, params);
            log.debug("[PROXY-KDL-TUNNEL] 请求 URL: {}", url);

            String resp = HttpUtils.get(url);
            if (resp == null || resp.isBlank()) {
                throw new ProxyException("KDL_TUNNEL_EMPTY_RESP",
                        "快代理隧道代理 API 响应为空", null, ProviderType.KUAILAILI_TUNNEL, true);
            }

            JsonNode root = MAPPER.readTree(resp);
            int code = root.has("code") ? root.get("code").asInt() : -1;
            if (code != 0) {
                String msg = root.has("msg") ? root.get("msg").asText() : "未知错误";
                throw new ProxyException("KDL_TUNNEL_API_ERROR",
                        "快代理隧道代理 API 返回错误: code=" + code + ", msg=" + msg,
                        null, ProviderType.KUAILAILI_TUNNEL, isRetryable(code));
            }

            JsonNode data = root.get("data");
            if (data == null || !data.has("proxy_list")) {
                throw new ProxyException("KDL_TUNNEL_NO_DATA",
                        "快代理隧道代理 API 未返回 proxy_list", null, ProviderType.KUAILAILI_TUNNEL, true);
            }

            JsonNode proxyList = data.get("proxy_list");
            if (proxyList == null || !proxyList.isArray() || proxyList.isEmpty()) {
                throw new ProxyException("KDL_TUNNEL_EMPTY_LIST",
                        "快代理隧道代理 API 返回空列表", null, ProviderType.KUAILAILI_TUNNEL, true);
            }

            String proxyStr = proxyList.get(0).asText();
            if (proxyStr == null || proxyStr.isBlank()) {
                throw new ProxyException("KDL_TUNNEL_BAD_PROXY",
                        "快代理隧道代理返回格式异常", null, ProviderType.KUAILAILI_TUNNEL, true);
            }

            // 解析 host:port
            String[] parts = proxyStr.split(":");
            if (parts.length != 2) {
                throw new ProxyException("KDL_TUNNEL_BAD_FORMAT",
                        "快代理隧道代理返回格式异常: " + proxyStr, null, ProviderType.KUAILAILI_TUNNEL, true);
            }

            String host = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ProxyException("KDL_TUNNEL_BAD_PORT",
                        "快代理隧道代理端口格式异常: " + parts[1], null, ProviderType.KUAILAILI_TUNNEL, true);
            }

            log.info("[PROXY-KDL-TUNNEL] 获取隧道代理成功, accountId={}, host:port={}:{}",
                    request.getAccountId(), host, port);

            return ProxyInfo.builder()
                    .providerType(ProviderType.KUAILAILI_TUNNEL)
                    .proxyType(ProxyType.RESIDENTIAL)
                    .host(host)
                    .port(port)
                    .username(auth.getSecretId())
                    .password(auth.getSecretKey())
                    .sessionType(SessionType.ROTATING)
                    .boundAccountId(request.getAccountId())
                    .acquiredAt(LocalDateTime.now())
                    .expireAt(LocalDateTime.now().plusMinutes(request.getStickyDurationMinutes()))
                    .captchaPassed(false)
                    .consecutiveFailures(0)
                    .metadata("{\"provider\":\"kuaidaili-tunnel\",\"apiHost\":\"tps.kdlapi.com\"}")
                    .build();

        } catch (ProxyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PROXY-KDL-TUNNEL] 获取隧道代理异常, accountId={}", request.getAccountId(), e);
            throw new ProxyException("KDL_TUNNEL_EXCEPTION",
                    "快代理隧道代理异常: " + e.getMessage(),
                    e, ProviderType.KUAILAILI_TUNNEL, true);
        }
    }

    @Override
    public void release(ProxyInfo proxy) {
        log.debug("[PROXY-KDL-TUNNEL] 释放隧道代理（无操作）, accountId={}", proxy.getBoundAccountId());
    }

    @Override
    public String queryBalance() {
        return "隧道代理按请求计费，余额请到快代理后台查询";
    }

    @Override
    public String getProviderName() {
        return "KuaidailiTunnel(快代理-隧道代理)";
    }

    /**
     * 判断错误码是否可重试。
     */
    private boolean isRetryable(int code) {
        // 2=订单过期, 3=暂无可用代理, 4=未实名认证, -5=不能提取隧道代理 → 不可重试
        // -1=无效请求, -3=参数错误, -4=提取失败 → 可重试
        return code != 2 && code != 3 && code != 4 && code != -5
                && code != -11 && code != -12 && code != -13 && code != -14 && code != -15 && code != -16;
    }
}
