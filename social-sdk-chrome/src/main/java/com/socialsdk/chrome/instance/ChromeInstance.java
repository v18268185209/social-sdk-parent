package com.socialsdk.chrome.instance;

import com.socialsdk.chrome.cleanup.ResourceCleaner;
import com.socialsdk.chrome.config.ChromeInstanceConfig;
import com.socialsdk.chrome.config.FingerprintConfig;
import com.socialsdk.chrome.fingerprint.FingerprintSpoofer;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Chrome浏览器实例
 * 封装单个Chrome浏览器实例的完整生命周期管理
 */
public class ChromeInstance implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ChromeInstance.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

    // 状态枚举
    public enum State {
        CREATED,     // 已创建
        INITIALIZING, // 初始化中
        READY,       // 就绪
        IN_USE,      // 使用中
        IDLE,        // 空闲
        CLOSING,     // 关闭中
        CLOSED,      // 已关闭
        ERROR        // 错误状态
    }

    // ==================== 实例属性 ====================

    private final String instanceId;
    private final String instanceName;
    private final ChromeInstanceConfig config;
    private final Instant createdTime;
    private final AtomicInteger useCount = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    private volatile State state = State.CREATED;
    private volatile Instant lastUsedTime;
    private volatile Instant lastActivityTime;
    private volatile String lastError;
    private volatile int restartCount = 0;

    // 核心组件
    private volatile WebDriver driver;
    private volatile ChromeDriverService driverService;
    private volatile FingerprintSpoofer fingerprintSpoofer;
    private volatile ResourceCleaner resourceCleaner;

    // ==================== 构造函数 ====================

    public ChromeInstance(ChromeInstanceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ChromeInstanceConfig cannot be null");
        }
        
        this.config = config;
        this.instanceId = config.getInstanceId() != null ? config.getInstanceId() : generateInstanceId();
        this.instanceName = config.getInstanceName() != null ? config.getInstanceName() : "Chrome-" + INSTANCE_COUNTER.incrementAndGet();
        this.createdTime = Instant.now();
        this.lastUsedTime = createdTime;
        this.lastActivityTime = createdTime;

        logger.info("Created Chrome instance: {} ({})", instanceName, instanceId);
    }

    private static String generateInstanceId() {
        return "chrome-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    // ==================== 生命周期管理 ====================

    /**
     * 启动Chrome实例
     */
    public synchronized ChromeInstance start() {
        stateLock.writeLock().lock();
        try {
            if (state == State.READY || state == State.IN_USE || state == State.IDLE) {
                logger.warn("Instance {} is already started", instanceName);
                return this;
            }

            if (state == State.CLOSING || state == State.CLOSED) {
                throw new IllegalStateException("Cannot start a closed instance: " + instanceName);
            }

            try {
                state = State.INITIALIZING;
                logger.info("Starting Chrome instance: {} ({})", instanceName, instanceId);

                // 创建ChromeDriverService
                driverService = createDriverService();

                // 创建ChromeOptions
                ChromeOptions options = config.toChromeOptions();

                // 启动WebDriver
                driver = new ChromeDriver(driverService, options);
                driver.manage()
                    .timeouts()
                    .pageLoadTimeout(config.getPageLoadTimeout())
                    .implicitlyWait(config.getImplicitWaitTimeout())
                    .setScriptTimeout(config.getScriptTimeout());

                // 初始化指纹伪装
                if (config.isEnableFingerprintSpoofing()) {
                    FingerprintConfig fingerprintConfig = config.getFingerprintConfig();
                    fingerprintSpoofer = new FingerprintSpoofer(fingerprintConfig);
                    fingerprintSpoofer.apply(driver);
                }

                // 初始化资源清理器
                resourceCleaner = new ResourceCleaner(driver, config);

                // 应用额外配置
                applyExtraConfig(driver);

                // 启动时截图
                if (config.isScreenshotOnStart()) {
                    takeScreenshot("startup");
                }

                // 设置状态
                state = State.READY;
                running.set(true);
                lastActivityTime = Instant.now();

                logger.info("Chrome instance started successfully: {} ({})", instanceName, instanceId);
                return this;

            } catch (Exception e) {
                state = State.ERROR;
                lastError = e.getMessage();
                logger.error("Failed to start Chrome instance: {}", instanceName, e);
                throw new RuntimeException("Failed to start Chrome instance: " + e.getMessage(), e);
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * 获取WebDriver
     */
    public WebDriver getDriver() {
        stateLock.readLock().lock();
        try {
            if (state != State.READY && state != State.IN_USE && state != State.IDLE) {
                throw new IllegalStateException("Instance is not ready: " + state);
            }
            return driver;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * 使用实例（自动管理状态）
     */
    public <T> T use(java.util.function.Function<WebDriver, T> function) {
        return use(function, true);
    }

    /**
     * 使用实例（可选是否标记为使用中）
     */
    public <T> T use(java.util.function.Function<WebDriver, T> function, boolean markAsUsed) {
        stateLock.writeLock().lock();
        try {
            // 检查状态
            if (state == State.CLOSED || state == State.CLOSING) {
                throw new IllegalStateException("Instance is closed: " + instanceName);
            }

            if (state == State.ERROR) {
                if (config.isAutoRestartOnError()) {
                    restart();
                } else {
                    throw new IllegalStateException("Instance is in error state: " + lastError);
                }
            }

            // 如果未启动，则启动
            if (state == State.CREATED) {
                start();
            }

            // 标记为使用中
            State previousState = state;
            if (markAsUsed && previousState != State.IN_USE) {
                state = State.IN_USE;
            }

            useCount.incrementAndGet();
            lastUsedTime = Instant.now();
            lastActivityTime = Instant.now();

            try {
                // 执行用户操作
                T result = function.apply(driver);
                lastActivityTime = Instant.now();
                return result;
            } catch (Exception e) {
                handleError(e);
                throw e;
            } finally {
                // 恢复状态
                if (markAsUsed && previousState == State.READY) {
                    state = State.IDLE;
                }
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * 刷新实例（防止超时）
     */
    public ChromeInstance refresh() {
        stateLock.writeLock().lock();
        try {
            if (state != State.CLOSED && state != State.CLOSING) {
                logger.debug("Refreshing Chrome instance: {}", instanceName);
                
                // 检查是否需要重启
                if (isExpired()) {
                    logger.info("Instance {} has expired, restarting", instanceName);
                    restart();
                } else if (driver != null) {
                    // 保持活跃状态
                    try {
                        driver.getTitle();
                        lastActivityTime = Instant.now();
                    } catch (Exception e) {
                        logger.warn("Failed to refresh instance, will restart", e);
                        restart();
                    }
                }
            }
            return this;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * 重启实例
     */
    public synchronized ChromeInstance restart() {
        stateLock.writeLock().lock();
        try {
            logger.info("Restarting Chrome instance: {}", instanceName);
            
            // 先关闭
            close(false);

            // 重置状态
            state = State.CREATED;
            restartCount++;
            lastError = null;

            // 重新启动
            start();

            logger.info("Chrome instance restarted: {} (restart count: {})", instanceName, restartCount);
            return this;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    /**
     * 关闭实例
     */
    @Override
    public void close() {
        close(true);
    }

    /**
     * 关闭实例（可选择是否保存状态）
     */
    public void close(boolean saveState) {
        stateLock.writeLock().lock();
        try {
            if (state == State.CLOSED) {
                logger.debug("Instance {} is already closed", instanceName);
                return;
            }

            state = State.CLOSING;
            logger.info("Closing Chrome instance: {}", instanceName);

            try {
                // 保存状态
                if (saveState && resourceCleaner != null) {
                    resourceCleaner.saveState();
                }

                // 清理资源
                if (resourceCleaner != null) {
                    resourceCleaner.cleanup();
                }

                // 关闭WebDriver
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception e) {
                        logger.warn("Error closing WebDriver", e);
                    }
                    driver = null;
                }

                // 停止服务
                if (driverService != null && driverService.isRunning()) {
                    try {
                        driverService.stop();
                    } catch (Exception e) {
                        logger.warn("Error stopping driver service", e);
                    }
                    driverService = null;
                }

                state = State.CLOSED;
                running.set(false);
                logger.info("Chrome instance closed: {}", instanceName);

            } catch (Exception e) {
                state = State.ERROR;
                logger.error("Error closing Chrome instance", e);
                throw new RuntimeException("Error closing Chrome instance: " + e.getMessage(), e);
            }
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    // ==================== 状态查询方法 ====================

    public String getInstanceId() {
        return instanceId;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public State getState() {
        stateLock.readLock().lock();
        try {
            return state;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }

    public boolean isError() {
        return state == State.ERROR;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public Instant getLastUsedTime() {
        return lastUsedTime;
    }

    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    public int getUseCount() {
        return useCount.get();
    }

    public int getRestartCount() {
        return restartCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Duration getUptime() {
        if (createdTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(createdTime, Instant.now());
    }

    public Duration getIdleTime() {
        if (lastActivityTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(lastActivityTime, Instant.now());
    }

    /**
     * 检查实例是否已过期
     */
    public boolean isExpired() {
        Duration maxLifetime = config.getMaxLifetime();
        if (maxLifetime != null && getUptime().compareTo(maxLifetime) > 0) {
            return true;
        }

        Duration idleTimeout = config.getIdleTimeout();
        if (idleTimeout != null && getIdleTime().compareTo(idleTimeout) > 0 && state == State.IDLE) {
            return true;
        }

        return false;
    }

    // ==================== 辅助方法 ====================

    private ChromeDriverService createDriverService() {
        ChromeDriverService.Builder builder = new ChromeDriverService.Builder();

        // 配置日志输出
        if (config.isVerboseLogging()) {
            builder.withVerbose(true);
        }

        // 配置日志文件
        builder.withLogFile(new File("logs/chrome-" + instanceId + ".log"));

        return builder.build();
    }

    private void applyExtraConfig(WebDriver driver) {
        // 执行额外的JavaScript注入
        String extraJs = config.generateJavascriptInjection();
        if (extraJs != null && !extraJs.isEmpty()) {
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(extraJs);
            } catch (Exception e) {
                logger.warn("Failed to execute extra JavaScript config", e);
            }
        }
    }

    private void handleError(Exception e) {
        lastError = e.getMessage();
        lastActivityTime = Instant.now();

        if (config.isAutoRestartOnError() && restartCount < config.getMaxRetries()) {
            logger.warn("Instance {} encountered error, will restart. Error: {}", instanceName, e.getMessage());
            try {
                restart();
            } catch (Exception restartEx) {
                state = State.ERROR;
                logger.error("Failed to restart instance after error", restartEx);
            }
        } else {
            state = State.ERROR;
            logger.error("Instance {} is now in error state", instanceName);
        }
    }

    /**
     * 截图保存
     */
    public void takeScreenshot(String prefix) {
        if (driver == null || config.getScreenshotDir() == null) {
            return;
        }

        try {
            java.io.File dir = new java.io.File(config.getScreenshotDir());
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = String.format("%s-%s-%d.png", prefix, instanceId, System.currentTimeMillis());
            java.io.File screenshot = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(
                org.openqa.selenium.OutputType.FILE
            );
            java.nio.file.Files.copy(
                screenshot.toPath(),
                new java.io.File(dir, filename).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            logger.debug("Screenshot saved: {}", filename);
        } catch (Exception e) {
            logger.warn("Failed to take screenshot", e);
        }
    }

    /**
     * 获取配置
     */
    public ChromeInstanceConfig getConfig() {
        return config;
    }

    /**
     * 获取资源清理器
     */
    public Optional<ResourceCleaner> getResourceCleaner() {
        return Optional.ofNullable(resourceCleaner);
    }

    /**
     * 获取指纹伪装器
     */
    public Optional<FingerprintSpoofer> getFingerprintSpoofer() {
        return Optional.ofNullable(fingerprintSpoofer);
    }

    // ==================== 静态工厂方法 ====================

    public static ChromeInstance create() {
        return new ChromeInstance(ChromeInstanceConfig.defaultConfig());
    }

    public static ChromeInstance createHeadless() {
        return new ChromeInstance(ChromeInstanceConfig.headlessConfig());
    }

    public static ChromeInstance createStealth() {
        return new ChromeInstance(ChromeInstanceConfig.stealthConfig());
    }

    public static ChromeInstance createPerformance() {
        return new ChromeInstance(ChromeInstanceConfig.performanceConfig());
    }
}
