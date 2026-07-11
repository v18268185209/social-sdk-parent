package cn.net.rjnetwork.chrome.operation;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 缓存管理器
 * 提供浏览器缓存的设置、获取、清理等功能，支持localStorage、sessionStorage、IndexedDB、Cache API
 */
public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    // ==================== 缓存配置 ====================

    public static class CacheConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 是否自动序列化
         */
        private boolean autoSerialize = true;

        /**
         * 是否压缩数据
         */
        private boolean compressData = false;

        /**
         * 默认过期时间（秒）
         */
        private long defaultExpirySeconds = 3600;

        /**
         * 最大缓存大小（字节）
         */
        private long maxCacheSize = 10 * 1024 * 1024;

        /**
         * 存储前缀
         */
        private String storagePrefix = "sdk_cache_";

        /**
         * 是否启用索引管理
         */
        private boolean indexedManagement = true;

        // Getters and Setters
        public boolean isAutoSerialize() { return autoSerialize; }
        public void setAutoSerialize(boolean autoSerialize) { this.autoSerialize = autoSerialize; }
        public boolean isCompressData() { return compressData; }
        public void setCompressData(boolean compressData) { this.compressData = compressData; }
        public long getDefaultExpirySeconds() { return defaultExpirySeconds; }
        public void setDefaultExpirySeconds(long defaultExpirySeconds) { this.defaultExpirySeconds = defaultExpirySeconds; }
        public long getMaxCacheSize() { return maxCacheSize; }
        public void setMaxCacheSize(long maxCacheSize) { this.maxCacheSize = maxCacheSize; }
        public String getStoragePrefix() { return storagePrefix; }
        public void setStoragePrefix(String storagePrefix) { this.storagePrefix = storagePrefix; }
        public boolean isIndexedManagement() { return indexedManagement; }
        public void setIndexedManagement(boolean indexedManagement) { this.indexedManagement = indexedManagement; }

        /**
         * 默认配置
         */
        public static CacheConfig defaultConfig() {
            return new CacheConfig();
        }

        /**
         * 持久化配置
         */
        public static CacheConfig persistentConfig() {
            CacheConfig config = new CacheConfig();
            config.setAutoSerialize(true);
            config.setIndexedManagement(true);
            return config;
        }
    }

    // ==================== 缓存条目 ====================

    public static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private String key;
        private String value;
        private String type;
        private long size;
        private long createdAt;
        private long expiresAt;
        private Map<String, String> metadata;

        public CacheEntry() {
            this.createdAt = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }

        public CacheEntry(String key, String value) {
            this();
            this.key = key;
            this.value = value;
            this.type = detectType(value);
            this.size = value != null ? value.getBytes(StandardCharsets.UTF_8).length : 0;
        }

        public boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }

        // Getters and Setters
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

        private String detectType(String value) {
            if (value == null) return "null";
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
                return "json";
            } catch (Exception e) {
                // 不是JSON
            }
            if (value.matches("^[A-Za-z0-9+/=]+$")) {
                return "base64";
            }
            return "string";
        }
    }

    // ==================== 缓存会话 ====================

    public static class CacheSession implements Serializable {
        private static final long serialVersionUID = 1L;

        private String domain;
        private long saveTime;
        private List<CacheEntry> entries;
        private String localStorageSnapshot;
        private String sessionStorageSnapshot;

        public CacheSession() {
            this.entries = new ArrayList<>();
            this.saveTime = System.currentTimeMillis();
        }

        public void addEntry(CacheEntry entry) {
            entries.add(entry);
        }

        public List<CacheEntry> getEntries() { return entries; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public long getSaveTime() { return saveTime; }
        public void setSaveTime(long saveTime) { this.saveTime = saveTime; }
        public String getLocalStorageSnapshot() { return localStorageSnapshot; }
        public void setLocalStorageSnapshot(String localStorageSnapshot) { this.localStorageSnapshot = localStorageSnapshot; }
        public String getSessionStorageSnapshot() { return sessionStorageSnapshot; }
        public void setSessionStorageSnapshot(String sessionStorageSnapshot) { this.sessionStorageSnapshot = sessionStorageSnapshot; }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final CacheConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    // ==================== 构造函数 ====================

    public CacheManager(WebDriver driver) {
        this(driver, CacheConfig.defaultConfig());
    }

    public CacheManager(WebDriver driver, CacheConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.config = config;
        initialize();
    }

    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.debug("CacheManager initialized");
            initializeCacheIndex();
        }
    }

    private void initializeCacheIndex() {
        if (!config.isIndexedManagement()) {
            return;
        }

        try {
            String script = String.format(
                "if (!localStorage.getItem('%sindex')) {" +
                "  localStorage.setItem('%sindex', JSON.stringify({keys: [], expiry: {}}));" +
                "}",
                config.getStoragePrefix(), config.getStoragePrefix()
            );
            executeJs(script);
        } catch (Exception e) {
            logger.debug("Failed to initialize cache index: {}", e.getMessage());
        }
    }

    // ==================== LocalStorage操作 ====================

    /**
     * 设置LocalStorage项
     */
    public CacheManager setLocalStorage(String key, String value) {
        return setLocalStorage(key, value, config.getDefaultExpirySeconds());
    }

    /**
     * 设置LocalStorage项（带过期时间）
     */
    public CacheManager setLocalStorage(String key, String value, long expirySeconds) {
        try {
            String prefixedKey = config.getStoragePrefix() + key;
            String actualValue = config.isAutoSerialize() ? serializeValue(value) : value;

            ((JavascriptExecutor) driver).executeScript(
                "localStorage.setItem(arguments[0], arguments[1]);",
                prefixedKey, actualValue
            );

            // 更新索引
            if (config.isIndexedManagement()) {
                updateIndex(key, expirySeconds);
            }

            // 更新内存缓存
            memoryCache.put(key, new CacheEntry(key, actualValue));

            logger.debug("Set localStorage: {}", key);
        } catch (WebDriverException e) {
            logger.warn("Failed to set localStorage: {} - {}", key, e.getMessage());
        }
        return this;
    }

    /**
     * 获取LocalStorage项
     */
    public Optional<String> getLocalStorage(String key) {
        try {
            String prefixedKey = config.getStoragePrefix() + key;
            Object result = ((JavascriptExecutor) driver).executeScript(
                "return localStorage.getItem(arguments[0]);",
                prefixedKey
            );

            if (result != null) {
                String value = result.toString();
                if (config.isAutoSerialize()) {
                    return Optional.of(deserializeValue(value));
                }
                return Optional.of(value);
            }
        } catch (WebDriverException e) {
            logger.debug("Failed to get localStorage: {} - {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取LocalStorage项（带类型转换）
     */
    public <T> Optional<T> getLocalStorage(String key, Class<T> type) {
        return getLocalStorage(key).map(value -> {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(value, type);
            } catch (Exception e) {
                logger.warn("Failed to deserialize localStorage value: {}", e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull);
    }

    /**
     * 删除LocalStorage项
     */
    public CacheManager deleteLocalStorage(String key) {
        try {
            String prefixedKey = config.getStoragePrefix() + key;
            ((JavascriptExecutor) driver).executeScript(
                "localStorage.removeItem(arguments[0]);",
                prefixedKey
            );

            // 移除索引
            if (config.isIndexedManagement()) {
                removeFromIndex(key);
            }

            // 移除内存缓存
            memoryCache.remove(key);

            logger.debug("Deleted localStorage: {}", key);
        } catch (WebDriverException e) {
            logger.warn("Failed to delete localStorage: {} - {}", key, e.getMessage());
        }
        return this;
    }

    /**
     * 清空LocalStorage
     */
    public CacheManager clearLocalStorage() {
        try {
            // 先收集所有缓存键
            List<String> keys = getAllLocalStorageKeys();

            // 清空
            ((JavascriptExecutor) driver).executeScript(
                "localStorage.clear();"
            );

            // 重新初始化索引
            if (config.isIndexedManagement()) {
                initializeCacheIndex();
            }

            // 清空内存缓存
            keys.forEach(memoryCache::remove);

            logger.info("Cleared localStorage ({} items)", keys.size());
        } catch (WebDriverException e) {
            logger.warn("Failed to clear localStorage: {}", e.getMessage());
        }
        return this;
    }

    /**
     * 获取所有LocalStorage键
     */
    public List<String> getAllLocalStorageKeys() {
        List<String> keys = new ArrayList<>();
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "var keys = [];" +
                "for (var i = 0; i < localStorage.length; i++) {" +
                "  var key = localStorage.key(i);" +
                "  if (key && key.startsWith('" + config.getStoragePrefix() + "')) {" +
                "    keys.push(key.substring(" + config.getStoragePrefix().length() + "));" +
                "  }" +
                "}" +
                "return keys;"
            );
            if (result instanceof List) {
                ((List<?>) result).forEach(k -> keys.add(k.toString()));
            }
        } catch (WebDriverException e) {
            logger.debug("Failed to get localStorage keys: {}", e.getMessage());
        }
        return keys;
    }

    /**
     * 获取LocalStorage大小
     */
    public long getLocalStorageSize() {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "var size = 0;" +
                "for (var i = 0; i < localStorage.length; i++) {" +
                "  var key = localStorage.key(i);" +
                "  size += key.length + (localStorage.getItem(key) || '').length;" +
                "}" +
                "return size;"
            );
            return result != null ? Long.parseLong(result.toString()) : 0;
        } catch (WebDriverException e) {
            return 0;
        }
    }

    // ==================== SessionStorage操作 ====================

    /**
     * 设置SessionStorage项
     */
    public CacheManager setSessionStorage(String key, String value) {
        try {
            String prefixedKey = config.getStoragePrefix() + key;
            String actualValue = config.isAutoSerialize() ? serializeValue(value) : value;

            ((JavascriptExecutor) driver).executeScript(
                "sessionStorage.setItem(arguments[0], arguments[1]);",
                prefixedKey, actualValue
            );

            logger.debug("Set sessionStorage: {}", key);
        } catch (WebDriverException e) {
            logger.warn("Failed to set sessionStorage: {} - {}", key, e.getMessage());
        }
        return this;
    }

    /**
     * 获取SessionStorage项
     */
    public Optional<String> getSessionStorage(String key) {
        try {
            String prefixedKey = config.getStoragePrefix() + key;
            Object result = ((JavascriptExecutor) driver).executeScript(
                "return sessionStorage.getItem(arguments[0]);",
                prefixedKey
            );

            if (result != null) {
                String value = result.toString();
                if (config.isAutoSerialize()) {
                    return Optional.of(deserializeValue(value));
                }
                return Optional.of(value);
            }
        } catch (WebDriverException e) {
            logger.debug("Failed to get sessionStorage: {} - {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 删除SessionStorage项
     */
    public CacheManager deleteSessionStorage(String key) {
        try {
            String prefixedKey = config.getStoragePrefix() + key;
            ((JavascriptExecutor) driver).executeScript(
                "sessionStorage.removeItem(arguments[0]);",
                prefixedKey
            );
            logger.debug("Deleted sessionStorage: {}", key);
        } catch (WebDriverException e) {
            logger.warn("Failed to delete sessionStorage: {} - {}", key, e.getMessage());
        }
        return this;
    }

    /**
     * 清空SessionStorage
     */
    public CacheManager clearSessionStorage() {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "sessionStorage.clear();"
            );
            logger.info("Cleared sessionStorage");
        } catch (WebDriverException e) {
            logger.warn("Failed to clear sessionStorage: {}", e.getMessage());
        }
        return this;
    }

    // ==================== Cache API操作 ====================

    /**
     * 设置Cache项
     */
    public CacheManager setCache(String cacheName, String key, String value) {
        return setCache(cacheName, key, value, config.getDefaultExpirySeconds());
    }

    /**
     * 设置Cache项（带过期时间）
     */
    public CacheManager setCache(String cacheName, String key, String value, long expirySeconds) {
        try {
            String actualValue = config.isAutoSerialize() ? serializeValue(value) : value;
            String jsonValue = String.format(
                "{\"value\": \"%s\", \"expiry\": %d}",
                escapeJson(actualValue), System.currentTimeMillis() + (expirySeconds * 1000)
            );

            ((JavascriptExecutor) driver).executeScript(
                "return caches.open(arguments[0]).then(function(cache) {" +
                "  return cache.put(arguments[1], new Response(arguments[2]));" +
                "});",
                cacheName, key, jsonValue
            );

            logger.debug("Set cache: {} in {}", key, cacheName);
        } catch (WebDriverException e) {
            logger.warn("Failed to set cache: {} - {}", key, e.getMessage());
        }
        return this;
    }

    /**
     * 获取Cache项
     */
    public Optional<String> getCache(String cacheName, String key) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "return caches.open(arguments[0]).then(function(cache) {" +
                "  return cache.match(arguments[1]).then(function(response) {" +
                "    if (!response) return null;" +
                "    return response.text();" +
                "  });" +
                "});",
                cacheName, key
            );

            if (result != null) {
                String json = result.toString();
                // 解析JSON
                try {
                    com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                    if (node.has("value") && node.has("expiry")) {
                        long expiry = node.get("expiry").asLong();
                        if (System.currentTimeMillis() > expiry) {
                            // 过期，删除
                            deleteCache(cacheName, key);
                            return Optional.empty();
                        }
                        String value = node.get("value").asText();
                        if (config.isAutoSerialize()) {
                            return Optional.of(deserializeValue(value));
                        }
                        return Optional.of(value);
                    }
                } catch (Exception e) {
                    // 不是标准格式，直接返回
                    if (config.isAutoSerialize()) {
                        return Optional.of(deserializeValue(json));
                    }
                    return Optional.of(json);
                }
            }
        } catch (WebDriverException e) {
            logger.debug("Failed to get cache: {} - {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 删除Cache项
     */
    public CacheManager deleteCache(String cacheName, String key) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "return caches.open(arguments[0]).then(function(cache) {" +
                "  return cache.delete(arguments[1]);" +
                "});",
                cacheName, key
            );
            logger.debug("Deleted cache: {} from {}", key, cacheName);
        } catch (WebDriverException e) {
            logger.warn("Failed to delete cache: {} - {}", key, e.getMessage());
        }
        return this;
    }

    /**
     * 清空Cache
     */
    public CacheManager clearCache(String cacheName) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "return caches.delete(arguments[0]);",
                cacheName
            );
            logger.info("Cleared cache: {}", cacheName);
        } catch (WebDriverException e) {
            logger.warn("Failed to clear cache: {}", e.getMessage());
        }
        return this;
    }

    /**
     * 获取所有Cache名称
     */
    public List<String> getCacheNames() {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "return caches.keys();"
            );
            if (result instanceof List) {
                List<String> names = new ArrayList<>();
                ((List<?>) result).forEach(n -> names.add(n.toString()));
                return names;
            }
        } catch (WebDriverException e) {
            logger.debug("Failed to get cache names: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // ==================== 完整缓存操作 ====================

    /**
     * 设置缓存（自动选择存储）
     */
    public CacheManager set(String key, String value) {
        return set(key, value, config.getDefaultExpirySeconds());
    }

    /**
     * 设置缓存（指定存储类型和过期时间）
     */
    public CacheManager set(String key, String value, long expirySeconds) {
        setLocalStorage(key, value, expirySeconds);
        return this;
    }

    /**
     * 获取缓存（自动选择存储）
     */
    public Optional<String> get(String key) {
        Optional<String> localResult = getLocalStorage(key);

        // 优先返回localStorage的结果
        if (localResult.isPresent()) {
            return localResult;
        }

        return getSessionStorage(key);
    }

    /**
     * 删除缓存
     */
    public CacheManager delete(String key) {
        deleteLocalStorage(key);
        deleteSessionStorage(key);
        return this;
    }

    /**
     * 清空所有缓存
     */
    public CacheManager clear() {
        clearLocalStorage();
        clearSessionStorage();
        getCacheNames().forEach(this::clearCache);
        logger.info("Cleared all caches");
        return this;
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("localStorageSize", getLocalStorageSize());
        stats.put("localStorageKeys", getAllLocalStorageKeys().size());
        stats.put("cacheNames", getCacheNames());
        stats.put("memoryCacheSize", memoryCache.size());
        return stats;
    }

    // ==================== 保存和加载 ====================

    /**
     * 保存缓存到文件
     */
    public void saveToFile(String filePath) {
        try {
            CacheSession session = buildSession();
            session.setLocalStorageSnapshot(snapshotLocalStorage());

            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(session);

            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            java.nio.file.Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            logger.info("Cache saved to: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to save cache: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save cache", e);
        }
    }

    /**
     * 从文件加载缓存
     */
    public void loadFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.warn("Cache file not found: {}", filePath);
                return;
            }

            String json = java.nio.file.Files.readString(file.toPath());
            CacheSession session = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, CacheSession.class);

            // 恢复LocalStorage
            if (session.getLocalStorageSnapshot() != null) {
                restoreLocalStorage(session.getLocalStorageSnapshot());
            }

            // 恢复缓存条目
            for (CacheEntry entry : session.getEntries()) {
                setLocalStorage(entry.getKey(), entry.getValue());
            }

            logger.info("Cache loaded from: {} ({} entries)", filePath, session.getEntries().size());
        } catch (Exception e) {
            logger.error("Failed to load cache: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load cache", e);
        }
    }

    // ==================== 辅助方法 ====================

    private String snapshotLocalStorage() {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                "var data = {};" +
                "for (var i = 0; i < localStorage.length; i++) {" +
                "  var key = localStorage.key(i);" +
                "  data[key] = localStorage.getItem(key);" +
                "}" +
                "return JSON.stringify(data);"
            );
            return result != null ? result.toString() : "{}";
        } catch (Exception e) {
            return "{}";
        }
    }

    private void restoreLocalStorage(String snapshot) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "var data = JSON.parse(arguments[0]);" +
                "Object.keys(data).forEach(function(key) {" +
                "  localStorage.setItem(key, data[key]);" +
                "});",
                snapshot
            );
        } catch (Exception e) {
            logger.warn("Failed to restore localStorage: {}", e.getMessage());
        }
    }

    private String serializeValue(String value) {
        if (value == null) return "";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + escapeJson(value) + "\"";
        }
    }

    private String deserializeValue(String value) {
        if (value == null) return null;
        try {
            // 尝试解析JSON
            com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
            if (node.isTextual()) {
                return node.asText();
            }
            return value;
        } catch (Exception e) {
            // 不是JSON，返回原始值（去掉引号）
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private void updateIndex(String key, long expirySeconds) {
        try {
            String script = String.format(
                "var index = JSON.parse(localStorage.getItem('%sindex') || '{keys:[], expiry:{}}');" +
                "if (!index.keys.includes(arguments[0])) {" +
                "  index.keys.push(arguments[0]);" +
                "}" +
                "index.expiry[arguments[0]] = %d + Date.now();" +
                "localStorage.setItem('%sindex', JSON.stringify(index));",
                config.getStoragePrefix(), config.getStoragePrefix()
            );
            ((JavascriptExecutor) driver).executeScript(script, key);
        } catch (Exception e) {
            logger.debug("Failed to update index: {}", e.getMessage());
        }
    }

    private void removeFromIndex(String key) {
        try {
            String script = String.format(
                "var index = JSON.parse(localStorage.getItem('%sindex') || '{keys:[], expiry:{}}');" +
                "index.keys = index.keys.filter(function(k) { return k !== arguments[0]; });" +
                "delete index.expiry[arguments[0]];" +
                "localStorage.setItem('%sindex', JSON.stringify(index));",
                config.getStoragePrefix(), config.getStoragePrefix()
            );
            ((JavascriptExecutor) driver).executeScript(script, key);
        } catch (Exception e) {
            logger.debug("Failed to remove from index: {}", e.getMessage());
        }
    }

    private Object executeJs(String script) {
        try {
            return ((JavascriptExecutor) driver).executeScript(script);
        } catch (Exception e) {
            logger.debug("JavaScript execution failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建缓存会话
     */
    public CacheSession buildSession() {
        CacheSession session = new CacheSession();
        session.setDomain(getCurrentDomain());

        for (String key : getAllLocalStorageKeys()) {
            Optional<String> value = getLocalStorage(key);
            if (value.isPresent()) {
                CacheEntry entry = new CacheEntry(key, value.get());
                session.addEntry(entry);
            }
        }

        return session;
    }

    private String getCurrentDomain() {
        try {
            String url = driver.getCurrentUrl();
            if (url != null) {
                java.net.URL parsedUrl = new java.net.URL(url);
                return parsedUrl.getHost();
            }
        } catch (Exception e) {
            // 忽略
        }
        return "";
    }

    // ==================== 静态方法 ====================

    public static CacheManager create(WebDriver driver) {
        return new CacheManager(driver);
    }

    public static CacheManager create(WebDriver driver, CacheConfig config) {
        return new CacheManager(driver, config);
    }
}
