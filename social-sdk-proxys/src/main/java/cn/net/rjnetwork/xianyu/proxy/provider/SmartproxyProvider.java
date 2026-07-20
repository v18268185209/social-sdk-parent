package cn.net.rjnetwork.xianyu.proxy.provider;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import cn.net.rjnetwork.xianyu.proxy.util.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Smartproxy 适配器 (smartproxy.com)
 *
 * <p>国际住宅代理，按流量计费。API 直接返回隧道入口地址，支持城市级定位。</p>
 *
 * <h3>application.yml 配置示例</h3>
 * <pre>{@code
 * proxy:
 *   providers:
 *     smartproxy:
 *       enabled: true
 *       username: your_username
 *       password: your_password
 *       host: gate.smartproxy.com
 *       port: 7000
 *       # 城市代码，如 Shanghai
 *       city: Shanghai
 * }</pre>
 *
 * <h3>API 规范</h3>
 * <p>Smartproxy 提供 SOCKS5 和 HTTP 两种隧道入口，本实现默认 HTTP。</p>
 */
public class SmartproxyProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(SmartproxyProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final String city;
    private final AtomicLong acquireCount = new AtomicLong(0);

    public SmartproxyProvider(String username, String password, String host, int port, String city) {
        this.username = username;
        this.password = password;
        this.host = host != null ? host : "gate.smartproxy.com";
        this.port = port > 0 ? port : 7000;
        this.city = city;
    }

    public SmartproxyProvider(SmartproxyConfig config) {
        this(config.getUsername(), config.getPassword(), config.getHost(), config.getPort(), config.getCity());
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) {
        long count = acquireCount.incrementAndGet();
        log.debug("[SMARTPROXY] acquire request #{}, accountId={}", count, request.getAccountId());

        // Smartproxy：隧道入口固定，通过用户名控制 IP 类型 / 城市 / 会话粘性
        String resolvedUsername = resolveUsername(request, count);

        return ProxyInfo.builder()
                .providerType(ProviderType.SMARTPROXY)
                .proxyType(ProxyType.RESIDENTIAL)
                .host(host)
                .port(port)
                .username(resolvedUsername)
                .password(password)
                .sessionType(request.getSessionType())
                .boundAccountId(request.getAccountId())
                .acquiredAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusMinutes(request.getStickyDurationMinutes()))
                .captchaPassed(false)
                .consecutiveFailures(0)
                .metadata("{\"provider\":\"smartproxy\",\"session\":" + count + ",\"city\":\"" + city + "\"}")
                .build();
    }

    @Override
    public void release(ProxyInfo proxy) {
        log.debug("[SMARTPROXY] release proxy, accountId={}", proxy.getBoundAccountId());
    }

    @Override
    public String queryBalance() {
        try {
            // Smartproxy 开放 API：Get Traffic Balance（实际以官方为准）
            String url = "https://api.smartproxy.com/v1/traffic?username=" + username + "&password=" + password;
            String resp = HttpUtils.get(url);
            JsonNode node = MAPPER.readTree(resp);
            if (node.has("traffic")) {
                return "剩余流量: " + node.get("traffic").asLong() + " bytes";
            }
            return "查询结果: " + resp.substring(0, Math.min(200, resp.length()));
        } catch (Exception e) {
            log.warn("[SMARTPROXY] queryBalance failed: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }

    @Override
    public String getProviderName() {
        return "Smartproxy";
    }

    /**
     * 解析用户名。Smartproxy 支持在用户名里附加城市、会话 ID、IP 类型标识。
     *
     * <p>格式：{@code username-city-{city}-session-{sessionId}-type-res}</p>
     */
    protected String resolveUsername(ProxyAcquireRequest request, long count) {
        StringBuilder sb = new StringBuilder(username);

        String effectiveCity = request.getPreferredCity() != null && !request.getPreferredCity().isBlank()
                ? request.getPreferredCity() : (city != null ? city : "");
        if (!effectiveCity.isBlank()) {
            sb.append("-city-").append(effectiveCity);
        }

        if (request.getSessionType() == SessionType.STICKY) {
            sb.append("-session-").append(request.getAccountId()).append("-").append(count);
        }

        if (request.getProxyType() == ProxyType.MOBILE_4G) {
            sb.append("-type-mobile");
        } else if (request.getProxyType() == ProxyType.DATACENTER) {
            sb.append("-type-dc");
        } else {
            sb.append("-type-res");
        }

        return sb.toString();
    }

    /**
     * Smartproxy 配置属性。
     */
    public static class SmartproxyConfig {
        private boolean enabled = true;
        private String username;
        private String password;
        private String host = "gate.smartproxy.com";
        private int port = 7000;
        private String city;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }
}
