package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chrome 容器健康检测器。
 *
 * <p>负责：
 * <ul>
 *   <li>检测指定容器的 CDP 是否就绪（{@link #isCdpReady(ChromeProfile, ChromeSession)}）</li>
 *   <li>检测指定容器的 Chrome 进程是否存活（{@link #isProcessAlive(ChromeProfile)}）</li>
 *   <li>判断容器是否允许崩溃恢复（基于 {@link ChromeConfig#getMaxCrashRecoveryAttempts()}）</li>
 * </ul>
 *
 * <p>崩溃自动恢复的实际编排逻辑由 {@link ChromeProfileManager} 执行，本类只做底层检测。
 */
@Component
public class ChromeHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ChromeHealthChecker.class);

    private final ChromeConfig config;

    public ChromeHealthChecker(ChromeConfig config) {
        this.config = config;
    }

    /**
     * 检测 CDP 是否就绪。
     *
     * @param profile 容器描述
     * @param session ChromeSession 实例
     * @return true = CDP 可响应；false = CDP 不可达
     */
    public boolean isCdpReady(ChromeProfile profile, ChromeSession session) {
        return session.isCdpReady(profile.getCdpPort());
    }

    /**
     * 检测 Chrome 进程是否存活。
     *
     * @param profile 容器描述
     * @return true = 进程存活；false = 已退出
     */
    public boolean isProcessAlive(ChromeProfile profile) {
        Process proc = profile.getChromeProcess();
        return proc != null && proc.isAlive();
    }

    /**
     * 综合健康判断：进程存活 且 CDP 就绪。
     */
    public boolean isHealthy(ChromeProfile profile, ChromeSession session) {
        return isProcessAlive(profile) && isCdpReady(profile, session);
    }

    /**
     * 容器是否已崩溃（进程退出 或 CDP 不可达）。
     */
    public boolean isCrashed(ChromeProfile profile, ChromeSession session) {
        return !isProcessAlive(profile) || !isCdpReady(profile, session);
    }

    /**
     * 检查容器是否允许崩溃自动恢复。
     *
     * @param profile 容器描述
     * @return true = 允许恢复；false = 超过最大重启次数
     */
    public boolean canRecover(ChromeProfile profile) {
        return profile.getCrashCount() < config.getMaxCrashRecoveryAttempts();
    }

    /**
     * 执行崩溃恢复前的等待冷却。
     *
     * <p>防止频繁重启 Chrome 导致系统资源耗尽。
     *
     * @param profile 容器描述
     */
    public void waitCooldown(ChromeProfile profile) {
        try {
            Thread.sleep(config.getCrashRecoveryCooldownMs());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 记录一次崩溃（递增 crashCount）。
     *
     * @param profile 容器描述
     */
    public void recordCrash(ChromeProfile profile) {
        int count = profile.getCrashCount() + 1;
        profile.setCrashCount(count);
        profile.setStatus(ChromeProfile.ContainerStatus.CRASHED);
        log.warn("[HEALTH] 容器崩溃记录, accountId={}, crashCount={}/{}",
                profile.getAccountId(), count, config.getMaxCrashRecoveryAttempts());
    }

    /**
     * 重置崩溃计数（容器成功启动后调用）。
     *
     * @param profile 容器描述
     */
    public void resetCrashCount(ChromeProfile profile) {
        profile.setCrashCount(0);
    }
}
