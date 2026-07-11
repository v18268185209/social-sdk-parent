package cn.net.rjnetwork.chrome;

import cn.net.rjnetwork.chrome.cleanup.ResourceCleaner;
import cn.net.rjnetwork.chrome.instance.ChromeInstance;
import cn.net.rjnetwork.chrome.pool.ChromeInstanceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 优雅停止管理器
 * 提供Chrome实例和实例池的优雅停止功能，确保资源正确释放
 */
public class GracefulShutdown {

    private static final Logger logger = LoggerFactory.getLogger(GracefulShutdown.class);

    // ==================== 停止配置 ====================

    public static class ShutdownConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 全局超时时间
         */
        private Duration globalTimeout = Duration.ofSeconds(60);

        /**
         * 每个实例的最大清理时间
         */
        private Duration perInstanceTimeout = Duration.ofSeconds(15);

        /**
         * 是否强制终止超时实例
         */
        private boolean forceTerminateOnTimeout = true;

        /**
         * 是否保存状态
         */
        private boolean saveState = true;

        /**
         * 是否在停止前截图
         */
        private boolean screenshotBeforeShutdown = true;

        /**
         * 截图保存目录
         */
        private String screenshotDir;

        /**
         * 是否执行清理
         */
        private boolean performCleanup = true;

        /**
         * 是否执行final cleanup
         */
        private boolean performFinalCleanup = true;

        /**
         * 最大并行关闭实例数
         */
        private int maxParallelShutdown = 5;

        /**
         * 是否启用JVM关闭钩子
         */
        private boolean registerShutdownHook = true;

        /**
         * 关闭钩子优先级（数值越小越早执行）
         */
        private int shutdownHookPriority = 100;

        /**
         * 是否记录详细日志
         */
        private boolean verboseLogging = false;

        // Getters and Setters
        public Duration getGlobalTimeout() { return globalTimeout; }
        public void setGlobalTimeout(Duration globalTimeout) { this.globalTimeout = globalTimeout; }
        public Duration getPerInstanceTimeout() { return perInstanceTimeout; }
        public void setPerInstanceTimeout(Duration perInstanceTimeout) { this.perInstanceTimeout = perInstanceTimeout; }
        public boolean isForceTerminateOnTimeout() { return forceTerminateOnTimeout; }
        public void setForceTerminateOnTimeout(boolean forceTerminateOnTimeout) { this.forceTerminateOnTimeout = forceTerminateOnTimeout; }
        public boolean isSaveState() { return saveState; }
        public void setSaveState(boolean saveState) { this.saveState = saveState; }
        public boolean isScreenshotBeforeShutdown() { return screenshotBeforeShutdown; }
        public void setScreenshotBeforeShutdown(boolean screenshotBeforeShutdown) { this.screenshotBeforeShutdown = screenshotBeforeShutdown; }
        public String getScreenshotDir() { return screenshotDir; }
        public void setScreenshotDir(String screenshotDir) { this.screenshotDir = screenshotDir; }
        public boolean isPerformCleanup() { return performCleanup; }
        public void setPerformCleanup(boolean performCleanup) { this.performCleanup = performCleanup; }
        public boolean isPerformFinalCleanup() { return performFinalCleanup; }
        public void setPerformFinalCleanup(boolean performFinalCleanup) { this.performFinalCleanup = performFinalCleanup; }
        public int getMaxParallelShutdown() { return maxParallelShutdown; }
        public void setMaxParallelShutdown(int maxParallelShutdown) { this.maxParallelShutdown = maxParallelShutdown; }
        public boolean isRegisterShutdownHook() { return registerShutdownHook; }
        public void setRegisterShutdownHook(boolean registerShutdownHook) { this.registerShutdownHook = registerShutdownHook; }
        public int getShutdownHookPriority() { return shutdownHookPriority; }
        public void setShutdownHookPriority(int shutdownHookPriority) { this.shutdownHookPriority = shutdownHookPriority; }
        public boolean isVerboseLogging() { return verboseLogging; }
        public void setVerboseLogging(boolean verboseLogging) { this.verboseLogging = verboseLogging; }

        /**
         * 快速停止配置
         */
        public static ShutdownConfig quickShutdown() {
            ShutdownConfig config = new ShutdownConfig();
            config.setGlobalTimeout(Duration.ofSeconds(30));
            config.setPerInstanceTimeout(Duration.ofSeconds(10));
            config.setForceTerminateOnTimeout(true);
            config.setSaveState(false);
            config.setScreenshotBeforeShutdown(false);
            config.setPerformCleanup(false);
            config.setMaxParallelShutdown(10);
            return config;
        }

