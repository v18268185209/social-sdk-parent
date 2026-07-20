package cn.net.rjnetwork.xianyu.proxy.provider;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
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
 * 阿布云代理适配器 (abuyun.com)
 *
 * <p>接入方式：HTTP 隧道代理。提供用户名 + 密码认证，后端自动轮换 IP。</p>
 *
 * <h3>application.yml 配置示例</h3>
 * <pre>{@code
 * proxy:
 *   providers:
 *     abuyun:
 *       enabled: true
 *       username: your_username
 *       password: your_password
 *       host: http-dyn.abuyun.com
 *       port: 9020
 *       # city: 上海  # 可选，部分套餐支持城市定向
 * }</pre>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * ProxyProvider provider = new AbuyunProvider(config);
 * ProxyInfo proxy = provider.acquire(ProxyAcquireRequest.defaultRequest(accountId));
 * // 使用 proxy...
 * provider.release(proxy);
 * }</pre>
 */
public class AbuyunProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(AbuyunProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final AtomicLong acquireCount = new AtomicLong(0);

    public AbuyunProvider(String username, String password, String host, int port) {
        this.username = username;
        this.password = password;
        this.host = host != null ? host : "http-dyn.abuyun.com";
        this.port = port > 0 ? port : 9020;
    }

    /**
     * 从 Spring 配置对象初始化（推荐）。
     */
    public AbuyunProvider(AbuyunConfig config) {
        this(config.getUsername(), config.getPassword(), config.getHost(), config.getPort());
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) {
        long count = acquireCount.incrementAndGet();
        log.debug("[ABUYUN] acquire request #{}, accountId={}", count, request.getAccountId());

        // 阿布云隧道模式：用户名里可带城市 / 会话粘性标识
        // 格式：APPXXXXXXXXX-zone-{zone}-city-{city}-session-{sessionId}
        String resolvedUsername = resolveUsernameForRequest(request, count);

        return ProxyInfo.builder()
                .providerType(ProviderType.ABUYUN)
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
                .metadata("{\"provider\":\"abuyun\",\"session\":" + count + "}")
                .build();
    }

    @Override
    public void release(ProxyInfo proxy) {
        // 阿布云隧道模式无需显式释放，下次请求自动换 IP
        log.debug("[ABUYUN] release proxy, accountId={}", proxy.getBoundAccountId());
    }

    @Override
    public String queryBalance() {
        try {
            // 阿布云开放 API：Get Balance（实际以官方文档为准，这里为示意）
            String url = "https://api-abuyun.com/user/getBalance?appKey=" + username;
            String resp = HttpUtils.get(url);
            JsonNode node = MAPPER.readTree(resp);
            if (node.has("balance")) {
                return "余额: " + node.get("balance").asDouble() + " 元";
            }
            return "余额查询成功: " + resp.substring(0, Math.min(200, resp.length()));
        } catch (Exception e) {
            log.warn("[ABUYUN] queryBalance failed: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }

    @Override
    public String getProviderName() {
        return "Abuyun(阿布云)";
    }

    /**
     * 根据请求参数解析用户名。
     *
     * <p>阿布云支持在用户名里附加 zone、city、session 等标识。带粘性会话需求时，
     * 把 sessionId 拼入用户名，阿布云后端会保持会话期间 IP 不变。</p>
     */
    protected String resolveUsernameForRequest(ProxyAcquireRequest request, long count) {
        StringBuilder sb = new StringBuilder(username);
        if (request.getPreferredCity() != null && !request.getPreferredCity().isBlank()) {
            sb.append("-city-").append(request.getPreferredCity());
        }
        if (request.getSessionType() == SessionType.STICKY) {
            sb.append("-session-").append(request.getAccountId()).append("-").append(count);
        }
        return sb.toString();
    }

    /**
     * 阿布云配置属性（Spring Boot ConfigurationProperties 注入）。
     */
    public static class AbuyunConfig {
        private boolean enabled = true;
        private String username;
        private String password;
        private String host = "http-dyn.abuyun.com";
        private int port = 9020;

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
    }
}
