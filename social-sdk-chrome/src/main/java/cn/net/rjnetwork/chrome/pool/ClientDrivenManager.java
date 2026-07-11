package cn.net.rjnetwork.chrome.pool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.net.rjnetwork.chrome.config.ChromeInstanceConfig;
import cn.net.rjnetwork.chrome.instance.ChromeInstance;
import cn.net.rjnetwork.chrome.operation.CacheManager;
import cn.net.rjnetwork.chrome.operation.CookieManager;
import cn.net.rjnetwork.chrome.operation.OperationManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 客户端驱动的Chrome实例管理器
 * 
 * 核心设计理念:
 * 1. 客户端ID (clientId) 作为唯一标识符，由外部传入
 * 2. 每个客户端拥有独立的数据目录 (basePath/clientId/)
 * 3. 支持按客户端ID获取实例，操作相关方法
 * 4. 支持按客户端隔离清理资源
 * 
 * @author Social SDK
 */
public class ClientDrivenManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ClientDrivenManager.class);

    // ==================== 客户端配置 ====================

    public static class ClientConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 客户端唯一标识
         */
        private String clientId;

        /**
         * 数据存储基础目录
         */
        private String baseDataPath = "./chrome-data";

        /**
         * Cookie文件名称
         */
        private String cookieFileName = "cookies.json";

        /**
         * 缓存文件名称
         */
        private String cacheFileName = "cache.json";

        /**
         * 会话目录名称
         */
        private String sessionDirName = "sessions";

        /**
         * 是否自动保存Cookie
         */
        private boolean autoSaveCookies = true;

        /**
         * 是否自动保存缓存
         */
        private boolean autoSaveCache = true;

        /**
         * 保存间隔（秒）
         */
        private long saveIntervalSeconds = 60;

        /**
         * 实例最大生命周期
         */
        private Duration maxLifetime = Duration.ofHours(2);

        /**
         * 空闲超时时间
         */
        private Duration idleTimeout = Duration.ofMinutes(30);

        /**
         * 是否启用指纹伪装
         */
        private boolean enableFingerprint = true;

        /**
         * 是否启用硬件伪装
         */
        private boolean enableHardware = true;

        // Getters and Setters
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getBaseDataPath() { return baseDataPath; }
        public void setBaseDataPath(String baseDataPath) { this.baseDataPath = baseDataPath; }
        public String getCookieFileName() { return cookieFileName; }
        public void setCookieFileName(String cookieFileName) { this.cookieFileName = cookieFileName; }
        public String getCacheFileName() { return cacheFileName; }
        public void setCacheFileName(String cacheFileName) { this.cacheFileName = cacheFileName; }
        public String getSessionDirName() { return sessionDirName; }
        public void setSessionDirName(String sessionDirName) { this.sessionDirName = sessionDirName; }
        public boolean isAutoSaveCookies() { return autoSaveCookies; }
        public void setAutoSaveCookies(boolean autoSaveCookies) { this.autoSaveCookies = autoSaveCookies; }
        public boolean isAutoSaveCache() { return autoSaveCache; }
        public void setAutoSaveCache(boolean autoSaveCache) { this.autoSaveCache = autoSaveCache; }
        public long getSaveIntervalSeconds() { return saveIntervalSeconds; }
        public void setSaveIntervalSeconds(long saveIntervalSeconds) { this.saveIntervalSeconds = saveIntervalSeconds; }
        public Duration getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(Duration maxLifetime) { this.maxLifetime = maxLifetime; }
        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
        public boolean isEnableFingerprint() { return enableFingerprint; }
        public void setEnableFingerprint(boolean enableFingerprint) { this.enableFingerprint = enableFingerprint; }
        public boolean isEnableHardware() { return enableHardware; }
        public void setEnableHardware(boolean enableHardware) { this.enableHardware = enableHardware; }

        /**
         * 获取客户端数据目录
         */
        public Path getClientDataPath() {
            return Paths.get(baseDataPath, sanitizeClientId(clientId));
        }

        /**
         * 获取Cookie文件路径
         */
        public Path getCookieFilePath() {
            return getClientDataPath().resolve(cookieFileName);
        }

        /**
         * 获取缓存文件路径
         */
        public Path getCacheFilePath() {
            return getClientDataPath().resolve(cacheFileName);
        }

        /**
         * 获取会话目录路径
         */
        public Path getSessionDirPath() {
            return getClientDataPath().resolve(sessionDirName);
        }

        private String sanitizeClientId(String id) {
            if (id == null) return "unknown";
            return id.replaceAll("[^a-zA-Z0-9-_]", "_");
        }

        /**
         * 创建默认配置
         */
        public static ClientConfig defaultConfig(String clientId) {
            ClientConfig config = new ClientConfig();
            config.setClientId(clientId);
            config.setAutoSaveCookies(true);
            config.setAutoSaveCache(true);
            return config;
        }

        /**
         * 创建持久化配置
         */
        public static ClientConfig persistentConfig(String clientId, String basePath) {
            ClientConfig config = new ClientConfig();
            config.setClientId(clientId);
            config.setBaseDataPath(basePath);
            config.setAutoSaveCookies(true);
            config.setAutoSaveCache(true);
            config.setSaveIntervalSeconds(30);
            return config;
        }
    }

    // ==================== 客户端会话（包装器） ====================

    /**
     * 客户端会话包装器
     * 封装ChromeInstance并管理客户端相关数据
     */
    public static class ClientSession implements AutoCloseable {
        private final String clientId;
        private final ClientConfig config;
        private final ChromeInstance instance;
        private final OperationManager operations;
        private final AtomicBoolean isDirty = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        // 操作器引用
        private volatile CookieManager cookieManager;
        private volatile CacheManager cacheManager;
        private volatile Path dataPath;

        public ClientSession(String clientId, ClientConfig config, ChromeInstance instance) {
            this.clientId = clientId;
            this.config = config;
            this.instance = instance;
            this.dataPath = config.getClientDataPath();

            // 初始化数据目录
            initializeDataDirectory();

            // 创建操作管理器
            this.operations = new OperationManager(instance.getDriver());

            // 初始化操作器
            initializeManagers();
        }

        private void initializeDataDirectory() {
            try {
                Files.createDirectories(dataPath);
                Files.createDirectories(config.getSessionDirPath());
                logger.debug("Initialized data directory for client {}: {}", clientId, dataPath);
            } catch (IOException e) {
                logger.warn("Failed to create data directory for client {}: {}", clientId, e.getMessage());
            }
        }

        private void initializeManagers() {
            this.cookieManager = new CookieManager(instance.getDriver());
            this.cacheManager = new CacheManager(instance.getDriver());
        }

        // ==================== 标记脏数据 ====================

        public void markDirty() {
            isDirty.set(true);
        }

        public boolean isDirty() {
            return isDirty.get();
        }

        // ==================== Cookie操作 ====================

        /**
         * 保存Cookie到文件
         */
        public synchronized void saveCookies() {
            if (!config.isAutoSaveCookies()) {
                return;
            }

            try {
                Path cookiePath = config.getCookieFilePath();
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();

                // 保存Cookie信息
                root.put("clientId", clientId);
                root.put("saveTime", System.currentTimeMillis());
                root.put("domain", instance.getDriver().getCurrentUrl());

                // 获取所有Cookie
                var cookieArray = mapper.createArrayNode();
                for (org.openqa.selenium.Cookie cookie : instance.getDriver().manage().getCookies()) {
                    ObjectNode cookieNode = mapper.createObjectNode();
                    cookieNode.put("name", cookie.getName());
                    cookieNode.put("value", cookie.getValue());
                    if (cookie.getDomain() != null) cookieNode.put("domain", cookie.getDomain());
                    if (cookie.getPath() != null) cookieNode.put("path", cookie.getPath());
                    if (cookie.getExpiry() != null) cookieNode.put("expiry", cookie.getExpiry().toInstant().toString());
                    cookieNode.put("secure", cookie.isSecure());
                    cookieNode.put("httpOnly", cookie.isHttpOnly());
                    cookieArray.add(cookieNode);
                }
                root.set("cookies", cookieArray);

                // 写入文件
                mapper.writerWithDefaultPrettyPrinter().writeValue(cookiePath.toFile(), root);
                isDirty.set(false);
                logger.debug("Cookies saved for client {}: {}", clientId, cookiePath);

            } catch (Exception e) {
                logger.error("Failed to save cookies for client {}: {}", clientId, e.getMessage(), e);
            }
        }

        /**
         * 从文件加载Cookie
         */
        public synchronized boolean loadCookies() {
            Path cookiePath = config.getCookieFilePath();
            if (!Files.exists(cookiePath)) {
                logger.debug("Cookie file not found for client {}: {}", clientId, cookiePath);
                return false;
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.readValue(cookiePath.toFile(), ObjectNode.class);

                // 清除现有Cookie
                instance.getDriver().manage().deleteAllCookies();

                // 加载Cookie
                if (root.has("cookies")) {
                    ((com.fasterxml.jackson.databind.JsonNode) root.get("cookies")).forEach(cookieNode -> {
                        try {
                            org.openqa.selenium.Cookie.Builder builder = 
                                new org.openqa.selenium.Cookie.Builder(
                                    cookieNode.get("name").asText(),
                                    cookieNode.get("value").asText()
                                );

                            if (cookieNode.has("domain")) builder.domain(cookieNode.get("domain").asText());
                            if (cookieNode.has("path")) builder.path(cookieNode.get("path").asText());

                            if (cookieNode.has("expiry")) {
                                Instant expiry = Instant.parse(cookieNode.get("expiry").asText());
                                builder.expiresOn(java.util.Date.from(expiry));
                            }

                            instance.getDriver().manage().addCookie(builder.build());
                        } catch (Exception e) {
                            logger.warn("Failed to load cookie: {}", e.getMessage());
                        }
                    });
                }

                logger.info("Cookies loaded for client {}: {} cookies", clientId, 
                    root.has("cookies") ? root.get("cookies").size() : 0);
                return true;

            } catch (Exception e) {
                logger.error("Failed to load cookies for client {}: {}", clientId, e.getMessage(), e);
                return false;
            }
        }

        // ==================== 缓存操作 ====================

        /**
         * 保存缓存到文件
         */
        public synchronized void saveCache() {
            if (!config.isAutoSaveCache()) {
                return;
            }

            try {
                Path cachePath = config.getCacheFilePath();
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();

                root.put("clientId", clientId);
                root.put("saveTime", System.currentTimeMillis());
                root.put("localStorageSize", cacheManager.getLocalStorageSize());
                root.put("localStorageKeys", cacheManager.getAllLocalStorageKeys().size());

                // 快照LocalStorage
                ObjectNode storageNode = mapper.createObjectNode();
                for (String key : cacheManager.getAllLocalStorageKeys()) {
                    cacheManager.getLocalStorage(key).ifPresent(value -> 
                        storageNode.put(key, value)
                    );
                }
                root.set("localStorage", storageNode);

                mapper.writerWithDefaultPrettyPrinter().writeValue(cachePath.toFile(), root);
                logger.debug("Cache saved for client {}: {}", clientId, cachePath);

            } catch (Exception e) {
                logger.error("Failed to save cache for client {}: {}", clientId, e.getMessage(), e);
            }
        }

        /**
         * 从文件加载缓存
         */
        public synchronized boolean loadCache() {
            Path cachePath = config.getCacheFilePath();
            if (!Files.exists(cachePath)) {
                return false;
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.readValue(cachePath.toFile(), ObjectNode.class);

                if (root.has("localStorage")) {
                    ((com.fasterxml.jackson.databind.JsonNode) root.get("localStorage")).fields().forEachRemaining(entry -> {
                        cacheManager.setLocalStorage(entry.getKey(), entry.getValue().asText());
                    });
                }

                logger.info("Cache loaded for client {}", clientId);
                return true;

            } catch (Exception e) {
                logger.error("Failed to load cache for client {}: {}", clientId, e.getMessage(), e);
                return false;
            }
        }

        // ==================== 会话操作 ====================

        /**
         * 保存完整会话
         */
        public synchronized void saveSession(String sessionName) {
            try {
                Path sessionPath = config.getSessionDirPath().resolve(sessionName + ".json");
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.createObjectNode();

                root.put("clientId", clientId);
                root.put("sessionName", sessionName);
                root.put("saveTime", System.currentTimeMillis());
                root.put("url", instance.getDriver().getCurrentUrl());

                // Cookie
                var cookieArray = mapper.createArrayNode();
                for (org.openqa.selenium.Cookie cookie : instance.getDriver().manage().getCookies()) {
                    ObjectNode cookieNode = mapper.createObjectNode();
                    cookieNode.put("name", cookie.getName());
                    cookieNode.put("value", cookie.getValue());
                    if (cookie.getDomain() != null) cookieNode.put("domain", cookie.getDomain());
                    if (cookie.getPath() != null) cookieNode.put("path", cookie.getPath());
                    cookieArray.add(cookieNode);
                }
                root.set("cookies", cookieArray);

                // LocalStorage
                ObjectNode storageNode = mapper.createObjectNode();
                for (String key : cacheManager.getAllLocalStorageKeys()) {
                    cacheManager.getLocalStorage(key).ifPresent(value -> 
                        storageNode.put(key, value)
                    );
                }
                root.set("localStorage", storageNode);

                mapper.writerWithDefaultPrettyPrinter().writeValue(sessionPath.toFile(), root);
                logger.info("Session saved for client {}: {}", clientId, sessionPath);

            } catch (Exception e) {
                logger.error("Failed to save session for client {}: {}", clientId, e.getMessage(), e);
            }
        }

        /**
         * 加载会话
         */
        public synchronized boolean loadSession(String sessionName) {
            Path sessionPath = config.getSessionDirPath().resolve(sessionName + ".json");
            if (!Files.exists(sessionPath)) {
                logger.warn("Session file not found: {}", sessionPath);
                return false;
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode root = mapper.readValue(sessionPath.toFile(), ObjectNode.class);

                // 加载Cookie
                if (root.has("cookies")) {
                    instance.getDriver().manage().deleteAllCookies();
                    ((com.fasterxml.jackson.databind.JsonNode) root.get("cookies")).forEach(cookieNode -> {
                        try {
                            org.openqa.selenium.Cookie.Builder builder = 
                                new org.openqa.selenium.Cookie.Builder(
                                    cookieNode.get("name").asText(),
                                    cookieNode.get("value").asText()
                                );
                            if (cookieNode.has("domain")) builder.domain(cookieNode.get("domain").asText());
                            if (cookieNode.has("path")) builder.path(cookieNode.get("path").asText());
                            instance.getDriver().manage().addCookie(builder.build());
                        } catch (Exception e) {
                            logger.warn("Failed to load session cookie: {}", e.getMessage());
                        }
                    });
                }

                // 加载LocalStorage
                if (root.has("localStorage")) {
                    ((com.fasterxml.jackson.databind.JsonNode) root.get("localStorage")).fields().forEachRemaining(entry -> {
                        cacheManager.setLocalStorage(entry.getKey(), entry.getValue().asText());
                    });
                }

                logger.info("Session loaded for client {}: {}", clientId, sessionName);
                return true;

            } catch (Exception e) {
                logger.error("Failed to load session for client {}: {}", clientId, e.getMessage(), e);
                return false;
            }
        }

        // ==================== 获取操作器 ====================

        public ChromeInstance getInstance() {
            return instance;
        }

        public WebDriver getDriver() {
            return instance.getDriver();
        }

        public OperationManager operations() {
            return operations;
        }

        public CookieManager cookies() {
            return cookieManager;
        }

        public CacheManager cache() {
            return cacheManager;
        }

        public String getClientId() {
            return clientId;
        }

        public Path getDataPath() {
            return dataPath;
        }

        public boolean isClosed() {
            return closed.get();
        }

        // ==================== 生命周期 ====================

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            try {
                // 保存数据
                if (config.isAutoSaveCookies()) {
                    saveCookies();
                }
                if (config.isAutoSaveCache()) {
                    saveCache();
                }

                // 关闭实例
                instance.close();
                logger.info("Client session closed: {}", clientId);

            } catch (Exception e) {
                logger.error("Error closing client session {}: {}", clientId, e.getMessage(), e);
            }
        }
    }

    // ==================== 管理器属性 ====================

    private final String managerId;
    private final Path basePath;
    private final Map<String, ClientSession> clientSessions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 构造函数 ====================

    public ClientDrivenManager() {
        this("./chrome-data");
    }

    public ClientDrivenManager(String basePath) {
        this.managerId = "manager-" + System.currentTimeMillis();
        this.basePath = Paths.get(basePath);

        // 创建基础目录
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            logger.warn("Failed to create base directory: {}", e.getMessage());
        }

        // 创建调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ClientManager-Scheduler-" + managerId);
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        logger.info("ClientDrivenManager initialized: basePath={}", basePath);
    }

    // ==================== 客户端管理 ====================

    /**
     * 创建或获取客户端会话
     * 
     * @param clientId 客户端唯一标识
     * @param config 配置（首次创建时使用）
     * @return 客户端会话
     */
    public synchronized ClientSession getOrCreateClient(String clientId) {
        return getOrCreateClient(clientId, ClientConfig.defaultConfig(clientId));
    }

    /**
     * 创建或获取客户端会话（自定义配置）
     */
    public synchronized ClientSession getOrCreateClient(String clientId, ClientConfig config) {
        // 检查是否已存在
        ClientSession existing = clientSessions.get(clientId);
        if (existing != null && !existing.isClosed()) {
            logger.debug("Returning existing client session: {}", clientId);
            return existing;
        }

        // 创建新会话
        logger.info("Creating new client session: {}", clientId);

        // 合并配置
        config.setClientId(clientId);
        config.setBaseDataPath(basePath.toString());

        // 创建实例配置
        ChromeInstanceConfig instanceConfig = createInstanceConfig(config);

        // 创建Chrome实例
        ChromeInstance instance = new ChromeInstance(instanceConfig);
        instance.start();

        // 创建会话包装器
        ClientSession session = new ClientSession(clientId, config, instance);
        clientSessions.put(clientId, session);

        // 启动自动保存调度
        scheduleAutoSave(session, config);

        return session;
    }

    /**
     * 获取客户端会话（如果不存在则返回Optional.empty）
     */
    public Optional<ClientSession> getClient(String clientId) {
        ClientSession session = clientSessions.get(clientId);
        if (session != null && !session.isClosed()) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * 获取客户端并执行操作
     */
    public <T> T withClient(String clientId, java.util.function.Function<ClientSession, T> action) {
        return withClient(clientId, ClientConfig.defaultConfig(clientId), action);
    }

    /**
     * 获取客户端并执行操作（自定义配置）
     */
    public <T> T withClient(String clientId, ClientConfig config, java.util.function.Function<ClientSession, T> action) {
        ClientSession session = getOrCreateClient(clientId, config);
        try {
            return action.apply(session);
        } finally {
            // 不自动关闭，保持会话活跃
        }
    }

    /**
     * 释放客户端会话（保留实例，下一次获取时复用）
     */
    public void releaseClient(String clientId) {
        ClientSession session = clientSessions.get(clientId);
        if (session != null && !session.isClosed()) {
            logger.debug("Released client session: {}", clientId);
            // 标记脏数据并保存
            session.markDirty();
            session.saveCookies();
            session.saveCache();
        }
    }

    /**
     * 关闭并清理客户端会话
     */
    public void closeClient(String clientId) {
        ClientSession session = clientSessions.remove(clientId);
        if (session != null) {
            session.close();
            logger.info("Client session closed and removed: {}", clientId);
        }
    }

    /**
     * 关闭所有客户端会话
     */
    public void closeAllClients() {
        lock.writeLock().lock();
        try {
            logger.info("Closing all client sessions: {}", clientSessions.size());

            // 保存所有数据
            clientSessions.values().forEach(session -> {
                try {
                    session.markDirty();
                    session.saveCookies();
                    session.saveCache();
                } catch (Exception e) {
                    logger.warn("Error saving data for client: {}", e.getMessage());
                }
            });

            // 关闭所有会话
            clientSessions.values().forEach(ClientSession::close);
            clientSessions.clear();

            logger.info("All client sessions closed");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== 实例配置创建 ====================

    private ChromeInstanceConfig createInstanceConfig(ClientConfig clientConfig) {
        ChromeInstanceConfig config = ChromeInstanceConfig.stealthConfig();

        String instanceId = "chrome-" + clientConfig.getClientId() + "-" + System.currentTimeMillis();
        config.setInstanceId(instanceId);
        config.setMaxLifetime(clientConfig.getMaxLifetime());
        config.setIdleTimeout(clientConfig.getIdleTimeout());

        // 指纹伪装
        if (clientConfig.isEnableFingerprint()) {
            config.setEnableFingerprintSpoofing(true);
            config.getFingerprintConfig().setCanvasSpoofEnabled(true);
            config.getFingerprintConfig().setWebglSpoofEnabled(true);
            config.getFingerprintConfig().setScreenSpoofEnabled(true);
            config.getFingerprintConfig().setRandomizeClientRects(true);
        }

        // 硬件伪装
        if (clientConfig.isEnableHardware()) {
            config.setEnableHardwareSpoofing(true);
            config.getHardwareConfig().setCpuCores(8);
            config.getHardwareConfig().setLogicalProcessors(16);
            config.getHardwareConfig().setGpuRenderer("ANGLE (NVIDIA GeForce RTX 3080 Direct3D11 vs_0_0 mb_0_0)");
        }

        return config;
    }

    // ==================== 自动保存调度 ====================

    private void scheduleAutoSave(ClientSession session, ClientConfig config) {
        if (config.getSaveIntervalSeconds() <= 0) {
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            if (session.isDirty() && !session.isClosed()) {
                try {
                    session.saveCookies();
                    session.saveCache();
                } catch (Exception e) {
                    logger.debug("Auto-save failed for client {}: {}", session.getClientId(), e.getMessage());
                }
            }
        }, config.getSaveIntervalSeconds(), config.getSaveIntervalSeconds(), TimeUnit.SECONDS);
    }

    // ==================== 批量操作 ====================

    /**
     * 获取所有客户端ID
     */
    public Set<String> getAllClientIds() {
        return Collections.unmodifiableSet(clientSessions.keySet());
    }

    /**
     * 获取活跃客户端数量
     */
    public int getActiveClientCount() {
        return (int) clientSessions.values().stream()
            .filter(s -> !s.isClosed())
            .count();
    }

    /**
     * 执行所有客户端操作
     */
    public <T> Map<String, T> withAllClients(java.util.function.Function<ClientSession, T> action) {
        Map<String, T> results = new HashMap<>();
        clientSessions.forEach((clientId, session) -> {
            if (!session.isClosed()) {
                results.put(clientId, action.apply(session));
            }
        });
        return results;
    }

    // ==================== 数据目录管理 ====================

    /**
     * 获取客户端数据目录
     */
    public Path getClientDataPath(String clientId) {
        return basePath.resolve(clientId.replaceAll("[^a-zA-Z0-9-_]", "_"));
    }

    /**
     * 清理客户端数据目录（不关闭实例）
     */
    public void cleanClientData(String clientId) {
        Path clientPath = getClientDataPath(clientId);
        try {
            Files.walk(clientPath)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete: {}", path);
                    }
                });
            logger.info("Cleaned data directory for client: {}", clientId);
        } catch (IOException e) {
            logger.error("Failed to clean data directory for client {}: {}", clientId, e.getMessage());
        }
    }

    /**
     * 列出所有已保存的客户端数据
     */
    public List<String> listSavedClients() {
        List<String> clients = new ArrayList<>();
        if (!Files.exists(basePath)) {
            return clients;
        }

        try {
            Files.list(basePath)
                .filter(Files::isDirectory)
                .forEach(path -> clients.add(path.getFileName().toString()));
        } catch (IOException e) {
            logger.error("Failed to list saved clients: {}", e.getMessage());
        }
        return clients;
    }

    /**
     * 加载已保存的客户端数据（不创建实例）
     */
    public Optional<ClientConfig> loadClientConfig(String clientId) {
        Path configPath = getClientDataPath(clientId).resolve("client.config");
        if (!Files.exists(configPath)) {
            return Optional.empty();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ClientConfig config = mapper.readValue(configPath.toFile(), ClientConfig.class);
            return Optional.of(config);
        } catch (Exception e) {
            logger.error("Failed to load client config: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        lock.writeLock().lock();
        try {
            // 停止调度器
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 关闭所有客户端
            closeAllClients();

            logger.info("ClientDrivenManager closed: {}", managerId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 检查管理器是否运行
     */
    public boolean isRunning() {
        return running.get();
    }

    public String getManagerId() {
        return managerId;
    }

    public Path getBasePath() {
        return basePath;
    }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("managerId", managerId);
        stats.put("basePath", basePath.toString());
        stats.put("running", running.get());
        stats.put("activeClients", getActiveClientCount());
        stats.put("totalClients", clientSessions.size());
        stats.put("savedClients", listSavedClients().size());
        return stats;
    }

    public void printStatistics() {
        logger.info("=== ClientDrivenManager Statistics ===");
        logger.info("Manager ID: {}", managerId);
        logger.info("Base Path: {}", basePath);
        logger.info("Running: {}", running.get());
        logger.info("Active Clients: {}", getActiveClientCount());
        logger.info("Total Clients: {}", clientSessions.size());
        logger.info("Saved Clients: {}", listSavedClients().size());
        logger.info("======================================");
    }

    // ==================== 静态工厂方法 ====================

    public static ClientDrivenManager create() {
        return new ClientDrivenManager();
    }

    public static ClientDrivenManager create(String basePath) {
        return new ClientDrivenManager(basePath);
    }

    public static ClientDrivenManager createPersistent(String basePath) {
        ClientDrivenManager manager = new ClientDrivenManager(basePath);
        // 确保目录存在
        try {
            Files.createDirectories(manager.basePath);
        } catch (IOException e) {
            logger.warn("Failed to create base directory: {}", e.getMessage());
        }
        return manager;
    }
}
