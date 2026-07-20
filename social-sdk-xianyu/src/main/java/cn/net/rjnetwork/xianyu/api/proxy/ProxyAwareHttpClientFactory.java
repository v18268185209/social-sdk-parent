package cn.net.rjnetwork.xianyu.api.proxy;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyLease;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager;

import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Authenticator;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 代理感知的 HTTP 客户端工厂。根据账号从代理池获取代理，构造带代理认证的 HttpClient。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * ProxyAwareHttpClientFactory factory = new ProxyAwareHttpClientFactory(poolManager, accountId);
 * HttpClient client = factory.createClient(30, TimeUnit.SECONDS);
 * // 使用 client 发请求，会自动走代理
 * }</pre>
 */
public class ProxyAwareHttpClientFactory {

    private final ProxyPoolManager poolManager;
    private final Long accountId;

    /**
     * @param poolManager 代理池管理器
     * @param accountId   当前请求的账号 ID，用于从代理池获取对应的代理
     */
    public ProxyAwareHttpClientFactory(ProxyPoolManager poolManager, Long accountId) {
        this.poolManager = poolManager;
        this.accountId = accountId;
    }

    /**
     * 根据账号绑定的代理，构造一个带代理的 HttpClient。
     *
     * @param timeout  连接超时
     * @param timeUnit 超时单位
     * @return 代理感知的 HttpClient
     */
    public HttpClient createClient(long timeout, java.util.concurrent.TimeUnit timeUnit) {
        return createClient(timeout, timeUnit, null);
    }

    /**
     * 根据账号绑定的代理，构造带代理的 HttpClient（绑定账号）。
     *
     * @param timeout  连接超时
     * @param timeUnit 超时单位
     * @param executor 线程池（可为 null）
     * @return 代理感知的 HttpClient
     */
    public HttpClient createClient(long timeout, java.util.concurrent.TimeUnit timeUnit, java.util.concurrent.Executor executor) {
        if (poolManager == null || accountId == null) {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeUnit.toMillis(timeout)))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        ProxyInfo proxy = poolManager.findBoundProxy(accountId).orElse(null);
        if (proxy == null) {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeUnit.toMillis(timeout)))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeUnit.toMillis(timeout)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(java.net.ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));

        if (proxy.getUsername() != null && !proxy.getUsername().isBlank()) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            proxy.getUsername(),
                            (proxy.getPassword() != null ? proxy.getPassword() : "").toCharArray()
                    );
                }
            });
        }

        if (executor != null) {
            builder.executor(executor);
        }

        return builder.build();
    }
}
