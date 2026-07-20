package cn.net.rjnetwork.xianyu.proxy.provider;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import cn.net.rjnetwork.xianyu.proxy.util.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 快代理适配器 (kuaidaili.com)
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li><b>隧道代理</b>（推荐）：用户名密码认证，API 拉 IP 列表后拼出隧道入口</li>
 *   <li><b>独享 IP</b>：API 返回 host:port，定时提取</li>
 * </ul>
 *
 * <h3>application.yml 配置示例</h3>
 * <pre>{@code
 * proxy:
 *   providers:
 *     kuaidaili:
 *       enabled: true
 *       orderId: your_order_id
 *       apiKey: your_api_key
 *       # 隧道模式
 *       tunnelHost:{tunnel.tql.kuaidaili.com}
 *       tunnelPort: 15818
 * }</pre>
 */
public class KuaidailiProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(KuaidailiProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 隧道代理入口（优先使用，与阿布云类似） */
    private final String tunnelHost;
    private final int tunnelPort;

    /** API 凭证 */
    private final String orderId;
    private final String apiKey;
    private final AtomicLong acquireCount = new AtomicLong(0);

    public KuaidailiProvider(String orderId, String apiKey, String tunnelHost, int tunnelPort) {
        this.orderId = orderId;
        this.apiKey = apiKey;
        this.tunnelHost = tunnelHost;
        this.tunnelPort = tunnelPort > 0 ? tunnelPort : 15818;
    }

    public KuaidailiProvider(KuaidailiConfig config) {
        this(config.getOrderId(), config.getApiKey(), config.getTunnelHost(), config.getTunnelPort());
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) {
        long count = acquireCount.incrementAndGet();
        log.debug("[KUAILAILI] acquire request #{}, accountId={}", count, request.getAccountId());

        // 隧道代理模式：直接返回隧道入口
        if (tunnelHost != null && !tunnelHost.isBlank()) {
            return ProxyInfo.builder()
                    .providerType(ProviderType.KUAILAILI)
                    .proxyType(ProxyType.RESIDENTIAL)
                    .host(tunnelHost)
                    .port(tunnelPort)
                    .username(orderId)
                    .password(resolvePassword(request, count))
                    .sessionType(request.getSessionType())
                    .boundAccountId(request.getAccountId())
                    .acquiredAt(LocalDateTime.now())
                    .expireAt(LocalDateTime.now().plusMinutes(request.getStickyDurationMinutes()))
                    .captchaPassed(false)
                    .consecutiveFailures(0)
                    .metadata("{\"provider\":\"kuaidaili\",\"session\":" + count + "}")
                    .build();
        }

        // 独享 IP 模式：调 API 拉一条独享 IP
        return fetchExclusiveIp(request, count);
    }

    @Override
    public void release(ProxyInfo proxy) {
        log.debug("[KUAILAILI] release proxy, accountId={}", proxy.getBoundAccountId());
        // 快代理独显 IP 模式下如果需要显式释放，调用 API 删除白名单
    }

    @Override
    public String queryBalance() {
        try {
            // 快代理开放 API：Get Balance（实际以官方文档为准）
            String url = "http://www.kuaidaili.com/api/getbalance?orderId=" + orderId + "&apiKey=" + apiKey;
            String resp = HttpUtils.get(url);
            JsonNode node = MAPPER.readTree(resp);
            if (node.has("data") && node.get("data").has("balance")) {
                return "余额: " + node.get("data").get("balance").asDouble() + " 元";
            }
            return "查询结果: " + resp.substring(0, Math.min(200, resp.length()));
        } catch (Exception e) {
            log.warn("[KUAILAILI] queryBalance failed: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }

    @Override
    public String getProviderName() {
        return "Kuaidaili(快代理)";
    }

    /**
     * 解析隧道密码。快代理支持在密码里附加订单号、会话 ID 等。
     */
    protected String resolvePassword(ProxyAcquireRequest request, long count) {
        StringBuilder sb = new StringBuilder();
        sb.append(apiKey);
        if (request.getPreferredCity() != null && !request.getPreferredCity().isBlank()) {
            sb.append("&city=").append(request.getPreferredCity());
        }
        if (request.getSessionType() == SessionType.STICKY) {
            sb.append("&session=").append(request.getAccountId()).append("_").append(count);
        }
        return sb.toString();
    }

    /**
     * 独享 IP 模式：调 API 拉一条独享 IP。
     */
    private ProxyInfo fetchExclusiveIp(ProxyAcquireRequest request, long count) {
        try {
            // 快代理独享 IP API（示意，实际以官方为准）
            String url = "http://www.kuaidaili.com/api/getProxy?orderId=" + orderId
                    + "&apiKey=" + apiKey
                    + "&count=1"
                    + "&format=json";
            String resp = HttpUtils.get(url);
            JsonNode node = MAPPER.readTree(resp);

            if (node.has("code") && node.get("code").asInt() != 0) {
                throw new cn.net.rjnetwork.xianyu.proxy.core.ProxyException(
                        "KUAILAILI_API_ERROR", node.has("msg") ? node.get("msg").asText() : "API 错误");
            }

            JsonNode data = node.get("data");
            JsonNode first = data.get("proxy_list").get(0);
            String[] hostPort = first.asText().split(":");

            return ProxyInfo.builder()
                    .providerType(ProviderType.KUAILAILI)
                    .proxyType(ProxyType.RESIDENTIAL)
                    .host(hostPort[0])
                    .port(Integer.parseInt(hostPort[1]))
                    .sessionType(request.getSessionType())
                    .boundAccountId(request.getAccountId())
                    .acquiredAt(LocalDateTime.now())
                    .expireAt(LocalDateTime.now().plusMinutes(request.getStickyDurationMinutes()))
                    .captchaPassed(false)
                    .consecutiveFailures(0)
                    .metadata("{\"provider\":\"kuaidaili-exclusive\",\"session\":" + count + "}")
                    .build();
        } catch (ProxyException e) {
            throw e;
        } catch (Exception e) {
            throw new cn.net.rjnetwork.xianyu.proxy.core.ProxyException(
                    "FETCH_EXCLUSIVE_IP_ERROR", "获取独享 IP 失败: " + e.getMessage(), e, ProviderType.KUAILAILI, true);
        }
    }

    /**
     * 快代理配置属性。
     */
    public static class KuaidailiConfig {
        private boolean enabled = true;
        private String orderId;
        private String apiKey;
        private String tunnelHost = "tunnel.kuaidaili.com";
        private int tunnelPort = 15818;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getTunnelHost() { return tunnelHost; }
        public void setTunnelHost(String tunnelHost) { this.tunnelHost = tunnelHost; }
        public int getTunnelPort() { return tunnelPort; }
        public void setTunnelPort(int tunnelPort) { this.tunnelPort = tunnelPort; }
    }
}
