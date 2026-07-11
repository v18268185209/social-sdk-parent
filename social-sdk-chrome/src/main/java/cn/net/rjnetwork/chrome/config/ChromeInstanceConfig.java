package cn.net.rjnetwork.chrome.config;

import org.openqa.selenium.PageLoadStrategy;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chrome实例配置
 * 完整的单个Chrome浏览器实例配置，包含所有高级功能配置
 */
public class ChromeInstanceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 基础配置 ====================

    /**
     * 实例ID（自动生成）
     */
    private String instanceId;

    /**
     * 实例名称（用于日志和识别）
     */
    private String instanceName = "Chrome-Instance";

    /**
     * Chrome可执行文件路径
     */
    private String executablePath;

    /**
     * 是否启用无头模式
     */
    private boolean headless = false;

    /**
     * 浏览器窗口大小 (宽,高)
     */
    private String windowSize = "1920,1080";

    /**
     * 页面加载策略
     */
    private PageLoadStrategy pageLoadStrategy = PageLoadStrategy.NORMAL;

    /**
     * 用户数据目录（用于隔离和保存session）
     */
    private String userDataDir;

    /**
     * 浏览器启动参数列表
     */
    private List<String> arguments = new ArrayList<>();

    // ==================== 高级功能配置 ====================

    /**
     * 指纹伪装配置
     */
    private FingerprintConfig fingerprintConfig = new FingerprintConfig();

    /**
     * 硬件伪装配置
     */
    private HardwareConfig hardwareConfig = new HardwareConfig();

    /**
     * 是否启用指纹伪装
     */
    private boolean enableFingerprintSpoofing = true;

    /**
     * 是否启用硬件伪装
     */
    private boolean enableHardwareSpoofing = true;

    // ==================== 超时配置 ====================

    /**
     * 页面加载超时时间
     */
    private Duration pageLoadTimeout = Duration.ofSeconds(30);

    /**
     * 隐式等待超时时间
     */
    private Duration implicitWaitTimeout = Duration.ofSeconds(5);

    /**
     * 脚本执行超时时间
     */
    private Duration scriptTimeout = Duration.ofSeconds(30);

    /**
     * 实例最大生命周期（超时后自动停止）
     */
    private Duration maxLifetime = Duration.ofHours(2);

    /**
     * 空闲超时时间（超过此时间未使用则自动回收）
     */
    private Duration idleTimeout = Duration.ofMinutes(30);

    // ==================== 资源管理配置 ====================

    /**
     * 是否禁用GPU
     */
    private boolean disableGpu = true;

    /**
     * 是否禁用沙箱
     */
    private boolean noSandbox = true;

    /**
     * 是否禁用/dev/shm使用
     */
    private boolean disableDevShmUsage = true;

    /**
     * 下载目录
     */
    private String downloadDir;

    /**
     * 扩展程序路径列表
     */
    private List<String> extensionPaths = new ArrayList<>();

    /**
     * 代理服务器地址 (如 "socks5://127.0.0.1:1080")
     */
    private String proxyServer;

    /**
     * 代理认证用户名
     */
    private String proxyUsername;

    /**
     * 代理认证密码
     */
    private String proxyPassword;

    // ==================== 异常处理配置 ====================

    /**
     * 是否启用自动重试
     */
    private boolean enableAutoRetry = true;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 重试间隔时间（毫秒）
     */
    private long retryIntervalMs = 1000;

    /**
     * 是否在发生异常时自动重启实例
     */
    private boolean autoRestartOnError = false;

    // ==================== 日志和调试配置 ====================

    /**
     * 是否启用详细日志
     */
    private boolean verboseLogging = false;

    /**
     * 日志级别 (ALL, FINE, INFO, WARNING, SEVERE, OFF)
     */
    private String logLevel = "WARNING";

    /**
     * 是否在启动时截图
     */
    private boolean screenshotOnStart = false;

    /**
     * 截图保存目录
     */
    private String screenshotDir;

    // ==================== 状态管理配置 ====================

    /**
     * 是否启用状态持久化
     */
    private boolean enableStatePersistence = false;

    /**
     * 状态保存目录
     */
    private String stateDir;

    /**
     * 自动保存间隔（毫秒）
     */
    private long autoSaveIntervalMs = 60000;

    // ==================== 构造函数 ====================

    public ChromeInstanceConfig() {
        this.instanceId = generateInstanceId();
    }

    public ChromeInstanceConfig(String instanceName) {
        this();
        this.instanceName = instanceName;
    }

    private static String generateInstanceId() {
        return "chrome-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    // ==================== 预置配置 ====================

    /**
     * 获取默认配置
     */
    public static ChromeInstanceConfig defaultConfig() {
        return new ChromeInstanceConfig("Default-Chrome");
    }

    /**
     * 获取无头模式配置（适合爬虫）
     */
    public static ChromeInstanceConfig headlessConfig() {
        ChromeInstanceConfig config = new ChromeInstanceConfig("Headless-Chrome");
        config.setHeadless(true);
        config.setEnableFingerprintSpoofing(true);
        config.setPageLoadTimeout(Duration.ofSeconds(60));
        return config;
    }

    /**
     * 获取反检测配置（适合自动化登录）
     */
    public static ChromeInstanceConfig stealthConfig() {
        ChromeInstanceConfig config = new ChromeInstanceConfig("Stealth-Chrome");
        config.setHeadless(false);
        config.setEnableFingerprintSpoofing(true);
        config.setEnableHardwareSpoofing(true);
        config.setAutoRestartOnError(true);
        config.setEnableAutoRetry(true);
        config.setScreenshotOnStart(true);
        return config;
    }

    /**
     * 获取高性能配置（适合批量操作）
     */
    public static ChromeInstanceConfig performanceConfig() {
        ChromeInstanceConfig config = new ChromeInstanceConfig("Performance-Chrome");
        config.setHeadless(true);
        config.setPageLoadTimeout(Duration.ofSeconds(15));
        config.setImplicitWaitTimeout(Duration.ofSeconds(2));
        config.setScriptTimeout(Duration.ofSeconds(10));
        config.setMaxLifetime(Duration.ofHours(1));
        config.setIdleTimeout(Duration.ofMinutes(10));
        return config;
    }

    // ==================== Getters and Setters ====================

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public String getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(String windowSize) {
        this.windowSize = windowSize;
    }

    public PageLoadStrategy getPageLoadStrategy() {
        return pageLoadStrategy;
    }

    public void setPageLoadStrategy(PageLoadStrategy pageLoadStrategy) {
        this.pageLoadStrategy = pageLoadStrategy;
    }

    public String getUserDataDir() {
        return userDataDir;
    }

    public void setUserDataDir(String userDataDir) {
        this.userDataDir = userDataDir;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public void addArgument(String arg) {
        this.arguments.add(arg);
    }

    public FingerprintConfig getFingerprintConfig() {
        return fingerprintConfig;
    }

    public void setFingerprintConfig(FingerprintConfig fingerprintConfig) {
        this.fingerprintConfig = fingerprintConfig;
    }

    public HardwareConfig getHardwareConfig() {
        return hardwareConfig;
    }

    public void setHardwareConfig(HardwareConfig hardwareConfig) {
        this.hardwareConfig = hardwareConfig;
    }

    public boolean isEnableFingerprintSpoofing() {
        return enableFingerprintSpoofing;
    }

    public void setEnableFingerprintSpoofing(boolean enableFingerprintSpoofing) {
        this.enableFingerprintSpoofing = enableFingerprintSpoofing;
    }

    public boolean isEnableHardwareSpoofing() {
        return enableHardwareSpoofing;
    }

    public void setEnableHardwareSpoofing(boolean enableHardwareSpoofing) {
        this.enableHardwareSpoofing = enableHardwareSpoofing;
    }

    public Duration getPageLoadTimeout() {
        return pageLoadTimeout;
    }

    public void setPageLoadTimeout(Duration pageLoadTimeout) {
        this.pageLoadTimeout = pageLoadTimeout;
    }

    public Duration getImplicitWaitTimeout() {
        return implicitWaitTimeout;
    }

    public void setImplicitWaitTimeout(Duration implicitWaitTimeout) {
        this.implicitWaitTimeout = implicitWaitTimeout;
    }

    public Duration getScriptTimeout() {
        return scriptTimeout;
    }

    public void setScriptTimeout(Duration scriptTimeout) {
        this.scriptTimeout = scriptTimeout;
    }

    public Duration getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(Duration maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public boolean isDisableGpu() {
        return disableGpu;
    }

    public void setDisableGpu(boolean disableGpu) {
        this.disableGpu = disableGpu;
    }

    public boolean isNoSandbox() {
        return noSandbox;
    }

    public void setNoSandbox(boolean noSandbox) {
        this.noSandbox = noSandbox;
    }

    public boolean isDisableDevShmUsage() {
        return disableDevShmUsage;
    }

    public void setDisableDevShmUsage(boolean disableDevShmUsage) {
        this.disableDevShmUsage = disableDevShmUsage;
    }

    public String getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

    public List<String> getExtensionPaths() {
        return extensionPaths;
    }

    public void setExtensionPaths(List<String> extensionPaths) {
        this.extensionPaths = extensionPaths;
    }

    public String getProxyServer() {
        return proxyServer;
    }

    public void setProxyServer(String proxyServer) {
        this.proxyServer = proxyServer;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isEnableAutoRetry() {
        return enableAutoRetry;
    }

    public void setEnableAutoRetry(boolean enableAutoRetry) {
        this.enableAutoRetry = enableAutoRetry;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public boolean isAutoRestartOnError() {
        return autoRestartOnError;
    }

    public void setAutoRestartOnError(boolean autoRestartOnError) {
        this.autoRestartOnError = autoRestartOnError;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public boolean isScreenshotOnStart() {
        return screenshotOnStart;
    }

    public void setScreenshotOnStart(boolean screenshotOnStart) {
        this.screenshotOnStart = screenshotOnStart;
    }

    public String getScreenshotDir() {
        return screenshotDir;
    }

    public void setScreenshotDir(String screenshotDir) {
        this.screenshotDir = screenshotDir;
    }

    public boolean isEnableStatePersistence() {
        return enableStatePersistence;
    }

    public void setEnableStatePersistence(boolean enableStatePersistence) {
        this.enableStatePersistence = enableStatePersistence;
    }

    public String getStateDir() {
        return stateDir;
    }

    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }

    public long getAutoSaveIntervalMs() {
        return autoSaveIntervalMs;
    }

    public void setAutoSaveIntervalMs(long autoSaveIntervalMs) {
        this.autoSaveIntervalMs = autoSaveIntervalMs;
    }

    // ==================== 工具方法 ====================

    /**
     * 构建ChromeOptions
     */
    public org.openqa.selenium.chrome.ChromeOptions toChromeOptions() {
        org.openqa.selenium.chrome.ChromeOptions options = new org.openqa.selenium.chrome.ChromeOptions();

        // 基础配置
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=" + windowSize);
        options.addArguments("--disable-gpu=" + disableGpu);
        options.addArguments("--no-sandbox=" + noSandbox);
        options.addArguments("--disable-dev-shm-usage=" + disableDevShmUsage);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        options.setPageLoadStrategy(pageLoadStrategy);

        // 自定义参数
        arguments.forEach(options::addArguments);

        // 用户数据目录
        if (userDataDir != null && !userDataDir.isEmpty()) {
            options.addArguments("--user-data-dir=" + userDataDir);
        }

        // 代理配置
        if (proxyServer != null && !proxyServer.isEmpty()) {
            options.addArguments("--proxy-server=" + proxyServer);
        }

        // 下载目录
        if (downloadDir != null && !downloadDir.isEmpty()) {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", downloadDir);
            prefs.put("download.prompt_for_download", false);
            prefs.put("download.directory_upgrade", true);
            prefs.put("safebrowsing.enabled", true);
            options.setExperimentalOption("prefs", prefs);
        }

        // 扩展程序
        if (!extensionPaths.isEmpty()) {
            List<java.io.File> extensionFiles = new ArrayList<>();
            for (String path : extensionPaths) {
                extensionFiles.add(new java.io.File(path));
            }
            options.addExtensions(extensionFiles);
        }

        // 可执行文件路径
        if (executablePath != null && !executablePath.isEmpty()) {
            options.setBinary(executablePath);
        }

        return options;
    }

    /**
     * 生成完整的JS注入代码
     */
    public String generateJavascriptInjection() {
        StringBuilder sb = new StringBuilder();
        
        if (enableFingerprintSpoofing && fingerprintConfig != null) {
            sb.append(fingerprintConfig.generateJavascriptInjection());
        }
        
        if (enableHardwareSpoofing && hardwareConfig != null) {
            sb.append(hardwareConfig.generateJavascriptInjection());
        }
        
        return sb.toString();
    }

    /**
     * 克隆配置
     */
    public ChromeInstanceConfig clone() {
        ChromeInstanceConfig cloned = new ChromeInstanceConfig(this.instanceName);
        cloned.setExecutablePath(this.executablePath);
        cloned.setHeadless(this.headless);
        cloned.setWindowSize(this.windowSize);
        cloned.setPageLoadStrategy(this.pageLoadStrategy);
        cloned.setUserDataDir(this.userDataDir);
        cloned.setArguments(new ArrayList<>(this.arguments));
        cloned.setFingerprintConfig(this.fingerprintConfig);
        cloned.setHardwareConfig(this.hardwareConfig);
        cloned.setEnableFingerprintSpoofing(this.enableFingerprintSpoofing);
        cloned.setEnableHardwareSpoofing(this.enableHardwareSpoofing);
        cloned.setPageLoadTimeout(this.pageLoadTimeout);
        cloned.setImplicitWaitTimeout(this.implicitWaitTimeout);
        cloned.setScriptTimeout(this.scriptTimeout);
        cloned.setMaxLifetime(this.maxLifetime);
        cloned.setIdleTimeout(this.idleTimeout);
        cloned.setDisableGpu(this.disableGpu);
        cloned.setNoSandbox(this.noSandbox);
        cloned.setDisableDevShmUsage(this.disableDevShmUsage);
        cloned.setDownloadDir(this.downloadDir);
        cloned.setExtensionPaths(new ArrayList<>(this.extensionPaths));
        cloned.setProxyServer(this.proxyServer);
        cloned.setProxyUsername(this.proxyUsername);
        cloned.setProxyPassword(this.proxyPassword);
        cloned.setEnableAutoRetry(this.enableAutoRetry);
        cloned.setMaxRetries(this.maxRetries);
        cloned.setRetryIntervalMs(this.retryIntervalMs);
        cloned.setAutoRestartOnError(this.autoRestartOnError);
        cloned.setVerboseLogging(this.verboseLogging);
        cloned.setLogLevel(this.logLevel);
        cloned.setScreenshotOnStart(this.screenshotOnStart);
        cloned.setScreenshotDir(this.screenshotDir);
        cloned.setEnableStatePersistence(this.enableStatePersistence);
        cloned.setStateDir(this.stateDir);
        cloned.setAutoSaveIntervalMs(this.autoSaveIntervalMs);
        return cloned;
    }
}
