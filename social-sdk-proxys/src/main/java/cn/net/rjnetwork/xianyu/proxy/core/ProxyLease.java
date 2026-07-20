package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import lombok.Getter;

/**
 * 代理租约。从 {@link ProxyPoolManager#acquire(ProxyAcquireRequest)} 获取，
 * 使用完毕后必须调用 {@link #release()} 归还。
 *
 * <p>推荐用法：</p>
 * <pre>{@code
 * try (ProxyLease lease = pool.acquire(request)) {
 *     ProxyInfo proxy = lease.getProxy();
 *     // 用 proxy 去发 HTTP 请求、跑 CDP 滑块...
 * }</pre>
 *
 * <p>不可重入。同一个 lease 不能并发调用业务，否则代理 IP 被并发操作的风险。</p>
 */
@Getter
public class ProxyLease implements AutoCloseable {

    /** 租约唯一 ID */
    private final String leaseId;

    /** 分配的代理信息 */
    private final ProxyInfo proxy;

    /** 归还回调，由 ProxyPoolManager 注入 */
    private final Runnable releaseCallback;

    /** 是否已释放 */
    private volatile boolean released = false;

    public ProxyLease(String leaseId, ProxyInfo proxy, Runnable releaseCallback) {
        this.leaseId = leaseId;
        this.proxy = proxy;
        this.releaseCallback = releaseCallback;
        this.released = false;
    }

    /**
     * 归还代理到池中。支持 try-with-resources 自动释放。
     *
     * <p>重复调用幂等，不会二次释放。</p>
     */
    @Override
    public void close() {
        release();
    }

    /**
     * 显式归还。业务代码里如果用非 try-with-resources 模式，请主动调用此方法。
     */
    public synchronized void release() {
        if (released) {
            return;
        }
        released = true;
        if (releaseCallback != null) {
            try {
                releaseCallback.run();
            } catch (Exception e) {
                // 不应抛异常到业务方，只记录
            }
        }
    }

    /**
     * 标记该代理此次使用失败，由 {@link cn.net.rjnetwork.xianyu.proxy.health.ProxyHealthChecker} 累计。
     * 连续失败达到阈值会自动打入冷名单。
     */
    public void markFailure() {
        proxy.setConsecutiveFailures(proxy.getConsecutiveFailures() + 1);
    }

    /**
     * 标记该代理此次使用成功，重置连续失败计数。
     */
    public void markSuccess() {
        proxy.setConsecutiveFailures(0);
    }

    /** 是否已释放（业务方判断 try-with-resources 外是否还能用） */
    public boolean isReleased() {
        return released;
    }
}
