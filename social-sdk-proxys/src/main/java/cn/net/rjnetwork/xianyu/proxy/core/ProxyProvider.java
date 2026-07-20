package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyType;
import cn.net.rjnetwork.xianyu.proxy.config.SessionType;

/**
 * 代理供应商适配器接口。每个代理供应商（阿布云、Smartproxy、自建等）实现此接口，
 * 由 {@link ProxyPoolManager} 统一调度。
 *
 * <p>实现类要求：</p>
 * <ul>
 *   <li>无状态：不得在实现类内缓存代理，所有状态由 ProxyPoolManager 管理</li>
 *   <li>线程安全：acquire/release 可能被多线程并发调用</li>
 *   <li>超时可控：HTTP 调用必须有超时，不能无限阻塞</li>
 * </ul>
 */
public interface ProxyProvider {

    /**
     * 分配一条代理。
     *
     * @param request 分配请求（账号 ID、期望城市、会话类型、代理类型）
     * @return 可用的代理信息，永不返回 null
     * @throws ProxyException 无可用代理或供应商接口异常
     */
    ProxyInfo acquire(ProxyAcquireRequest request) throws ProxyException;

    /**
     * 释放一条代理（归还到供应商池 / 标记为空闲）。
     *
     * @param proxy 要释放的代理
     */
    void release(ProxyInfo proxy);

    /**
     * 查询当前供应商剩余配额（余额 / 流量 / IP 数）。
     *
     * @return 配额字符串，各供应商格式不同（如 "剩余 5.2 GB"、"今日剩余 1200 次"）
     */
    String queryBalance();

    /**
     * 获取该供应商的名称。
     */
    default String getProviderName() {
        return getClass().getSimpleName();
    }
}
