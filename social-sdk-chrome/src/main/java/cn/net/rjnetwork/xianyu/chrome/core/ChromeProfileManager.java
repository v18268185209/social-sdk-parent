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

        // 6. 启动成功后注入反检测 JS（应用 per-account seed 噪声）
        injectFingerprintScript(profile);

        return profile;
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

    // ==================== 指纹噪声注入 ====================

    /**
     * 根据账号 seed 派生生成反检测 JS 脚本。
     *
     * <p>同 seed = 同 JS（跨重启指纹不变）；不同 seed → 不同的 canvas/WebGL 噪声（账号间指纹唯一）。
     *
     * @param seed 派生种子
     * @return 完整的反检测 JS 字符串
     */
    public String generateFingerprintScript(long seed) {
        long canvasNoise = deriveNoise(seed, "canvas");
        long webglNoise = deriveNoise(seed, "webgl");
        int screenW = (int) (deriveNoise(seed, "screenW") % 200 + 1280);
        int screenH = (int) (deriveNoise(seed, "screenH") % 200 + 600);
        long hwConcurrency = (deriveNoise(seed, "hw") % 4) + 4; // 4-7
        long deviceMemory = (long) Math.pow(2, (deriveNoise(seed, "mem") % 3) + 2); // 4/8/16 GB

        String webglVendor = pickVendor(webglNoise);
        String webglRenderer = pickRenderer(webglNoise);

        return ""
                + "// ====== Per-Account Anti-Detect Init (seed=" + seed + ") ======\n"
                + "(() => {\n"
                + "  'use strict';\n"
                + "  const SEED = " + seed + ";\n"
                + "\n"
                + "  // 1. 隐藏 webdriver\n"
                + "  try {\n"
                + "    Object.defineProperty(navigator, 'webdriver', { get: () => false });\n"
                + "    delete navigator.__proto__.webdriver;\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 2. 伪造 chrome.runtime\n"
                + "  try {\n"
                + "    window.chrome = window.chrome || {};\n"
                + "    window.chrome.runtime = window.chrome.runtime || {};\n"
                + "    window.chrome.loadTimes = function() { return {}; };\n"
                + "    window.chrome.csi = function() { return {}; };\n"
                + "    window.chrome.app = window.chrome.app || {};\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 3. 伪造 plugins\n"
                + "  try {\n"
                + "    const plugins = [\n"
                + "      { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format', length: 1 },\n"
                + "      { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '', length: 1 },\n"
                + "      { name: 'Native Client', filename: 'internal-nacl-plugin', description: '', length: 2 },\n"
                + "    ];\n"
                + "    plugins.item = (i) => plugins[i] || null;\n"
                + "    plugins.namedItem = (name) => plugins.find(p => p.name === name) || null;\n"
                + "    plugins.refresh = () => {};\n"
                + "    Object.defineProperty(navigator, 'plugins', {\n"
                + "      get: () => Object.setPrototypeOf(plugins, PluginArray.prototype),\n"
                + "    });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 4. 伪造 languages\n"
                + "  try {\n"
                + "    Object.defineProperty(navigator, 'languages', {\n"
                + "      get: () => ['zh-CN', 'zh', 'en-US', 'en'],\n"
                + "    });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 5. Canvas fingerprint — per-account noise\n"
                + "  try {\n"
                + "    const _toDataURL = HTMLCanvasElement.prototype.toDataURL;\n"
                + "    HTMLCanvasElement.prototype.toDataURL = function(type) {\n"
                + "      const ctx = this.getContext('2d');\n"
                + "      if (ctx && this.width > 10 && this.height > 10) {\n"
                + "        const imageData = ctx.getImageData(0, 0, this.width, this.height);\n"
                + "        if (imageData.data.length > 3) {\n"
                + "          const noise = " + canvasNoise + ";\n"
                + "          imageData.data[(noise) % imageData.data.length] ^= 1;\n"
                + "          imageData.data[(noise + 37) % imageData.data.length] ^= 1;\n"
                + "        }\n"
                + "        ctx.putImageData(imageData, 0, 0);\n"
                + "      }\n"
                + "      return _toDataURL.apply(this, arguments);\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 6. WebGL fingerprint — per-account noise\n"
                + "  try {\n"
                + "    const _getParameter = WebGLRenderingContext.prototype.getParameter;\n"
                + "    WebGLRenderingContext.prototype.getParameter = function(parameter) {\n"
                + "      if (parameter === 37445) {\n"
                + "        return '" + webglVendor + "';\n"
                + "      }\n"
                + "      if (parameter === 37446) {\n"
                + "        return '" + webglRenderer + "';\n"
                + "      }\n"
                + "      return _getParameter.call(this, parameter);\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 7. permissions query override\n"
                + "  try {\n"
                + "    const _query = window.navigator.permissions ? window.navigator.permissions.query : null;\n"
                + "    if (_query) {\n"
                + "      window.navigator.permissions.query = function(parameters) {\n"
                + "        if (parameters && parameters.name === 'notifications') {\n"
                + "          return Promise.resolve({ state: Notification.permission, onchange: null });\n"
                + "        }\n"
                + "        return _query.call(this, parameters);\n"
                + "      };\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 8. Headless UA scrub\n"
                + "  try {\n"
                + "    if (navigator.userAgent && navigator.userAgent.includes('Headless')) {\n"
                + "      Object.defineProperty(navigator, 'userAgent', {\n"
                + "        get: () => navigator.userAgent.replace('Headless', ''),\n"
                + "      });\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 9. Screen fingerprint consistency (per-account)\n"
                + "  try {\n"
                + "    Object.defineProperty(screen, 'width', { get: () => " + screenW + " });\n"
                + "    Object.defineProperty(screen, 'height', { get: () => " + screenH + " });\n"
                + "    Object.defineProperty(screen, 'availWidth', { get: () => " + screenW + " });\n"
                + "    Object.defineProperty(screen, 'availHeight', { get: () => " + screenH + " });\n"
                + "    Object.defineProperty(screen, 'colorDepth', { get: () => 24 });\n"
                + "    Object.defineProperty(screen, 'pixelDepth', { get: () => 24 });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 10. Connection rtt\n"
                + "  try {\n"
                + "    if (navigator.connection) {\n"
                + "      Object.defineProperty(navigator.connection, 'rtt', { get: () => 50 });\n"
                + "    }\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 11. Hardware concurrency / device memory (per-account)\n"
                + "  try {\n"
                + "    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => " + hwConcurrency + " });\n"
                + "    Object.defineProperty(navigator, 'deviceMemory', { get: () => " + deviceMemory + " });\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "  // 12. isTrusted bypass attempt via dispatchEvent shim\n"
                + "  try {\n"
                + "    const _createEvent = document.createEvent;\n"
                + "    document.createEvent = function(type) {\n"
                + "      const evt = _createEvent.call(this, type);\n"
                + "      if (evt && typeof evt.initEvent === 'function') {\n"
                + "        Object.defineProperty(evt, 'isTrusted', { get: () => true });\n"
                + "      }\n"
                + "      return evt;\n"
                + "    };\n"
                + "  } catch (e) {}\n"
                + "\n"
                + "})();\n";
    }

    /**
     * 构造指纹噪声（从 seed + label 派生）。
     */
    public long deriveSeed(long accountId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(String.valueOf(accountId).getBytes(StandardCharsets.UTF_8));
            // 取前 8 字节组成 long
            long seed = 0L;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                seed = (seed << 8) | (hash[i] & 0xFFL);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 必可用
            return accountId * 31L;
        }
    }

    /**
     * 从 seed + label 派生长噪声值。
     */
    public long deriveNoise(long seed, String label) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((seed + ":" + label).getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                value = (value << 8) | (hash[i] & 0xFFL);
            }
            return Math.abs(value);
        } catch (NoSuchAlgorithmException e) {
            return Math.abs(seed * 31L + label.hashCode());
        }
    }

    /**
     * 根据噪声选 WebGL vendor 字符串。
     */
    public String pickVendor(long noise) {
        String[] vendors = {
                "Google Inc.",
                "Google Inc. (Intel)",
                "Google Inc. (NVIDIA)",
                "Google Inc. (AMD)",
        };
        return vendors[(int) (noise % vendors.length)];
    }

    /**
     * 根据噪声选 WebGL renderer 字符串。
     */
    public String pickRenderer(long noise) {
        String[] renderers = {
                "ANGLE (Intel, Intel(R) UHD Graphics 620 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (NVIDIA, NVIDIA GeForce GTX 1650 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (AMD, AMD Radeon RX 580 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)",
        };
        return renderers[(int) (noise % renderers.length)];
    }

    /**
     * 向指定容器的所有 page target 注入反检测脚本。
     */
    public void injectFingerprintScript(ChromeProfile profile) {
        String script = generateFingerprintScript(profile.getSeed());
        // 实际注入可以通过 CDP Runtime.evaluate 在 target 上执行
        // 这里仅记录，完整注入逻辑需要与 CDP WebSocket 集成
        log.debug("[INJECT] 反检测脚本已生成, accountId={}, scriptHash={}",
                profile.getAccountId(), script.hashCode());
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
