package cn.net.rjnetwork.xianyu.proxy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 代理 IP 信息。一个实例代表一条可使用的代理通道。
 *
 * <p>三种形态：</p>
 * <ul>
 *   <li><b>直连模式</b>：{@link #isDirect()} = true，不使用代理，适用于本地开发 / 无代理配置</li>
 *   <li><b>隧道模式</b>：{@code host:port} 即代理入口，供应商后端自动轮换出口 IP</li>
 *   <li><b>独享模式</b>：{@code username:password@host:port}，IP 固定</li>
 * </ul>
 *
 * <h3>直连模式的使用场景</h3>
 * <p>当业务方未配置任何供应商，或者明确想绕过代理直连时，
 * {@link cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager#acquire(ProxyAcquireRequest)} 会返回一个特殊的 ProxyInfo，
 * {@link #isDirect()} = true。业务方无需 if/else 判断，可以直接传给 HTTP 客户端：</p>
 * <pre>{@code
 * ProxyInfo proxy = lease.getProxy();
 * if (proxy.isDirect()) {
 *     // 直连，不走代理
 *     client = HttpClient.newBuilder().build();
 * } else {
 *     // 走代理
 *     client = HttpClient.newBuilder()
 *         .proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())))
 *         .build();
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyInfo {

    /** 直连模式的特殊 host 标记（{@code "DIRECT"}），表示不使用代理 */
    public static final String DIRECT_HOST = "DIRECT";

    /** 代理供应商 */
    private ProviderType providerType;

    /** 代理类型（住宅 / 4G / 数据中心） */
    private ProxyType proxyType;

    /** 代理主机地址（IP 或域名）。直连模式下为 {@link #DIRECT_HOST} */
    private String host;

    /** 代理端口。直连模式下为 0 */
    private int port;

    /** 认证用户名（如有） */
    private String username;

    /** 认证密码（如有） */
    private String password;

    /** 出口 IP 地址（健康检查时填充，可能因轮换与预期不同） */
    private String exitIp;

    /** 出口 IP 所在城市（如 "上海"、"北京"） */
    private String city;

    /** 出口 IP 所属运营商（如 "中国电信"） */
    private String isp;

    /** 会话粘性类型 */
    private SessionType sessionType;

    /** 该代理被分配的闲鱼账号 ID（绑定后不为空，用于账号-IP 一对一审计） */
    private Long boundAccountId;

    /** 获取该代理的时间戳 */
    private LocalDateTime acquiredAt;

    /** 预计释放时间（粘性会话必须设，到点未释放触发告警） */
    private LocalDateTime expireAt;

    /** 该 IP 是否已通过闲鱼滑块验证（true = 已经过滑块，cookie 已落盘） */
    private boolean captchaPassed;

    /** 连续失败次数（超过阈值自动淘汰到冷名单） */
    private int consecutiveFailures;

    /** 元数据（供应商自定义字段，JSON 字符串） */
    private String metadata;

    /** 获取代理 URI，格式：scheme://[user:pass@]host:port */
    public String toProxyUri(String scheme) {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme == null ? "http" : scheme).append("://");
        if (username != null && !username.isBlank()) {
            sb.append(username);
            if (password != null && !password.isBlank()) {
                sb.append(':').append(password);
            }
            sb.append('@');
        }
        sb.append(host).append(':').append(port);
        return sb.toString();
    }

    /** 简易 URI，scheme=http */
    public String toProxyUri() {
        return toProxyUri("http");
    }

    /** 是否已过期 */
    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }

    /** 是否处于冷却期（连续失败 >= 3 次自动进入冷却） */
    public boolean isCoolingDown() {
        return consecutiveFailures >= 3;
    }

    /** 是否已绑定账号 */
    public boolean isBound() {
        return boundAccountId != null;
    }

    /**
     * 是否为直连模式（无代理）。当 host = {@link #DIRECT_HOST} 时表示不使用代理。
     *
     * <p>业务方看到此标记时，可以直接构造不带代理的 HttpClient。</p>
     */
    public boolean isDirect() {
        return DIRECT_HOST.equals(host);
    }

    /**
     * 创建直连模式的 ProxyInfo 单例（host=DIRECT, port=0）。
     *
     * <p>用于 {@link cn.net.rjnetwork.xianyu.proxy.core.DefaultProxyPoolManager} 无可用供应商时的兜底分配。
     * 这样业务方调用 acquire 永远不抛异常，只是拿到一个"不走代理"的 lease。</p>
     */
    public static ProxyInfo direct() {
        return ProxyInfo.builder()
                .host(DIRECT_HOST)
                .port(0)
                .sessionType(SessionType.ROTATING)
                .consecutiveFailures(0)
                .build();
    }
}
