package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 代理池管理器。业务方唯一入口，屏蔽供应商差异。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>按策略选择供应商（优先级 / 轮询 / 最少使用 / 最低成本）</li>
 *   <li>管理账号-IP 绑定关系（同一账号优先复用同一 IP）</li>
 *   <li>自动归还过期租约</li>
 *   <li>触发健康检查，冷名单管理</li>
 * </ul>
 *
 * <p>推荐用法：</p>
 * <pre>{@code
 * try (ProxyLease lease = pool.acquire(ProxyAcquireRequest.defaultRequest(accountId))) {
 *     ProxyInfo proxy = lease.getProxy();
 *     // 用 proxy 去发 HTTP 请求、跑 CDP 滑块...
 *     lease.markSuccess();
 * }</pre>
 */
public interface ProxyPoolManager {

    /**
     * 获取一条代理。优先使用账号已绑定的 IP，无绑定则按策略选供应商。
     *
     * @param request 代理分配请求
     * @return 代理租约，业务方负责归还
     * @throws ProxyException 无可用的代理
     */
    ProxyLease acquire(ProxyAcquireRequest request) throws ProxyException;

    /**
     * 手动归还代理（{@link ProxyLease#release()} 的托管版本，用于非 try-with-resources 场景）。
     *
     * @param leaseId 租约 ID
     */
    void release(String leaseId);

    /**
     * 查询当前账号绑定的代理（如有）。
     *
     * @param accountId 账号 ID
     * @return 绑定的代理，未绑定返回 empty
     */
    Optional<ProxyInfo> findBoundProxy(Long accountId);

    /**
     * 手动解绑账号的代理（运营商 IP 被封 / IP 失效时调用）。
     *
     * @param accountId 账号 ID
     */
    void unbind(Long accountId);

    /**
     * 手动标记某代理失败一次（健康检查累计失败会触发冷名单）。
     *
     * @param proxy 代理信息
     * @param reason 失败原因（日志用）
     */
    void markFailure(ProxyInfo proxy, String reason);

    /**
     * 触发一次全量健康检查（通常由定时任务调用，业务方可手动触发）。
     */
    void runHealthCheck();

    /**
     * 获取所有供应商余额/配额信息。
     */
    Map<ProviderType, String> queryAllBalances();

    /**
     * 获取当前在用的租约列表（LEASE ID -> PROXY）。
     */
    Map<String, ProxyInfo> listActiveLeases();

    /**
     * 获取所有账号-IP 绑定关系（账号 ID -> PROXY）。
     */
    Map<Long, ProxyInfo> listBindings();

    /**
     * 获取冷名单中的代理（连续失败次数超过阈值的代理）。
     */
    List<ProxyInfo> listCoolingDownProxies();

    /**
     * 注册一个自定义供应商。
     *
     * @param providerType 供应商类型（推荐用 {@link ProviderType#CUSTOM}）
     * @param provider     供应商实现
     */
    void registerProvider(ProviderType providerType, ProxyProvider provider);

    /**
     * 移除一个已注册的供应商（正在使用的租约不会被强制回收，新请求不再路由到它）。
     *
     * @param providerType 供应商类型
     */
    void unregisterProvider(ProviderType providerType);

    /**
     * 返回当前已注册的供应商类型列表。
     */
    List<ProviderType> listRegisteredProviders();

    /**
     * 设置健康检查调度器（由 Spring 配置注入 ScheduledExecutorService）。
     */
    void setHealthCheckScheduler(ScheduledExecutorService scheduler);

    /**
     * 设置健康检查器（用于自定义检查逻辑）。
     */
    void setHealthChecker(ProxyHealthChecker healthChecker);

    /**
     * 设置账号-IP 绑定策略。
     * true（默认）= 优先复用已绑定的 IP；false = 每次都换新 IP（不推荐，会触发风控）。
     */
    void setReuseBoundIp(boolean reuseBoundIp);
}
