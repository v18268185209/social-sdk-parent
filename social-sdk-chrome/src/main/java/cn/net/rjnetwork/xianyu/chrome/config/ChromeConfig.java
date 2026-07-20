package cn.net.rjnetwork.xianyu.chrome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chrome 容器管理全局配置（绑定前缀 chrome）。
 *
 * <p>多账号隔离设计：每个账号独占一个 Chrome 容器（独立 user-data-dir + 代理 IP + 按账号 seed 的指纹噪声），
 * 避免单 Chrome 多账号共享指纹导致封号。
 */
@Component
@ConfigurationProperties(prefix = "chrome")
@Data
public class ChromeConfig {

    /** Chrome 可执行文件路径（不填则尝试从 PATH / 常见位置自动探测） */
    private String executablePath;

    /** 可分配的 CDP 端口范围起始（含） */
    private int portRangeStart = 9222;

    /** 可分配的 CDP 端口范围结束（含） */
    private int portRangeEnd = 9322;

    /** 用户数据根目录（每个账号会在此目录下开一个子目录） */
    private String userDataDirRoot = "./chrome-profiles";

    /** Chrome 启动超时（秒） */
    private long launchTimeoutSeconds = 30;

    /** 健康检测间隔（秒） */
    private long healthCheckIntervalSeconds = 60;

    /** 崩溃后自动重启最大尝试次数 */
    private int maxCrashRecoveryAttempts = 3;

    /** 崩溃自动重启冷却时间（毫秒） */
    private long crashRecoveryCooldownMs = 5000;

    /** 反检测 JS 注入：canvas/WebGL 是否按账号 seed 生成（true=按账号隔离，false=全局噪声） */
    private boolean perAccountSeedNoise = true;

    /** 默认窗口宽度 */
    private int windowWidth = 1366;

    /** 默认窗口高度 */
    private int windowHeight = 768;

    /** 传递给 Chrome 的反检测启动参数（覆盖默认值时使用） */
    private String[] customLaunchArgs;

    /** 是否在 stderr/stdout 输出 Chrome 进程日志（调试用） */
    private boolean logChromeOutput = false;

    /**
     * 构建指定账号的目标 userDataDir 路径。
     */
    public String resolveProfileDir(long accountId) {
        return userDataDirRoot + "/account-" + accountId;
    }
}
