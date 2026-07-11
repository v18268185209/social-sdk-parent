package cn.net.rjnetwork.chrome.pool;

import cn.net.rjnetwork.chrome.config.ChromeInstanceConfig;
import cn.net.rjnetwork.chrome.instance.ChromeInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Chrome实例池管理器
 * 提供企业级的Chrome实例池管理，支持多实例、缓存维护、负载均衡等
 */
public class ChromeInstanceManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ChromeInstanceManager.class);

    // ==================== 池配置 ====================

    public static class PoolConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 最大实例数
         */
        private int maxInstances = 10;

        /**
         * 最小实例数（预热）
         */
        private int minInstances = 2;

        /**
         * 实例空闲超时时间
         */
        private Duration idleTimeout = Duration.ofMinutes(30);

        /**
         * 最大生命周期
         */
        private Duration maxLifetime = Duration.ofHours(2);

        /**
         * 获取实例超时时间
         */
        private Duration acquireTimeout = Duration.ofSeconds(30);

        /**
         * 是否启用实例预热
         */
        private boolean warmUpEnabled = true;

        /**
         * 预热实例数量
         */
        private int warmUpCount = 2;

        /**
         * 健康检查间隔
         */
        private Duration healthCheckInterval = Duration.ofMinutes(5);

        /**
         * 是否启用统计
         */
        private boolean statisticsEnabled = true;

        /**
         * 实例创建超时
         */
        private Duration instanceCreateTimeout = Duration.ofSeconds(60);

        // Getters and Setters
        public int getMaxInstances() { return maxInstances; }
        public void setMaxInstances(int maxInstances) { this.maxInstances = maxInstances; }
        public int getMinInstances() { return minInstances; }
        public void setMinInstances(int minInstances) { this.minInstances = minInstances; }
        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        public Duration getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(Duration maxLifetime) { this.maxLifetime = maxLifetime; }
        public Duration getAcquireTimeout() { return acquireTimeout; }
        public void setAcquireTimeout(Duration acquireTimeout) { this.acquireTimeout = acquireTimeout; }
        public boolean isWarmUpEnabled() { return warmUpEnabled; }
        public void setWarmUpEnabled(boolean warmUpEnabled) { this.warmUpEnabled = warmUpEnabled; }
        public int getWarmUpCount() { return warmUpCount; }
        public void setWarmUpCount(int warmUpCount) { this.warmUpCount = warmUpCount; }
        public Duration getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(Duration healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }
        public boolean isStatisticsEnabled() { return statisticsEnabled; }
        public void setStatisticsEnabled(boolean statisticsEnabled) { this.statisticsEnabled = statisticsEnabled; }
        public Duration getInstanceCreateTimeout() { return instanceCreateTimeout; }
        public void setInstanceCreateTimeout(Duration instanceCreateTimeout) { this.instanceCreateTimeout = instanceCreateTimeout; }
    }

    // ==================== 实例仓库 ====================

    private static class InstanceRepository {
        private final Map<String, ChromeInstance> instances = new ConcurrentHashMap<>();
        private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();
        private final LinkedBlockingQueue<String> idleQueue = new LinkedBlockingQueue<>();
        private final AtomicInteger activeCount = new AtomicInteger(0);
        private final AtomicInteger totalCreated = new AtomicInteger(0);
        private final AtomicInteger totalClosed = new AtomicInteger(0);

        synchronized void add(ChromeInstance instance) {
            instances.put(instance.getInstanceId(), instance);
            lastAccessTime.put(instance.getInstanceId(), System.currentTimeMillis());
            totalCreated.incrementAndGet();
        }

        synchronized boolean remove(String instanceId) {
            ChromeInstance removed = instances.remove(instanceId);
            lastAccessTime.remove(instanceId);
            idleQueue.remove(instanceId);
            if (removed != null) {
                totalClosed.incrementAndGet();
                return true;
            }
            return false;
        }

        ChromeInstance get(String instanceId) {
            return instances.get(instanceId);
        }

        Collection<ChromeInstance> getAll() {
            return Collections.unmodifiableCollection(instances.values());
        }

        int size() {
            return instances.size();
        }

        int idleSize() {
            return idleQueue.size();
        }

        int activeCount() {
            return activeCount.get();
        }

        void markActive(String instanceId) {
            lastAccessTime.put(instanceId, System.currentTimeMillis());
            activeCount.incrementAndGet();
            idleQueue.remove(instanceId);
        }

        void markIdle(String instanceId) {
            lastAccessTime.put(instanceId, System.currentTimeMillis());
            activeCount.decrementAndGet();
            idleQueue.offer(instanceId);
        }

        Set<String> getIdleInstanceIds() {
            return Collections.unmodifiableSet(new HashSet<>(idleQueue));
        }

        long getLastAccessTime(String instanceId) {
            return lastAccessTime.getOrDefault(instanceId, 0L);
        }

        int getTotalCreated() {
            return totalCreated.get();
        }

        int getTotalClosed() {
            return totalClosed.get();
        }
    }

    // ==================== 池属性 ====================

    private final String poolId;
    private final PoolConfig poolConfig;
    private final InstanceRepository repository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> healthCheckTask;

    // ==================== 构造函数 ====================

    public ChromeInstanceManager() {
        this(new PoolConfig());
    }

    public ChromeInstanceManager(PoolConfig poolConfig) {
        this.poolId = "pool-" + System.currentTimeMillis();
        this.poolConfig = poolConfig;
        this.repository = new InstanceRepository();

        logger.info("ChromeInstanceManager initialized: {}", poolId);
    }

    public ChromeInstanceManager(String poolId, PoolConfig poolConfig) {
        this.poolId = poolId;
        this.poolConfig = poolConfig;
        this.repository = new InstanceRepository();

        logger.info("ChromeInstanceManager initialized: {}", poolId);
    }

    // ==================== 生命周期管理 ====================

    /**
     * 启动实例池
     */
    public synchronized void start() {
        lock.writeLock().lock();
        try {
            if (running.get()) {
                logger.warn("Instance pool is already running: {}", poolId);
                return;
            }

            logger.info("Starting Chrome instance pool: {}", poolId);

            // 创建调度器
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ChromePool-Scheduler-" + poolId);
                t.setDaemon(true);
                return t;
            });

            // 启动健康检查
            if (poolConfig.getHealthCheckInterval() != null && !poolConfig.getHealthCheckInterval().isZero()) {
                healthCheckTask = scheduler.scheduleWithFixedDelay(
                    this::performHealthCheck,
                    poolConfig.getHealthCheckInterval().toMillis(),
                    poolConfig.getHealthCheckInterval().toMillis(),
                    TimeUnit.MILLISECONDS
                );
            }

            // 预热实例
            if (poolConfig.isWarmUpEnabled() && poolConfig.getWarmUpCount() > 0) {
                warmUp(poolConfig.getWarmUpCount());
            }

            running.set(true);
            logger.info("Chrome instance pool started: {} (config: max={}, min={})",
                poolId, poolConfig.getMaxInstances(), poolConfig.getMinInstances());

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 预热实例
     */
    public void warmUp(int count) {
        lock.readLock().lock();
        try {
            logger.info("Warming up {} Chrome instances", count);

            int warmedUp = 0;
            for (int i = 0; i < count && repository.size() < poolConfig.getMaxInstances(); i++) {
                try {
                    ChromeInstance instance = createInstance();
                    repository.add(instance);
                    repository.markIdle(instance.getInstanceId());
                    warmedUp++;
                } catch (Exception e) {
                    logger.warn("Failed to warm up instance", e);
                }
            }

            logger.info("Warmed up {} Chrome instances", warmedUp);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 从池中获取实例
     */
    public ChromeInstance acquire() {
        return acquire(poolConfig.getAcquireTimeout());
    }

    /**
     * 从池中获取实例（带超时）
     */
    public ChromeInstance acquire(Duration timeout) {
        if (!running.get()) {
            throw new IllegalStateException("Instance pool is not running: " + poolId);
        }

        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();

        while (true) {
            // 1. 尝试从空闲队列获取
            String idleInstanceId = repository.getIdleInstanceIds().stream()
                .filter(id -> !isExpired(id))
                .findFirst()
                .orElse(null);

            if (idleInstanceId != null) {
                ChromeInstance instance = repository.get(idleInstanceId);
                if (instance != null && instance.isReady()) {
                    repository.markActive(idleInstanceId);
                    logger.debug("Acquired idle instance: {}", idleInstanceId);
                    return instance;
                }
            }

            // 2. 检查是否可以创建新实例
            if (repository.size() < poolConfig.getMaxInstances()) {
                try {
                    ChromeInstance instance = createInstance();
                    repository.add(instance);
                    repository.markActive(instance.getInstanceId());
                    logger.debug("Created and acquired new instance: {}", instance.getInstanceId());
                    return instance;
                } catch (Exception e) {
                    logger.error("Failed to create new instance", e);
                }
            }

            // 3. 等待空闲实例
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeoutMillis) {
                throw new RuntimeException("Failed to acquire Chrome instance within timeout: " + timeout);
            }

            try {
                Thread.sleep(Math.min(100, timeoutMillis - elapsed));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Chrome instance", e);
            }
        }
    }

    /**
     * 释放实例回池
     */
    public void release(ChromeInstance instance) {
        if (instance == null) {
            return;
        }

        lock.readLock().lock();
        try {
            if (!running.get()) {
                instance.close();
                return;
            }

            String instanceId = instance.getInstanceId();

            // 检查实例是否有效
            if (instance.isError() || instance.isClosed()) {
                logger.debug("Removing invalid instance: {}", instanceId);
                repository.remove(instanceId);
                return;
            }

            // 检查是否过期
            if (isExpired(instanceId)) {
                logger.debug("Removing expired instance: {}", instanceId);
                instance.close();
                repository.remove(instanceId);
                return;
            }

            // 检查是否需要重启
            if (instance.getRestartCount() > 3) {
                logger.info("Restarting over-restarted instance: {}", instanceId);
                instance.restart();
            }

            // 放回空闲队列
            if (instance.isReady()) {
                repository.markIdle(instanceId);
                logger.debug("Released instance to pool: {}", instanceId);
            }

            // 检查是否需要补充实例
            maintainMinInstances();

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取实例（如果存在）
     */
    public Optional<ChromeInstance> get(String instanceId) {
        return Optional.ofNullable(repository.get(instanceId));
    }

    /**
     * 获取所有活跃实例
     */
    public Collection<ChromeInstance> getAllInstances() {
        return repository.getAll();
    }

    /**
     * 获取空闲实例列表
     */
    public Collection<ChromeInstance> getIdleInstances() {
        List<ChromeInstance> idleInstances = new ArrayList<>();
        for (String instanceId : repository.getIdleInstanceIds()) {
            ChromeInstance instance = repository.get(instanceId);
            if (instance != null && instance.isIdle()) {
                idleInstances.add(instance);
            }
        }
        return idleInstances;
    }

    /**
     * 关闭池并清理所有实例
     */
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (!running.get()) {
                return;
            }

            logger.info("Closing Chrome instance pool: {}", poolId);

            // 停止健康检查
            if (healthCheckTask != null) {
                healthCheckTask.cancel(false);
            }

            // 停止调度器
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 关闭所有实例
            for (ChromeInstance instance : repository.getAll()) {
                try {
                    instance.close();
                } catch (Exception e) {
                    logger.warn("Error closing instance", e);
                }
            }
            repository.getAll().clear();

            running.set(false);
            logger.info("Chrome instance pool closed: {}", poolId);

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== 实例管理 ====================

    /**
     * 创建新实例
     */
    private ChromeInstance createInstance() {
        ChromeInstanceConfig config = createDefaultConfig();
        
        // 为实例分配唯一ID
        String instanceId = "chrome-pool-" + poolId + "-" + System.currentTimeMillis() + "-" + 
            repository.getTotalCreated();
        config.setInstanceId(instanceId);

        // 配置生命周期
        config.setMaxLifetime(poolConfig.getMaxLifetime());
        config.setIdleTimeout(poolConfig.getIdleTimeout());

        // 创建并启动实例
        ChromeInstance instance = new ChromeInstance(config);
        instance.start();

        return instance;
    }

    private ChromeInstanceConfig createDefaultConfig() {
        ChromeInstanceConfig config = ChromeInstanceConfig.stealthConfig();
        config.setMaxRetries(3);
        config.setAutoRestartOnError(true);
        return config;
    }

    /**
     * 检查实例是否过期
     */
    private boolean isExpired(String instanceId) {
        ChromeInstance instance = repository.get(instanceId);
        return instance == null || instance.isExpired();
    }

    /**
     * 维护最小实例数
     */
    private void maintainMinInstances() {
        int currentSize = repository.size();
        int idleCount = repository.idleSize();

        if (currentSize < poolConfig.getMinInstances() && idleCount < poolConfig.getMinInstances() / 2) {
            logger.debug("Maintaining minimum instances: current={}, min={}", currentSize, poolConfig.getMinInstances());
            try {
                ChromeInstance instance = createInstance();
                repository.add(instance);
                repository.markIdle(instance.getInstanceId());
            } catch (Exception e) {
                logger.warn("Failed to maintain minimum instances", e);
            }
        }
    }

    // ==================== 健康检查 ====================

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        if (!running.get()) {
            return;
        }

        logger.debug("Performing health check for pool: {}", poolId);

        for (ChromeInstance instance : repository.getAll()) {
            try {
                if (instance.isError() || instance.isClosed()) {
                    logger.warn("Removing unhealthy instance: {}", instance.getInstanceId());
                    repository.remove(instance.getInstanceId());
                    continue;
                }

                // 刷新空闲实例
                if (instance.isIdle()) {
                    instance.refresh();
                }
            } catch (Exception e) {
                logger.warn("Health check failed for instance", e);
                repository.remove(instance.getInstanceId());
            }
        }

        // 补充实例
        maintainMinInstances();
    }

    /**
     * 检查池是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取池ID
     */
    public String getPoolId() {
        return poolId;
    }

    // ==================== 统计信息 ====================

    public int getTotalInstances() {
        return repository.size();
    }

    public int getActiveInstances() {
        return repository.activeCount();
    }

    public int getIdleInstanceCount() {
        return repository.idleSize();
    }

    public int getTotalCreated() {
        return repository.getTotalCreated();
    }

    public int getTotalClosed() {
        return repository.getTotalClosed();
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("poolId", poolId);
        stats.put("running", running.get());
        stats.put("totalInstances", repository.size());
        stats.put("activeInstances", repository.activeCount());
        stats.put("idleInstances", repository.idleSize());
        stats.put("totalCreated", repository.getTotalCreated());
        stats.put("totalClosed", repository.getTotalClosed());
        stats.put("maxInstances", poolConfig.getMaxInstances());
        stats.put("minInstances", poolConfig.getMinInstances());
        return stats;
    }

    /**
     * 打印统计信息
     */
    public void printStatistics() {
        logger.info("=== Chrome Instance Pool Statistics ===");
        logger.info("Pool ID: {}", poolId);
        logger.info("Running: {}", running.get());
        logger.info("Total Instances: {}", repository.size());
        logger.info("Active Instances: {}", repository.activeCount());
        logger.info("Idle Instances: {}", repository.idleSize());
        logger.info("Total Created: {}", repository.getTotalCreated());
        logger.info("Total Closed: {}", repository.getTotalClosed());
        logger.info("======================================");
    }

    // ==================== 静态工厂方法 ====================

    public static ChromeInstanceManager create() {
        return new ChromeInstanceManager();
    }

    public static ChromeInstanceManager create(int maxInstances, int minInstances) {
        PoolConfig config = new PoolConfig();
        config.setMaxInstances(maxInstances);
        config.setMinInstances(minInstances);
        return new ChromeInstanceManager(config);
    }

    public static ChromeInstanceManager createHighPerformance() {
        PoolConfig config = new PoolConfig();
        config.setMaxInstances(20);
        config.setMinInstances(5);
        config.setIdleTimeout(Duration.ofMinutes(10));
        config.setHealthCheckInterval(Duration.ofMinutes(2));
        config.setWarmUpEnabled(true);
        config.setWarmUpCount(5);
        return new ChromeInstanceManager(config);
    }

    public static ChromeInstanceManager createLowMemory() {
        PoolConfig config = new PoolConfig();
        config.setMaxInstances(3);
        config.setMinInstances(1);
        config.setIdleTimeout(Duration.ofMinutes(5));
        config.setHealthCheckInterval(Duration.ofMinutes(1));
        config.setWarmUpEnabled(false);
        return new ChromeInstanceManager(config);
    }
}
