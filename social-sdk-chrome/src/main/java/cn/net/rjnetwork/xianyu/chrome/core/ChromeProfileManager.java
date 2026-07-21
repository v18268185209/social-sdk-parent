package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.exception.ChromeException;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chrome 容器管理器（核心编排层）。
 *
 * <p>负责：
 * <ul>
 *   <li>容器生命周期：创建、启动、停止、销毁</li>
 *   <li>代理绑定：为每个容器 {@link ProxyPoolManager#acquire(ProxyAcquireRequest)} 获取专一代理，实现 IP 隔离</li>
 *   <li>指纹隔离：每个账号分配唯一 seed（SHA-256 派生），基于 seed 在注入 JS 中为 canvas/WebGL 生成唯一的、稳定的噪声；
 *       同 seed 噪声一致（跨重启指纹不变），不同 seed 噪声不同（账号间指纹唯一）</li>
 *   <li>崩溃检测：{@link #healthCheck()} 定时检测进程存活+CDP 就绪，标记 CRASHED</li>
 *   <li>崩溃恢复：在 {@link ChromeConfig#getMaxCrashRecoveryAttempts()} 内自动重启容器</li>
 *   <li>端口绑定：通过 {@link ChromePortPool} 管理 CDP 端口分配与回收</li>
 * </ul>
 *
 * <p>容器与账号是一对一关系：{@code accountId ↔ profileDir ↔ proxyUrl ↔ seed ↔ cdpPort}。
 */
@Component
public class ChromeProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ChromeProfileManager.class);

    private final ChromeConfig config;
    private final ChromePortPool portPool;
    private final ChromeSession session;
    private final ChromeHealthChecker healthChecker;

    /**
     * 代理池（可选 — 非 Spring Boot 环境可以为 null，此时不自动绑定代理）。
     */
    private volatile ProxyPoolManager proxyPoolManager;

    /** 活跃容器（accountId → ChromeProfile） */
    private final Map<Long, ChromeProfile> activeProfiles = new ConcurrentHashMap<>();

    /** 崩溃恢复时使用的后台线程 */
    private final ScheduledExecutorService recoveryScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "chrome-recovery");
                t.setDaemon(true);
                return t;
            });

    public ChromeProfileManager(ChromeConfig config,
                                ChromePortPool portPool,
                                ChromeSession session,
                                ChromeHealthChecker healthChecker) {
        this.config = config;
        this.portPool = portPool;
        this.session = session;
        this.healthChecker = healthChecker;
    }

    /**
     * 注入代理池管理器（Spring 环境中自动注入，或手动设置）。
     */
    public void setProxyPoolManager(ProxyPoolManager proxyPoolManager) {
        this.proxyPoolManager = proxyPoolManager;
    }

    // ==================== 容器生命周期 ====================

    /**
     * 为指定账号创建并启动 Chrome 容器。
     *
     * @param accountId   账号 ID
     * @param accountName 账号名称（展示用）
     * @return 创建的 ChromeProfile
     * @throws ChromeException 启动失败
     */
    public ChromeProfile launchAccount(long accountId, String accountName) {
        if (activeProfiles.containsKey(accountId)) {
            ChromeProfile existing = activeProfiles.get(accountId);
            if (existing.isAlive()) {
                log.info("[LAUNCH] 容器已在运行, accountId={}", accountId);
                return existing;
            } else {
                // 旧容器已崩溃，清理后重新创建
                stopAccount(accountId);
            }
        }

        // 1. 派生 seed
        long seed = deriveSeed(accountId);

        // 2. CDP 端口分配
        int port = portPool.acquirePort();

        // 3. 绑定代理
        String proxyUrl = null;
        String proxyLeaseId = null;
        if (proxyPoolManager != null) {
            try {
                ProxyAcquireRequest req = ProxyAcquireRequest.defaultRequest(accountId);
                var lease = proxyPoolManager.acquire(req);
                ProxyInfo proxyInfo = lease.getProxy();
                proxyUrl = proxyInfo.toProxyUri();
                proxyLeaseId = lease.getLeaseId();
                log.info("[LAUNCH] 代理绑定, accountId={}, proxy={}", accountId, proxyUrl);
            } catch (ProxyException pe) {
                portPool.releasePort(port);
                throw ChromeException.proxyBindingFailed(accountId, pe.getMessage());
            }
        }

        // 4. 构建 profile
        ChromeProfile profile = ChromeProfile.builder()
                .accountId(accountId)
                .accountName(accountName)
                .profileDir(config.resolveProfileDir(accountId))
                .cdpPort(port)
                .proxyUrl(proxyUrl)
                .proxyLeaseId(proxyLeaseId)
                .seed(seed)
                .status(ChromeProfile.ContainerStatus.INITIALIZING)
                .crashCount(0)
                .build();

        activeProfiles.put(accountId, profile);

        // 5. 启动 Chrome 进程
        try {
            session.launch(profile);
            log.info("[LAUNCH] 容器启动成功, accountId={}, port={}", accountId, port);
        } catch (ChromeException ce) {
            // 启动失败：清理资源
            cleanupFailedLaunch(profile);
            throw ce;
        }

        // 6. 启动成功后注入反检测 JS（应用 per-account seed 噪声，失败不影响启动））
        safeInjectFingerprint(profile);

        return profile;
    }

    private void safeInjectFingerprint(ChromeProfile profile) {
        try {
            injectFingerprintScript(profile);
        } catch (Exception e) {
            log.warn("[LAUNCH] 指纹注入失败（非关键）, accountId={}, err={}", profile.getAccountId(), e.getMessage());
        }
    }

    /**
     * 停止指定账号的 Chrome 容器（释放端口 + 释放代理 + 优雅退出进程）。
     *
     * @param accountId 账号 ID
     */
    public void stopAccount(long accountId) {
        ChromeProfile profile = activeProfiles.remove(accountId);
        if (profile == null) {
            log.debug("[STOP] 无对应容器, accountId={}", accountId);
            return;
        }

        // 1. 停止 Chrome 进程
        session.shutdown(profile);

        // 2. 释放代理租约
        if (proxyPoolManager != null && profile.getProxyLeaseId() != null) {
            try {
                proxyPoolManager.release(profile.getProxyLeaseId());
            } catch (Exception e) {
                log.warn("[STOP] 释放代理异常, accountId={}, err={}", accountId, e.getMessage());
            }
        }

        // 3. 释放端口
        session.releasePort(profile);

        log.info("[STOP] 容器已停止, accountId={}", accountId);
    }

    /**
     * 销毁所有容器（应用关闭时调用）。
     */
    public void shutdown() {
        log.info("[SHUTDOWN] 关闭所有 Chrome 容器, count={}", activeProfiles.size());
        List<Long> ids = new ArrayList<>(activeProfiles.keySet());
        for (Long accountId : ids) {
            try {
                stopAccount(accountId);
            } catch (Exception e) {
                log.error("[SHUTDOWN] 关闭容器异常, accountId={}", accountId, e);
            }
        }
        activeProfiles.clear();
    }

    // ==================== 指纹注入 ====================

    /**
     * 构造指纹 accountId → seed（SHA-256 派生）。
     * <p>同 accountId → 同 seed（跨重启指纹不变）；不同 accountId → 不同 seed（账号间指纹唯一）。
     */
    public long deriveSeed(long accountId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(String.valueOf(accountId).getBytes(StandardCharsets.UTF_8));
            long seed = 0L;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                seed = (seed << 8) | (hash[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            return accountId * 31L;
        }
    }

    /**
     * 向指定容器的 page target 注入反检测脚本（双通道）。
     *
     * <p>通过 {@link ChromeSession#injectFingerprintScript(int, java.util.function.LongSupplier)} 发送：
     * <ol>
     *   <li>{@code Page.addScriptToEvaluateOnNewDocument} — 持久化，每次 SPA 跳转/刷新都自动注入</li>
     *   <li>{@code Runtime.evaluate} — 立刻在当前页面生效</li>
     * </ol>
     *
     * <p>脚本由 {@link SliderAntiDetect#buildScript(long)} 按 seed 派生（per-account 指纹隔离）。
     */
    public void injectFingerprintScript(ChromeProfile profile) throws IOException, TimeoutException {
        log.info("[INJECT] 开始注入指纹脚本, accountId={}, seed={}",
                profile.getAccountId(), profile.getSeed());
        session.injectFingerprintScript(profile.getCdpPort(), profile::getSeed);
    }

    // ==================== 查询 API ====================

    /**
     * 获取指定账号的容器状态。
     */
    public Optional<ChromeProfile> getProfile(long accountId) {
        return Optional.ofNullable(activeProfiles.get(accountId));
    }

    /**
     * 获取所有活跃容器。
     */
    public Map<Long, ChromeProfile> listProfiles() {
        return Map.copyOf(activeProfiles);
    }

    /**
     * 获取指定账号的 CDP 端点。
     *
     * @return CDP 端点，如 http://127.0.0.1:9222
     */
    public Optional<String> getCdpEndpoint(long accountId) {
        return getProfile(accountId).map(ChromeProfile::getCdpEndpoint);
    }

    /**
     * 获取指定账号绑定的代理 URL。
     */
    public Optional<String> getProxyUrl(long accountId) {
        return getProfile(accountId).map(ChromeProfile::getProxyUrl);
    }

    /**
     * 获取指定账号的容器是否存活。
     */
    public boolean isAlive(long accountId) {
        ChromeProfile p = activeProfiles.get(accountId);
        return p != null && p.isAlive();
    }

    /**
     * 获取活跃容器数量。
     */
    public int getActiveCount() {
        return activeProfiles.size();
    }

    /**
     * 获取空闲 CDP 端口数。
     */
    public int getAvailablePorts() {
        return portPool.availableCount();
    }

    // ==================== 崩溃恢复 ====================

    /**
     * 定时健康检测（每 N 秒，由 Spring Scheduling 或外部调度器调用）。
     *
     * <p>检测每个容器的：1) 进程存活；2) CDP 就绪。
     * 标记 CRASHED 的容器，触发恢复。
     */
    public void healthCheck() {
        for (Map.Entry<Long, ChromeProfile> entry : activeProfiles.entrySet()) {
            ChromeProfile profile = entry.getValue();
            Long accountId = entry.getKey();

            if (profile.getStatus() == ChromeProfile.ContainerStatus.LAUNCHING) {
                continue; // 启动中，跳过
            }

            boolean healthy = healthChecker.isHealthy(profile, session);
            if (healthy) {
                profile.setLastHealthCheckAt(java.time.LocalDateTime.now());
                if (profile.getStatus() == ChromeProfile.ContainerStatus.CRASHED) {
                    // 容器之前被标记为崩溃但实际已恢复
                    profile.setStatus(ChromeProfile.ContainerStatus.RUNNING);
                }
                continue;
            }

            // 不健康 → 标记崩溃并尝试恢复
            healthChecker.recordCrash(profile);
            log.warn("[HEALTH] 容器不健康, accountId={}, crashCount={}",
                    accountId, profile.getCrashCount());

            if (healthChecker.canRecover(profile)) {
                recoveryScheduler.schedule(() -> attemptRecovery(accountId),
                        config.getCrashRecoveryCooldownMs(), TimeUnit.MILLISECONDS);
            } else {
                log.error("[HEALTH] 达到最大重启次数, 容器保持崩溃状态, accountId={}", accountId);
            }
        }
    }

    /**
     * 尝试恢复指定账号的容器。
     */
    private void attemptRecovery(long accountId) {
        ChromeProfile profile = activeProfiles.get(accountId);
        if (profile == null) return;

        log.info("[RECOVERY] 尝试恢复容器, accountId={}", accountId);
        try {
            // 先强制清理旧资源
            session.shutdown(profile);
            session.releasePort(profile);

            // 重新启动
            portPool.occupyPort(profile.getCdpPort()); // 尝试复用旧端口
            session.launch(profile);
            healthChecker.resetCrashCount(profile);
            injectFingerprintScript(profile);
            log.info("[RECOVERY] 容器恢复成功, accountId={}, port={}", accountId, profile.getCdpPort());
        } catch (Exception e) {
            log.error("[RECOVERY] 容器恢复失败, accountId={}", accountId, e);
            healthChecker.recordCrash(profile);
        }
    }

    // ==================== 内部方法 ====================

    private void cleanupFailedLaunch(ChromeProfile profile) {
        try {
            if (profile.getChromeProcess() != null) {
                session.shutdown(profile);
            }
            session.releasePort(profile);
            if (proxyPoolManager != null && profile.getProxyLeaseId() != null) {
                proxyPoolManager.release(profile.getProxyLeaseId());
            }
        } catch (Exception e) {
            log.warn("[LAUNCH] 清理异常, accountId={}", profile.getAccountId(), e);
        }
        activeProfiles.remove(profile.getAccountId());
    }
}
