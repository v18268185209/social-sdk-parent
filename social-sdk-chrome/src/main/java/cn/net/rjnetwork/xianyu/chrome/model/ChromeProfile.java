package cn.net.rjnetwork.xianyu.chrome.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 账号对应的 Chrome 容器配置与状态。
 *
 * <p>一个 ChromeProfile = 一个账号的完整容器绑定：
 * <ul>
 *   <li>accountId — 绑定的闲鱼账号</li>
 *   <li>profileDir — 独立 user-data-dir 路径</li>
 *   <li>cdpPort / cdpEndpoint — CDP 端口与访问入口</li>
 *   <li>proxyUrl — 绑定的代理 IP</li>
 *   <li>seed — 指纹噪声派生种子（同 seed = 同指纹；每个账号唯一且稳定）</li>
 * </ul>
 */
@Data
@Builder
public class ChromeProfile {

    /** 绑定闲鱼账号 ID */
    private Long accountId;

    /** 绑定的账号备注名（仅供展示） */
    private String accountName;

    /** 独立 user-data-dir 路径 */
    private String profileDir;

    /** CDP 端口 */
    private int cdpPort;

    /** CDP HTTP 端点，如 http://127.0.0.1:9222 */
    private String cdpEndpoint;

    /** Chrome 进程 PID（未启动时为 null） */
    private Process chromeProcess;

    /** 绑定的代理 URL（如 http://proxy:port 或 socks5://...） */
    private String proxyUrl;

    /** 代理租约 ID（归还时用） */
    private String proxyLeaseId;

    /** 指纹噪声派生 seed（每个账号唯一且跨重启稳定） */
    private long seed;

    /** 容器启动时间 */
    private LocalDateTime launchedAt;

    /** 最后健康检测时间 */
    private LocalDateTime lastHealthCheckAt;

    /** 容器当前状态 */
    private ContainerStatus status;

    /** 连续崩溃次数 */
    private int crashCount;

    /** 自定义指纹噪声覆盖参数（可选，null 表示用 seed 按算法派生） */
    private FingerprintOverride fingerprintOverride;

    /**
     * 判断容器是否正在运行（进程存活且状态为 RUNNING）。
     */
    public boolean isAlive() {
        return status == ContainerStatus.RUNNING
                && chromeProcess != null
                && chromeProcess.isAlive();
    }

    /**
     * 状态枚举。
     */
    public enum ContainerStatus {
        /** 已初始化但未启动 */
        INITIALIZING,
        /** 启动中 */
        LAUNCHING,
        /** 运行中 */
        RUNNING,
        /** 等待健康检查 */
        HEALTH_CHECK_PENDING,
        /** 崩溃 / 需要恢复 */
        CRASHED,
        /** 已关闭 */
        STOPPED
    }

    /**
     * 指纹覆盖参数（当不想用算法派生时使用）。
     */
    @Data
    @Builder
    public static class FingerprintOverride {
        override
        private String webglVendor;
        private String webglRenderer;
        private String canvasNoisePattern;
        private Integer screenWidth;
        private Integer screenHeight;
        private Integer colorDepth;
        private Integer pixelDepth;
        private String languages;
        private String plat form;
        private String hardwareConcurrency;
        private String deviceMemory;
    }

    /**
     * 展示用摘要（避免打印敏感信息）。
     */
    @Override
    public String toString() {
        return String.format("ChromeProfile{accountId=%d, accountName='%s', cdpPort=%d, proxyUrl='%s', status=%s, crashCount=%d}",
                accountId, accountName, cdpPort, proxyUrl, status, crashCount);
    }
}