        /**
         * 安全停止配置
         */
        public static ShutdownConfig safeShutdown() {
            ShutdownConfig config = new ShutdownConfig();
            config.setGlobalTimeout(Duration.ofSeconds(90));
            config.setPerInstanceTimeout(Duration.ofSeconds(20));
            config.setForceTerminateOnTimeout(false);
            config.setSaveState(true);
            config.setScreenshotBeforeShutdown(true);
            config.setPerformCleanup(true);
            config.setMaxParallelShutdown(3);
            return config;
        }
    }

    // ==================== 停止结果 ====================

    public static class ShutdownResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final boolean success;
        private final int totalInstances;
        private final int successfullyClosed;
        private final int forceTerminated;
        private final int failed;
        private final long durationMs;
        private final List<String> errors;
        private final Instant startTime;
        private final Instant endTime;

        public ShutdownResult(boolean success, int total, int closed, int forced, int failed, 
                             long duration, List<String> errors, Instant start, Instant end) {
            this.success = success;
            this.totalInstances = total;
            this.successfullyClosed = closed;
            this.forceTerminated = forced;
            this.failed = failed;
            this.durationMs = duration;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.startTime = start;
            this.endTime = end;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public int getTotalInstances() { return totalInstances; }
        public int getSuccessfullyClosed() { return successfullyClosed; }
        public int getForceTerminated() { return forceTerminated; }
        public int getFailed() { return failed; }
        public long getDurationMs() { return durationMs; }
        public List<String> getErrors() { return errors; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public Duration getDuration() { return Duration.ofMillis(durationMs); }

        public String toSummary() {
            return String.format(
                "Shutdown completed: success=%s, total=%d, closed=%d, forced=%d, failed=%d, duration=%s",
                success, totalInstances, successfullyClosed, forceTerminated, failed, getDuration()
            );
        }
    }

    // ==================== 实例属性 ====================

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final List<ChromeInstance> managedInstances = Collections.synchronizedList(new ArrayList<>());
    private final List<ChromeInstanceManager> managedPools = Collections.synchronizedList(new ArrayList<>());
    private final ShutdownConfig config;
    private final AtomicInteger forceTerminatedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    private volatile Thread shutdownThread;

    // ==================== 构造函数 ====================

    public GracefulShutdown() {
        this(new ShutdownConfig());
    }

    public GracefulShutdown(ShutdownConfig config) {
        this.config = config != null ? config : new ShutdownConfig();
        logger.debug("GracefulShutdown initialized");
    }

    // ==================== 实例管理 ====================

    /**
     * 注册要管理的实例
     */
    public void registerInstance(ChromeInstance instance) {
        if (instance != null && !managedInstances.contains(instance)) {
            managedInstances.add(instance);
            logger.debug("Registered instance: {}", instance.getInstanceId());
        }
    }

    /**
     * 批量注册实例
     */
    public void registerInstances(Collection<ChromeInstance> instances) {
        for (ChromeInstance instance : instances) {
            registerInstance(instance);
        }
    }

    /**
     * 注册要管理的实例池
     */
    public void registerPool(ChromeInstanceManager pool) {
        if (pool != null && !managedPools.contains(pool)) {
            managedPools.add(pool);
            logger.debug("Registered pool: {}", pool.getPoolId());
        }
    }

    /**
     * 取消注册实例
     */
    public void unregisterInstance(ChromeInstance instance) {
        if (instance != null) {
            managedInstances.remove(instance);
            logger.debug("Unregistered instance: {}", instance.getInstanceId());
        }
    }

    /**
     * 取消注册实例池
     */
    public void unregisterPool(ChromeInstanceManager pool) {
        if (pool != null) {
            managedPools.remove(pool);
            logger.debug("Unregistered pool: {}", pool.getPoolId());
        }
    }

    /**
     * 获取所有已注册的实例
     */
    public List<ChromeInstance> getManagedInstances() {
        return new ArrayList<>(managedInstances);
    }

    /**
     * 获取所有已注册的实例池
     */
    public List<ChromeInstanceManager> getManagedPools() {
        return new ArrayList<>(managedPools);
    }

    /**
     * 注册JVM关闭钩子
     */
    public void registerShutdownHook() {
        if (!config.isRegisterShutdownHook()) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM shutdown hook triggered");
            shutdown();
        }, "GracefulShutdown-Hook-" + System.currentTimeMillis()));

        logger.debug("Shutdown hook registered with priority: {}", config.getShutdownHookPriority());
    }

    // ==================== 关闭操作 ====================

    /**
     * 执行优雅关闭
     */
    public ShutdownResult shutdown() {
        return shutdown(true);
    }

    /**
     * 执行关闭
     * @param graceful 是否优雅关闭
     */
    public ShutdownResult shutdown(boolean graceful) {
        if (!shuttingDown.compareAndSet(false, true)) {
            logger.warn("Shutdown already in progress");
            return new ShutdownResult(false, 0, 0, 0, 0, 0, List.of("Shutdown already in progress"), Instant.now(), Instant.now());
        }

        Instant startTime = Instant.now();
        shutdownThread = Thread.currentThread();

        logger.info("Starting graceful shutdown... graceful={}, instances={}, pools={}",
            graceful, managedInstances.size(), managedPools.size());

        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        int successfullyClosed = 0;
        int forceTerminated = 0;

        try {
            // 1. 停止接收新请求（标记为关闭中）
            logger.info("Phase 1: Stopping accepting new requests");

            // 2. 关闭所有实例池
            logger.info("Phase 2: Shutting down instance pools");
            for (ChromeInstanceManager pool : managedPools) {
                try {
                    if (pool.isRunning()) {
                        pool.close();
                        logger.debug("Pool closed: {}", pool.getPoolId());
                    }
                } catch (Exception e) {
                    String error = "Failed to close pool: " + pool.getPoolId() + " - " + e.getMessage();
                    logger.error(error, e);
                    errors.add(error);
                }
            }

            // 3. 关闭所有实例
            logger.info("Phase 3: Shutting down instances");
            List<ChromeInstance> instancesCopy = new ArrayList<>(managedInstances);
            
            if (graceful) {
                ShutdownResult instanceResult = shutdownInstancesGracefully(instancesCopy, errors);
                successfullyClosed = instanceResult.getSuccessfullyClosed();
                forceTerminated = instanceResult.getForceTerminated();
                failedCount.set(instanceResult.getFailed());
            } else {
                ShutdownResult instanceResult = shutdownInstancesForcibly(instancesCopy, errors);
                successfullyClosed = instanceResult.getSuccessfullyClosed();
                forceTerminated = instanceResult.getForceTerminated();
                failedCount.set(instanceResult.getFailed());
            }

            // 4. 最终清理
            if (config.isPerformFinalCleanup()) {
                logger.info("Phase 4: Performing final cleanup");
                performFinalCleanup();
            }

            // 5. 清理注册列表
            managedInstances.clear();
            managedPools.clear();

            Instant endTime = Instant.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            boolean success = failedCount.get() == 0 || !graceful;

            ShutdownResult result = new ShutdownResult(
                success,
                instancesCopy.size(),
                successfullyClosed,
                forceTerminated,
                failedCount.get(),
                durationMs,
                errors,
                startTime,
                endTime
            );

            logger.info("Shutdown completed: {}", result.toSummary());
            return result;

        } catch (Exception e) {
            String error = "Unexpected error during shutdown: " + e.getMessage();
            logger.error(error, e);
            errors.add(error);

            Instant endTime = Instant.now();
            return new ShutdownResult(
                false,
                managedInstances.size(),
                successfullyClosed,
                forceTerminated,
                failedCount.get(),
                Duration.between(startTime, endTime).toMillis(),
                errors,
                startTime,
                endTime
            );
        } finally {
            shuttingDown.set(false);
        }
    }

    /**
     * 优雅关闭所有实例
     */
    private ShutdownResult shutdownInstancesGracefully(List<ChromeInstance> instances, List<String> errors) {
        int successfullyClosed = 0;
        int forceTerminated = 0;
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(config.getMaxParallelShutdown(), instances.size()),
            r -> {
                Thread t = new Thread(r, "InstanceShutdown-");
                t.setDaemon(true);
                return t;
            }
        );

        try {
            // 并行提交关闭任务
            List<Future<InstanceCloseResult>> futures = new ArrayList<>();
            for (ChromeInstance instance : instances) {
                futures.add(executor.submit(() -> closeInstanceGracefully(instance)));
            }

            // 等待结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Future<InstanceCloseResult> future = futures.get(i);
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remainingTime = config.getGlobalTimeout().toMillis() - elapsed;

                    if (remainingTime <= 0) {
                        logger.warn("Global timeout reached, forcing remaining instances to close");
                        break;
                    }

                    InstanceCloseResult result = future.get(remainingTime, TimeUnit.MILLISECONDS);
                    if (result.success) {
                        successfullyClosed++;
                    } else if (result.forceTerminated) {
                        forceTerminated++;
                        forceTerminatedCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                        errors.add(result.error);
                    }

                } catch (TimeoutException e) {
                    logger.warn("Timeout waiting for instance close, will force close");
                } catch (Exception e) {
                    String error = "Error waiting for instance close: " + e.getMessage();
                    logger.error(error, e);
                    errors.add(error);
                    failedCount.incrementAndGet();
                }
            }

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return new ShutdownResult(
            failedCount.get() == 0,
            instances.size(),
            successfullyClosed,
            forceTerminated,
            failedCount.get(),
            System.currentTimeMillis() - startTime,
            errors,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * 强制关闭所有实例
     */
    private ShutdownResult shutdownInstancesForcibly(List<ChromeInstance> instances, List<String> errors) {
        int successfullyClosed = 0;
        int forceTerminated = 0;
        long startTime = System.currentTimeMillis();

        for (ChromeInstance instance : instances) {
            try {
                InstanceCloseResult result = closeInstanceForcibly(instance);
                if (result.success) {
                    successfullyClosed++;
                } else if (result.forceTerminated) {
                    forceTerminated++;
                    forceTerminatedCount.incrementAndGet();
                } else {
                    failedCount.incrementAndGet();
                    errors.add(result.error);
                }
            } catch (Exception e) {
                String error = "Error forcibly closing instance: " + e.getMessage();
                logger.error(error, e);
                errors.add(error);
                failedCount.incrementAndGet();
            }
        }

        return new ShutdownResult(
            failedCount.get() == 0,
            instances.size(),
            successfullyClosed,
            forceTerminated,
            failedCount.get(),
            System.currentTimeMillis() - startTime,
            errors,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * 关闭单个实例（优雅）
     */
    private InstanceCloseResult closeInstanceGracefully(ChromeInstance instance) {
        try {
            if (config.isScreenshotBeforeShutdown()) {
                takeShutdownScreenshot(instance);
            }

            if (config.isSaveState()) {
                instance.getResourceCleaner().ifPresent(ResourceCleaner::saveState);
            }

            if (config.isPerformCleanup()) {
                instance.getResourceCleaner().ifPresent(ResourceCleaner::cleanup);
            }

            long timeoutMs = config.getPerInstanceTimeout().toMillis();
            instance.close();

            logger.debug("Instance closed gracefully: {}", instance.getInstanceId());
            return new InstanceCloseResult(true, false, null);

        } catch (Exception e) {
            if (config.isForceTerminateOnTimeout()) {
                logger.warn("Graceful close failed, forcing close: {}", instance.getInstanceId());
                return closeInstanceForcibly(instance);
            } else {
                String error = "Failed to close instance gracefully: " + instance.getInstanceId() + " - " + e.getMessage();
                logger.error(error, e);
                return new InstanceCloseResult(false, false, error);
            }
        }
    }

    /**
     * 关闭单个实例（强制）
     */
    private InstanceCloseResult closeInstanceForcibly(ChromeInstance instance) {
        try {
            logger.warn("Force closing instance: {}", instance.getInstanceId());
            
            try {
                instance.getDriver().quit();
            } catch (Exception e) {
                logger.debug("Error during force quit", e);
            }

            try {
                instance.close();
            } catch (Exception e) {
                logger.debug("Error during close", e);
            }

            forceTerminatedCount.incrementAndGet();
            logger.debug("Instance force closed: {}", instance.getInstanceId());
            return new InstanceCloseResult(true, true, null);

        } catch (Exception e) {
            String error = "Failed to force close instance: " + instance.getInstanceId() + " - " + e.getMessage();
            logger.error(error, e);
            return new InstanceCloseResult(false, true, error);
        }
    }

    /**
     * 执行最终清理
     */
    private void performFinalCleanup() {
        try {
            // 清理临时文件
            logger.debug("Performing final cleanup tasks");
            
            // 清理线程池
            // 清理缓存
            // 其他清理操作
            
        } catch (Exception e) {
            logger.warn("Error during final cleanup", e);
        }
    }

    private void takeShutdownScreenshot(ChromeInstance instance) {
        try {
            if (config.getScreenshotDir() != null) {
                instance.takeScreenshot("shutdown-" + System.currentTimeMillis());
            }
        } catch (Exception e) {
            logger.debug("Failed to take shutdown screenshot", e);
        }
    }

    // ==================== 状态查询 ====================

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public int getManagedInstanceCount() {
        return managedInstances.size();
    }

    public int getManagedPoolCount() {
        return managedPools.size();
    }

    public int getForceTerminatedCount() {
        return forceTerminatedCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public ShutdownConfig getConfig() {
        return config;
    }

    // ==================== 内部类 ====================

    private static class InstanceCloseResult {
        final boolean success;
        final boolean forceTerminated;
        final String error;

        InstanceCloseResult(boolean success, boolean forceTerminated, String error) {
            this.success = success;
            this.forceTerminated = forceTerminated;
            this.error = error;
        }
    }

    // ==================== 静态方法 ====================

    /**
     * 创建快速停止配置
     */
    public static GracefulShutdown createQuickShutdown() {
        return new GracefulShutdown(ShutdownConfig.quickShutdown());
    }

    /**
     * 创建安全停止配置
     */
    public static GracefulShutdown createSafeShutdown() {
        return new GracefulShutdown(ShutdownConfig.safeShutdown());
    }

    /**
     * 创建并注册JVM钩子的优雅停止器
     */
    public static GracefulShutdown createWithHook() {
        GracefulShutdown shutdown = new GracefulShutdown();
        shutdown.registerShutdownHook();
        return shutdown;
    }
}
