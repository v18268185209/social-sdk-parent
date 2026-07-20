package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.health.DefaultHealthChecker;
import cn.net.rjnetwork.xianyu.proxy.health.ProxyHealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * {@link ProxyPoolManager} 默认实现。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>管理多个 {@link ProxyProvider}，按优先级分配</li>
 *   <li>维护账号-IP 绑定关系（同一账号优先复用已绑定的 IP）</li>
 *   <li>管理租约生命周期（acquire / release / 泄露检测）</li>
 *   <li>冷名单机制（连续失败达到阈值自动打入冷却 / 定期复原）</li>
 *   <li>触发健康检查（定时 + 按需）</li>
 * </ul>
 *
 * <h3>分配策略</h3>
 * <ol>
 *   <li>如果账号已有绑定且绑定有效（未过期、未超过最大使用次数）→ 直接复用</li>
 *   <li>否则找优先级最高的可用供应商 acquire 一条</li>
 *   <li>如果首家供应商失败 → 重试下一家（重试次数由配置决定）</li>
 *   <li>全部失败 → 尝试分配冷名单中的 IP（最后兜底）</li>
 *   <li>仍失败 → 抛出 {@link ProxyException#noAvailableProxy()}</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <p>本类线程安全。所有共享状态用 {@link ConcurrentHashMap} 和 {@link java.util.concurrent.atomic} 保护。</p>
 */
public class DefaultProxyPoolManager implements ProxyPoolManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultProxyPoolManager.class);

    // ==================== 配置 ====================

    /** 供应商列表，按优先级排序（index 0 = 最高优先级） */
    private final List<Map.Entry<ProviderType, ProxyProvider>> providers = new CopyOnWriteArrayList<>();

    /** 健康检查器 */
    private volatile ProxyHealthChecker healthChecker = new DefaultHealthChecker();

    /** 任务调度器（定时健康检查 / 冷名单复原 / 泄露检测） */
    private volatile TaskScheduler taskScheduler;

    /** 是否复用已绑定的 IP */
    private volatile boolean reuseBoundIp = true;

    /** 一条绑定的最大使用次数 */
    private volatile int maxBindingUseCount = 100;

    /** 冷名单自动复原间隔（分钟） */
    private volatile int coolDownRecoveryMinutes = 30;

    /** 泄露检测：租约 acquire 多久没 release 算泄露（分钟） */
    private volatile int leaseLeakThresholdMinutes = 60;

    /** 绑定持久化回调（由 BindingPersistenceAutoConfiguration 注入） */
    private volatile BindingPersistenceCallback bindingPersistenceCallback;

    // ==================== 持久化回调接口 ====================

    /**
     * 绑定持久化回调接口。当内存中的绑定发生变化（unbind）时，
     * 默认实现同步更新{@code proxy_account_binding}表。
     */
    public interface BindingPersistenceCallback {
        void onUnbind(Long accountId);
    }

    /** 设置持久化回调（通常由 BindingPersistenceAutoConfiguration 注入） */
    public void setBindingPersistenceCallback(BindingPersistenceCallback callback) {
        this.bindingPersistenceCallback = callback;
    }

    // ==================== 运行时状态 ====================

    /** 活跃租约（leaseId -> LeaseHolder） */
    private final ConcurrentHashMap<String, LeaseHolder> activeLeases = new ConcurrentHashMap<>();

    /** 账号-IP 绑定（accountId -> BindingInfo） */
    private final ConcurrentHashMap<Long, BindingInfo> bindings = new ConcurrentHashMap<>();

    /** 冷名单（providerType:ip -> CoolDownInfo） */
    private final ConcurrentHashMap<String, CoolDownInfo> coolingDown = new ConcurrentHashMap<>();

    /** 租约 ID 生成器 */
    private final AtomicLong leaseIdGen = new AtomicLong(0);

    /** 分配总次数（指标用） */
    private final AtomicLong totalAcquireCount = new AtomicLong(0);

    /** 分配失败总次数（指标用） */
    private final AtomicLong totalAcquireFailCount = new AtomicLong(0);

    // ==================== 内部对象 ====================

    /** 租约持有者 */
    private static class LeaseHolder {
        final String leaseId;
        final ProxyInfo proxy;
        final Long accountId;
        final LocalDateTime acquiredAt;
        final LocalDateTime expireAt;
        final Runnable releaseCallback;
        volatile boolean released;

        LeaseHolder(String leaseId, ProxyInfo proxy, Long accountId,
                    LocalDateTime acquiredAt, LocalDateTime expireAt, Runnable releaseCallback) {
            this.leaseId = leaseId;
            this.proxy = proxy;
            this.accountId = accountId;
            this.acquiredAt = acquiredAt;
            this.expireAt = expireAt;
            this.releaseCallback = releaseCallback;
            this.released = false;
        }

        boolean isExpired() {
            return LocalDateTime.now().isAfter(expireAt);
        }
    }

    /** 绑定信息 */
    private static class BindingInfo {
        final Long accountId;
        final ProxyInfo proxy;
        final LocalDateTime boundAt;
        volatile int useCount;
        volatile long lastUsedAt;

        BindingInfo(Long accountId, ProxyInfo proxy) {
            this.accountId = accountId;
            this.proxy = proxy;
            this.boundAt = LocalDateTime.now();
            this.useCount = 0;
            this.lastUsedAt = System.currentTimeMillis();
        }

        boolean isExpired(int maxUseCount, int stickyValidMinutes) {
            if (useCount >= maxUseCount) return true;
            return LocalDateTime.now().isAfter(boundAt.plusMinutes(stickyValidMinutes));
        }

        void recordUse() {
            useCount++;
            lastUsedAt = System.currentTimeMillis();
        }
    }

    /** 冷却信息 */
    private static class CoolDownInfo {
        final String ip;
        final ProviderType providerType;
        final int consecutiveFailures;
        final LocalDateTime cooledDownAt;

        CoolDownInfo(String ip, ProviderType providerType, int consecutiveFailures) {
            this.ip = ip;
            this.providerType = providerType;
            this.consecutiveFailures = consecutiveFailures;
            this.cooledDownAt = LocalDateTime.now();
        }

        boolean shouldRecover(int coolDownRecoveryMinutes) {
            return LocalDateTime.now().isAfter(cooledDownAt.plusMinutes(coolDownRecoveryMinutes));
        }
    }

    // ==================== 构造函数 ====================

    public DefaultProxyPoolManager() {
    }

    public DefaultProxyPoolManager(ProxyHealthChecker healthChecker) {
        this.healthChecker = healthChecker != null ? healthChecker : new DefaultHealthChecker();
    }

    // ==================== 核心实现：acquire ====================

    @Override
    public ProxyLease acquire(ProxyAcquireRequest request) throws ProxyException {
        if (request == null || request.getAccountId() == null) {
            throw new ProxyException("INVALID_REQUEST", "acquire 请求必须包含 accountId", null, null, false);
        }

        totalAcquireCount.incrementAndGet();
        Long accountId = request.getAccountId();

        // 1. 尝试复用已绑定的 IP
        if (reuseBoundIp) {
            BindingInfo binding = bindings.get(accountId);
            if (binding != null && !binding.isExpired(maxBindingUseCount, request.getStickyDurationMinutes() * 2)) {
                ProxyInfo boundProxy = binding.proxy;
                // 检查冷名单
                if (!isCoolingDown(boundProxy)) {
                    binding.recordUse();
                    log.debug("[POOL] 复用绑定 IP, accountId={}, ip={}, useCount={}", accountId, boundProxy.getHost(), binding.useCount);
                    return createLease(boundProxy, accountId);
                }
            }
        }

        // 2. 按供应商优先级逐个尝试
        List<Exception> errors = new ArrayList<>();
        for (Map.Entry<ProviderType, ProxyProvider> entry : providers) {
            ProviderType providerType = entry.getKey();
            ProxyProvider provider = entry.getValue();

            try {
                ProxyInfo proxy = provider.acquire(request);
                if (proxy == null) {
                    continue;
                }

                // 健康检查（对新 acquire 的 IP）
                if (healthChecker != null) {
                    ProxyHealthChecker.HealthCheckResult result = healthChecker.check(proxy);
                    if (!result.isHealthy()) {
                        log.warn("[POOL] 新 IP 健康检查未通过, provider={}, accountId={}, reason={}",
                                providerType, accountId, result.getMessage());
                        errors.add(new ProxyException("HEALTH_CHECK_FAILED",
                                providerType + " " + result.getMessage(), null, providerType, true));
                        provider.release(proxy);
                        continue;
                    }
                }

                // 绑定关系
                bindings.put(accountId, new BindingInfo(accountId, proxy));

                log.info("[POOL] acquire 成功, accountId={}, provider={}, ip:port={}:{}",
                        accountId, providerType, proxy.getHost(), proxy.getPort());

                return createLease(proxy, accountId);

            } catch (Exception e) {
                log.warn("[POOL] acquire 失败, provider={}, accountId={}, err={}", providerType, accountId, e.getMessage());
                errors.add(e);
            }
        }

        // 3. 全部失败：尝试冷名单兜底
        ProxyInfo cooledIp = tryRecoverFromCoolDown();
        if (cooledIp != null) {
            log.warn("[POOL] 冷名单兜底分配, accountId={}, ip={}", accountId, cooledIp.getHost());
            bindings.put(accountId, new BindingInfo(accountId, cooledIp));
            return createLease(cooledIp, accountId);
        }

        totalAcquireFailCount.incrementAndGet();
        // 组装错误信息
        StringBuilder sb = new StringBuilder("所有供应商均不可用: ");
        for (int i = 0; i < errors.size(); i++) {
            sb.append(errors.get(i).getMessage());
            if (i < errors.size() - 1) sb.append("; ");
        }
        throw ProxyException.noAvailableProxy();
    }

    @Override
    public void release(String leaseId) {
        if (leaseId == null) return;
        LeaseHolder holder = activeLeases.remove(leaseId);
        if (holder == null || holder.released) return;

        holder.released = true;
        try {
            if (holder.releaseCallback != null) {
                holder.releaseCallback.run();
            }

            // 归还到供应商
            ProviderType providerType = holder.proxy.getProviderType();
            if (providerType != null) {
                providers.stream()
                        .filter(e -> e.getKey() == providerType)
                        .findFirst()
                        .ifPresent(e -> {
                            try {
                                e.getValue().release(holder.proxy);
                            } catch (Exception ex) {
                                log.warn("[POOL] release 归还供应商异常, provider={}, err={}", providerType, ex.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("[POOL] release 异常, leaseId={}", leaseId, e);
        }
    }

    // ==================== 绑定管理 ====================

    @Override
    public Optional<ProxyInfo> findBoundProxy(Long accountId) {
        if (accountId == null) return Optional.empty();
        BindingInfo binding = bindings.get(accountId);
        if (binding == null) return Optional.empty();
        return Optional.of(binding.proxy);
    }

    @Override
    public void unbind(Long accountId) {
        if (accountId == null) return;
        BindingInfo removed = bindings.remove(accountId);
        if (removed != null) {
            log.info("[POOL] 解绑账号, accountId={}, ip={}", accountId, removed.proxy.getHost());
            // 持久化回调：同步逻辑删除 DB 里的记录
            if (bindingPersistenceCallback != null) {
                try {
                    bindingPersistenceCallback.onUnbind(accountId);
                } catch (Exception e) {
                    log.warn("[POOL] unbind 持久化回调异常, accountId={}", accountId, e);
                }
            }
        }
    }

    @Override
    public void registerBinding(Long accountId, ProxyInfo proxy) {
        if (accountId == null || proxy == null) return;
        BindingInfo existing = bindings.get(accountId);
        if (existing != null) {
            log.debug("[POOL] 更新绑定, accountId={}, oldIp={}, newIp={}", accountId, existing.proxy.getHost(), proxy.getHost());
        } else {
            log.info("[POOL] 注册绑定, accountId={}, ip={}", accountId, proxy.getHost());
        }
        bindings.put(accountId, new BindingInfo(accountId, proxy));
        proxy.setBoundAccountId(accountId);
    }

    // ==================== 失败标记 & 冷名单 ====================

    @Override
    public void markFailure(ProxyInfo proxy, String reason) {
        if (proxy == null) return;
        proxy.setConsecutiveFailures(proxy.getConsecutiveFailures() + 1);

        String key = buildCoolDownKey(proxy);
        coolingDown.put(key, new CoolDownInfo(
                proxy.getHost(), proxy.getProviderType(), proxy.getConsecutiveFailures()));

        log.warn("[POOL] 代理被打入冷名单, ip={}, failures={}, reason={}",
                proxy.getHost(), proxy.getConsecutiveFailures(), reason);

        // 如果有账号绑定了这个 IP，解绑
        bindings.entrySet().removeIf(entry -> {
            if (entry.getValue().proxy.getHost().equals(proxy.getHost())) {
                log.info("[POOL] 冷名单 IP 有关联账号，自动解绑, accountId={}, ip={}",
                        entry.getKey(), proxy.getHost());
                return true;
            }
            return false;
        });
    }

    // ==================== 健康检查 ====================

    @Override
    public void runHealthCheck() {
        if (healthChecker == null) return;

        log.info("[POOL] 开始全量健康检查, bindingCount={}, coolingDownCount={}",
                bindings.size(), coolingDown.size());

        // 检查所有绑定中的 IP
        List<Long> toUnbind = new ArrayList<>();
        for (Map.Entry<Long, BindingInfo> entry : bindings.entrySet()) {
            ProxyHealthChecker.HealthCheckResult result = healthChecker.check(entry.getValue().proxy);
            if (!result.isHealthy()) {
                log.warn("[POOL] 健康检查失败，解绑, accountId={}, reason={}", entry.getKey(), result.getMessage());
                toUnbind.add(entry.getKey());
            }
        }
        toUnbind.forEach(bindings::remove);
    }

    // ==================== 余额 & 状态查询 ====================

    @Override
    public Map<ProviderType, String> queryAllBalances() {
        Map<ProviderType, String> result = new LinkedHashMap<>();
        for (Map.Entry<ProviderType, ProxyProvider> entry : providers) {
            try {
                result.put(entry.getKey(), entry.getValue().queryBalance());
            } catch (Exception e) {
                result.put(entry.getKey(), "查询失败: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<String, ProxyInfo> listActiveLeases() {
        Map<String, ProxyInfo> result = new LinkedHashMap<>();
        activeLeases.forEach((id, holder) -> result.put(id, holder.proxy));
        return result;
    }

    @Override
    public Map<Long, ProxyInfo> listBindings() {
        Map<Long, ProxyInfo> result = new LinkedHashMap<>();
        bindings.forEach((id, binding) -> result.put(id, binding.proxy));
        return result;
    }

    @Override
    public List<ProxyInfo> listCoolingDownProxies() {
        return coolingDown.values().stream()
                .map(info -> ProxyInfo.builder()
                        .host(info.ip)
                        .providerType(info.providerType)
                        .consecutiveFailures(info.consecutiveFailures)
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== 供应商管理 ====================

    @Override
    public void registerProvider(ProviderType providerType, ProxyProvider provider) {
        if (providerType == null || provider == null) return;
        // 移除已有的同类型供应商
        providers.removeIf(e -> e.getKey() == providerType);
        providers.add(new AbstractMap.SimpleEntry<>(providerType, provider));
        log.info("[POOL] 注册供应商, type={}", providerType);
    }

    @Override
    public void unregisterProvider(ProviderType providerType) {
        providers.removeIf(e -> e.getKey() == providerType);
        log.info("[POOL] 注销供应商, type={}", providerType);
    }

    @Override
    public List<ProviderType> listRegisteredProviders() {
        return providers.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    // ==================== Setter（Spring 注入）====================

    @Override
    public void setHealthCheckScheduler(ScheduledExecutorService scheduler) {
        // ScheduledExecutorService 包装为 TaskScheduler
        this.taskScheduler = scheduler != null ? new org.springframework.scheduling.concurrent.ConcurrentTaskScheduler(scheduler) : null;
        scheduleTasks();
    }

    @Override
    public void setHealthChecker(ProxyHealthChecker healthChecker) {
        this.healthChecker = healthChecker != null ? healthChecker : new DefaultHealthChecker();
    }

    @Override
    public void setReuseBoundIp(boolean reuseBoundIp) {
        this.reuseBoundIp = reuseBoundIp;
    }

    /** 设置最大绑定使用次数 */
    public void setMaxBindingUseCount(int maxBindingUseCount) {
        this.maxBindingUseCount = Math.max(1, maxBindingUseCount);
    }

    /** 设置冷名单复原间隔（分钟） */
    public void setCoolDownRecoveryMinutes(int coolDownRecoveryMinutes) {
        this.coolDownRecoveryMinutes = Math.max(1, coolDownRecoveryMinutes);
    }

    /** 设置泄露检测阈值（分钟） */
    public void setLeaseLeakThresholdMinutes(int leaseLeakThresholdMinutes) {
        this.leaseLeakThresholdMinutes = Math.max(1, leaseLeakThresholdMinutes);
    }

    // ==================== 内部方法 ====================

    private ProxyLease createLease(ProxyInfo proxy, Long accountId) {
        String leaseId = "lease-" + leaseIdGen.incrementAndGet();
        LocalDateTime acquiredAt = LocalDateTime.now();
        LocalDateTime expireAt = proxy.getExpireAt() != null ? proxy.getExpireAt()
                : acquiredAt.plusMinutes(30);

        Runnable releaseCallback = () -> release(leaseId);

        LeaseHolder holder = new LeaseHolder(leaseId, proxy, accountId, acquiredAt, expireAt, releaseCallback);
        activeLeases.put(leaseId, holder);

        return new ProxyLease(leaseId, proxy, releaseCallback);
    }

    private String buildCoolDownKey(ProxyInfo proxy) {
        return (proxy.getProviderType() != null ? proxy.getProviderType().name() : "unknown")
                + ":" + proxy.getHost();
    }

    private boolean isCoolingDown(ProxyInfo proxy) {
        return coolingDown.containsKey(buildCoolDownKey(proxy));
    }

    private ProxyInfo tryRecoverFromCoolDown() {
        Iterator<Map.Entry<String, CoolDownInfo>> it = coolingDown.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CoolDownInfo> entry = it.next();
            CoolDownInfo info = entry.getValue();
            if (info.shouldRecover(coolDownRecoveryMinutes)) {
                it.remove();
                log.info("[POOL] 冷名单 IP 复原, ip={}", info.ip);
                return ProxyInfo.builder()
                        .host(info.ip)
                        .providerType(info.providerType)
                        .consecutiveFailures(0)
                        .build();
            }
        }
        return null;
    }

    /**
     * 定时任务注册：健康检查 / 冷名单复原 / 泄露检测。
     */
    private void scheduleTasks() {
        if (taskScheduler == null) return;

        // 每 5 分钟运行一次健康检查
        taskScheduler.scheduleAtFixedRate(this::runHealthCheck, 5 * 60 * 1000);

        // 每 10 分钟检查冷名单复原
        taskScheduler.scheduleAtFixedRate(() -> {
            long recovered = coolingDown.values().stream()
                    .filter(info -> info.shouldRecover(coolDownRecoveryMinutes))
                    .count();
            if (recovered > 0) {
                coolingDown.entrySet().removeIf(e -> e.getValue().shouldRecover(coolDownRecoveryMinutes));
                log.info("[POOL] 冷名单批量复原, 复原数量={}", recovered);
            }
        }, 10 * 60 * 1000);

        // 每 30 分钟检测租约泄露
        taskScheduler.scheduleAtFixedRate(this::detectLeaseLeaks, 30 * 60 * 1000);
    }

    /**
     * 租约泄露检测：acquire 了很久还没 release 的租约视为泄露。
     */
    private void detectLeaseLeaks() {
        LocalDateTime now = LocalDateTime.now();
        List<String> leaked = new ArrayList<>();
        activeLeases.forEach((id, holder) -> {
            if (!holder.released && holder.accountId != null) {
                long minutes = ChronoUnit.MINUTES.between(holder.acquiredAt, now);
                if (minutes >= leaseLeakThresholdMinutes) {
                    leaked.add(id);
                    log.error("[POOL] 检测到租约泄露, leaseId={}, accountId={}, 已持有{}分钟, ip={}",
                            id, holder.accountId, minutes, holder.proxy.getHost());
                }
            }
        });
        if (!leaked.isEmpty()) {
            log.warn("[POOL] 本轮泄露检测共发现 {} 条泄露租约", leaked.size());
        }
    }

    /**
     * 获取统计指标（用于监控 / 日志）。
     */
    public PoolMetrics getMetrics() {
        PoolMetrics m = new PoolMetrics();
        m.totalAcquire = totalAcquireCount.get();
        m.totalAcquireFail = totalAcquireFailCount.get();
        m.activeLeaseCount = activeLeases.size();
        m.bindingCount = bindings.size();
        m.coolingDownCount = coolingDown.size();
        m.registeredProviders = providers.size();
        m.successRate = m.totalAcquire > 0
                ? (m.totalAcquire - m.totalAcquireFail) * 100.0 / m.totalAcquire : 100.0;
        return m;
    }

    /** 统计指标（DTO）。 */
    public static class PoolMetrics {
        public long totalAcquire;
        public long totalAcquireFail;
        public int activeLeaseCount;
        public int bindingCount;
        public int coolingDownCount;
        public int registeredProviders;
        public double successRate;

        @Override
        public String toString() {
            return String.format(
                    "PoolMetrics{providers=%d, activeLeases=%d, bindings=%d, coolingDown=%d, totalAcquire=%d, fail=%d, successRate=%.1f%%}",
                    registeredProviders, activeLeaseCount, bindingCount, coolingDownCount,
                    totalAcquire, totalAcquireFail, successRate);
        }
    }
}
