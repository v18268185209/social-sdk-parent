package com.socialsdk.chrome.cleanup;

import com.socialsdk.chrome.config.ChromeInstanceConfig;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 资源清理器
 * 提供Chrome浏览器实例的全面资源清理功能，包括Cookies、LocalStorage、缓存等
 */
public class ResourceCleaner {

    private static final Logger logger = LoggerFactory.getLogger(ResourceCleaner.class);

    // ==================== 清理配置 ====================

    public static class CleanupConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 是否清理Cookies
         */
        private boolean cleanCookies = true;

        /**
         * 是否清理LocalStorage
         */
        private boolean cleanLocalStorage = true;

        /**
         * 是否清理SessionStorage
         */
        private boolean cleanSessionStorage = true;

        /**
         * 是否清理IndexedDB
         */
        private boolean cleanIndexedDB = true;

        /**
         * 是否清理Cache
         */
        private boolean cleanCache = true;

        /**
         * 是否清理ServiceWorkers
         */
        private boolean cleanServiceWorkers = true;

        /**
         * 是否清理WebSQL
         */
        private boolean cleanWebSQL = true;

        /**
         * 是否清理Application Cache
         */
        private boolean cleanApplicationCache = true;

        /**
         * 是否清理Form Data
         */
        private boolean cleanFormData = true;

        /**
         * 是否清理Passwords
         */
        private boolean cleanPasswords = false;

        /**
         * 是否清理Downloads
         */
        private boolean cleanDownloads = false;

        /**
         * 是否清理Plugin Data
         */
        private boolean cleanPluginData = false;

        /**
         * 是否保存状态
         */
        private boolean saveState = false;

        /**
         * 状态保存目录
         */
        private String stateDir;

        /**
         * 自动清理间隔
         */
        private Duration autoCleanupInterval;

        /**
         * 清理前是否截图
         */
        private boolean screenshotBeforeCleanup = true;

        /**
         * 截图保存目录
         */
        private String screenshotDir;

        // Getters and Setters
        public boolean isCleanCookies() { return cleanCookies; }
        public void setCleanCookies(boolean cleanCookies) { this.cleanCookies = cleanCookies; }
        public boolean isCleanLocalStorage() { return cleanLocalStorage; }
        public void setCleanLocalStorage(boolean cleanLocalStorage) { this.cleanLocalStorage = cleanLocalStorage; }
        public boolean isCleanSessionStorage() { return cleanSessionStorage; }
        public void setCleanSessionStorage(boolean cleanSessionStorage) { this.cleanSessionStorage = cleanSessionStorage; }
        public boolean isCleanIndexedDB() { return cleanIndexedDB; }
        public void setCleanIndexedDB(boolean cleanIndexedDB) { this.cleanIndexedDB = cleanIndexedDB; }
        public boolean isCleanCache() { return cleanCache; }
        public void setCleanCache(boolean cleanCache) { this.cleanCache = cleanCache; }
        public boolean isCleanServiceWorkers() { return cleanServiceWorkers; }
        public void setCleanServiceWorkers(boolean cleanServiceWorkers) { this.cleanServiceWorkers = cleanServiceWorkers; }
        public boolean isCleanWebSQL() { return cleanWebSQL; }
        public void setCleanWebSQL(boolean cleanWebSQL) { this.cleanWebSQL = cleanWebSQL; }
        public boolean isCleanApplicationCache() { return cleanApplicationCache; }
        public void setCleanApplicationCache(boolean cleanApplicationCache) { this.cleanApplicationCache = cleanApplicationCache; }
        public boolean isCleanFormData() { return cleanFormData; }
        public void setCleanFormData(boolean cleanFormData) { this.cleanFormData = cleanFormData; }
        public boolean isCleanPasswords() { return cleanPasswords; }
        public void setCleanPasswords(boolean cleanPasswords) { this.cleanPasswords = cleanPasswords; }
        public boolean isCleanDownloads() { return cleanDownloads; }
        public void setCleanDownloads(boolean cleanDownloads) { this.cleanDownloads = cleanDownloads; }
        public boolean isCleanPluginData() { return cleanPluginData; }
        public void setCleanPluginData(boolean cleanPluginData) { this.cleanPluginData = cleanPluginData; }
        public boolean isSaveState() { return saveState; }
        public void setSaveState(boolean saveState) { this.saveState = saveState; }
        public String getStateDir() { return stateDir; }
        public void setStateDir(String stateDir) { this.stateDir = stateDir; }
        public Duration getAutoCleanupInterval() { return autoCleanupInterval; }
        public void setAutoCleanupInterval(Duration autoCleanupInterval) { this.autoCleanupInterval = autoCleanupInterval; }
        public boolean isScreenshotBeforeCleanup() { return screenshotBeforeCleanup; }
        public void setScreenshotBeforeCleanup(boolean screenshotBeforeCleanup) { this.screenshotBeforeCleanup = screenshotBeforeCleanup; }
        public String getScreenshotDir() { return screenshotDir; }
        public void setScreenshotDir(String screenshotDir) { this.screenshotDir = screenshotDir; }

        /**
         * 快速清理配置（清理所有可清理项）
         */
        public static CleanupConfig quickCleanup() {
            CleanupConfig config = new CleanupConfig();
            config.setCleanCookies(true);
            config.setCleanLocalStorage(true);
            config.setCleanSessionStorage(true);
            config.setCleanIndexedDB(true);
            config.setCleanCache(true);
            config.setCleanServiceWorkers(true);
            config.setCleanWebSQL(true);
            config.setCleanApplicationCache(true);
            config.setCleanFormData(true);
            return config;
        }

        /**
         * 完全清理配置
         */
        public static CleanupConfig fullCleanup() {
            CleanupConfig config = quickCleanup();
            config.setCleanPasswords(true);
            config.setCleanDownloads(true);
            config.setCleanPluginData(true);
            return config;
        }

        /**
         * 轻量级清理配置（仅清理存储）
         */
        public static CleanupConfig lightCleanup() {
            CleanupConfig config = new CleanupConfig();
            config.setCleanCookies(true);
            config.setCleanLocalStorage(true);
            config.setCleanSessionStorage(true);
            return config;
        }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final ChromeInstanceConfig instanceConfig;
    private CleanupConfig cleanupConfig;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, Object> cachedState = new ConcurrentHashMap<>();
    private volatile Instant lastCleanupTime;

    // ==================== 构造函数 ====================

    public ResourceCleaner(WebDriver driver, ChromeInstanceConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.instanceConfig = config;
        this.cleanupConfig = createDefaultCleanupConfig();
        this.lastCleanupTime = Instant.now();

        initialize();
    }

    public ResourceCleaner(WebDriver driver, ChromeInstanceConfig config, CleanupConfig cleanupConfig) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.instanceConfig = config;
        this.cleanupConfig = cleanupConfig != null ? cleanupConfig : createDefaultCleanupConfig();
        this.lastCleanupTime = Instant.now();

        initialize();
    }

    private CleanupConfig createDefaultCleanupConfig() {
        CleanupConfig config = new CleanupConfig();
        config.setCleanCookies(true);
        config.setCleanLocalStorage(true);
        config.setCleanSessionStorage(true);
        config.setCleanCache(true);
        config.setScreenshotBeforeCleanup(true);

        if (instanceConfig != null && instanceConfig.getStateDir() != null) {
            config.setSaveState(true);
            config.setStateDir(instanceConfig.getStateDir());
        }

        return config;
    }

    private void initialize() {
        if (cleanupConfig.getScreenshotDir() == null && instanceConfig != null) {
            cleanupConfig.setScreenshotDir(instanceConfig.getScreenshotDir());
        }
        initialized.set(true);
        logger.debug("ResourceCleaner initialized");
    }

    // ==================== 清理方法 ====================

    /**
     * 执行全面清理
     */
    public void cleanup() {
        cleanup(cleanupConfig);
    }

    /**
     * 执行指定配置的清理
     */
    public void cleanup(CleanupConfig config) {
        if (driver == null) {
            logger.warn("Cannot cleanup: WebDriver is null");
            return;
        }

        logger.info("Starting resource cleanup for instance: {}", 
            instanceConfig != null ? instanceConfig.getInstanceId() : "unknown");

        try {
            // 截图（清理前）
            if (config.isScreenshotBeforeCleanup()) {
                takeScreenshot("before-cleanup");
            }

            // 保存状态
            if (config.isSaveState()) {
                saveState();
            }

            // 获取当前URL（用于清理特定域名的数据）
            String currentUrl = getCurrentUrl();

            // 清理所有存储
            cleanupAllStorage(currentUrl);

            // 清理缓存
            if (config.isCleanCache()) {
                cleanupCache();
            }

            // 清理Service Workers
            if (config.isCleanServiceWorkers()) {
                cleanupServiceWorkers();
            }

            // 清理Application Cache
            if (config.isCleanApplicationCache()) {
                cleanupApplicationCache();
            }

            // 清理表单数据
            if (config.isCleanFormData()) {
                cleanupFormData();
            }

            // 清理Cookies
            if (config.isCleanCookies()) {
                cleanupCookies(currentUrl);
            }

            lastCleanupTime = Instant.now();
            logger.info("Resource cleanup completed");

        } catch (Exception e) {
            logger.error("Error during resource cleanup", e);
        }
    }

    /**
     * 清理特定域名的存储数据
     */
    public void cleanupDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            cleanup();
            return;
        }

        logger.info("Cleaning resources for domain: {}", domain);

        try {
            // 清理LocalStorage
            if (cleanupConfig.isCleanLocalStorage()) {
                executeJs(
                    "try {" +
                    "  var keys = Object.keys(localStorage);" +
                    "  keys.forEach(function(key) {" +
                    "    localStorage.removeItem(key);" +
                    "  });" +
                    "} catch(e) {}"
                );
            }

            // 清理SessionStorage
            if (cleanupConfig.isCleanSessionStorage()) {
                executeJs(
                    "try {" +
                    "  var keys = Object.keys(sessionStorage);" +
                    "  keys.forEach(function(key) {" +
                    "    sessionStorage.removeItem(key);" +
                    "  });" +
                    "} catch(e) {}"
                );
            }

            // 清理IndexedDB
            if (cleanupConfig.isCleanIndexedDB()) {
                executeJs(
                    "try {" +
                    "  indexedDB.databases().then(function(dbs) {" +
                    "    dbs.forEach(function(db) {" +
                    "      indexedDB.deleteDatabase(db.name);" +
                    "    });" +
                    "  });" +
                    "} catch(e) {}"
                );
            }

            // 清理Cookies
            if (cleanupConfig.isCleanCookies()) {
                executeJs(
                    "document.cookie.split(';').forEach(function(c) {" +
                    "  document.cookie = c.replace(/^ +/, '').replace(/=.*/, '=;expires=' + new Date().toUTCString() + ';path=/');" +
                    "});"
                );
            }

        } catch (Exception e) {
            logger.warn("Error cleaning domain resources", e);
        }
    }

    /**
     * 清理所有存储
     */
    private void cleanupAllStorage(String url) {
        try {
            // 清理LocalStorage
            if (cleanupConfig.isCleanLocalStorage()) {
                executeJs(
                    "try {" +
                    "  localStorage.clear();" +
                    "} catch(e) { console.log('LocalStorage cleanup failed:', e); }"
                );
            }

            // 清理SessionStorage
            if (cleanupConfig.isCleanSessionStorage()) {
                executeJs(
                    "try {" +
                    "  sessionStorage.clear();" +
                    "} catch(e) { console.log('SessionStorage cleanup failed:', e); }"
                );
            }

            // 清理IndexedDB
            if (cleanupConfig.isCleanIndexedDB()) {
                executeJs(
                    "try {" +
                    "  indexedDB.databases().then(function(databases) {" +
                    "    databases.forEach(function(db) {" +
                    "      if (db.name) {" +
                    "        var req = indexedDB.deleteDatabase(db.name);" +
                    "        req.onerror = function() {};" +
                    "      }" +
                    "    });" +
                    "  });" +
                    "} catch(e) { console.log('IndexedDB cleanup failed:', e); }"
                );
            }

            // 清理WebSQL
            if (cleanupConfig.isCleanWebSQL()) {
                executeJs(
                    "try {" +
                    "  if (openDatabase) {" +
                    "    openDatabase('temp', '1.0', 'temp', 1024, function(db) {" +
                    "      db.transaction(function(tx) {" +
                    "        tx.executeSql('SELECT name FROM sqlite_master WHERE type=\"table\"', [], function(tx, results) {" +
                    "          for (var i = 0; i < results.rows.length; i++) {" +
                    "            var table = results.rows.item(i).name;" +
                    "            tx.executeSql('DROP TABLE IF EXISTS ' + table);" +
                    "          }" +
                    "        });" +
                    "      });" +
                    "    });" +
                    "  }" +
                    "} catch(e) { console.log('WebSQL cleanup failed:', e); }"
                );
            }

            logger.debug("All storage cleaned");
        } catch (Exception e) {
            logger.warn("Error cleaning storage", e);
        }
    }

    /**
     * 清理缓存
     */
    private void cleanupCache() {
        try {
            executeJs(
                "try {" +
                "  if (caches) {" +
                "    caches.keys().then(function(names) {" +
                "      names.forEach(function(name) {" +
                "        caches.delete(name);" +
                "      });" +
                "    });" +
                "  }" +
                "} catch(e) { console.log('Cache cleanup failed:', e); }"
            );
            logger.debug("Cache cleaned");
        } catch (Exception e) {
            logger.warn("Error cleaning cache", e);
        }
    }

    /**
     * 清理Service Workers
     */
    private void cleanupServiceWorkers() {
        try {
            executeJs(
                "try {" +
                "  if (navigator.serviceWorker) {" +
                "    navigator.serviceWorker.getRegistrations().then(function(registrations) {" +
                "      registrations.forEach(function(registration) {" +
                "        registration.unregister();" +
                "      });" +
                "    });" +
                "  }" +
                "} catch(e) { console.log('ServiceWorker cleanup failed:', e); }"
            );
            logger.debug("Service Workers cleaned");
        } catch (Exception e) {
            logger.warn("Error cleaning service workers", e);
        }
    }

    /**
     * 清理Application Cache
     */
    private void cleanupApplicationCache() {
        try {
            executeJs(
                "try {" +
                "  if (applicationCache) {" +
                "    applicationCache.update();" +
                "    if (applicationCache.status === applicationCache.UPDATEREADY) {" +
                "      applicationCache.swapCache();" +
                "    }" +
                "  }" +
                "} catch(e) { console.log('ApplicationCache cleanup failed:', e); }"
            );
            logger.debug("Application Cache cleaned");
        } catch (Exception e) {
            logger.warn("Error cleaning application cache", e);
        }
    }

    /**
     * 清理表单数据
     */
    private void cleanupFormData() {
        try {
            executeJs(
                "try {" +
                "  var forms = document.querySelectorAll('form');" +
                "  forms.forEach(function(form) {" +
                "    form.reset();" +
                "  });" +
                "} catch(e) { console.log('Form data cleanup failed:', e); }"
            );
            logger.debug("Form data cleaned");
        } catch (Exception e) {
            logger.warn("Error cleaning form data", e);
        }
    }

    /**
     * 清理Cookies
     */
    private void cleanupCookies(String url) {
        try {
            driver.manage().deleteAllCookies();
            logger.debug("Cookies cleaned");
        } catch (Exception e) {
            logger.warn("Error cleaning cookies", e);
        }
    }

    // ==================== 状态管理 ====================

    /**
     * 保存当前状态
     */
    public void saveState() {
        if (cleanupConfig.getStateDir() == null) {
            logger.warn("State directory not configured");
            return;
        }

        try {
            java.io.File stateDir = new java.io.File(cleanupConfig.getStateDir());
            if (!stateDir.exists()) {
                stateDir.mkdirs();
            }

            String instanceId = instanceConfig != null ? instanceConfig.getInstanceId() : "unknown";
            java.io.File stateFile = new java.io.File(stateDir, "state-" + instanceId + ".json");

            Map<String, Object> state = new HashMap<>();
            state.put("timestamp", Instant.now().toString());
            state.put("localStorage", getLocalStorageData());
            state.put("cookies", getCookiesData());

            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(state);

            java.nio.file.Files.write(
                stateFile.toPath(),
                json.getBytes(StandardCharsets.UTF_8)
            );

            logger.info("State saved to: {}", stateFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Error saving state", e);
        }
    }

    /**
     * 恢复状态
     */
    public void restoreState() {
        if (cleanupConfig.getStateDir() == null) {
            logger.warn("State directory not configured");
            return;
        }

        try {
            String instanceId = instanceConfig != null ? instanceConfig.getInstanceId() : "unknown";
            java.io.File stateFile = new java.io.File(cleanupConfig.getStateDir(), "state-" + instanceId + ".json");

            if (!stateFile.exists()) {
                logger.warn("State file not found: {}", stateFile.getAbsolutePath());
                return;
            }

            String json = java.nio.file.Files.readString(stateFile.toPath());
            Map<String, Object> state = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, Map.class);

            // 恢复LocalStorage
            if (state.containsKey("localStorage")) {
                restoreLocalStorage((Map<String, String>) state.get("localStorage"));
            }

            // 恢复Cookies
            if (state.containsKey("cookies")) {
                restoreCookies((List<Map<String, Object>>) state.get("cookies"));
            }

            logger.info("State restored from: {}", stateFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Error restoring state", e);
        }
    }

    /**
     * 获取LocalStorage数据
     */
    private Map<String, String> getLocalStorageData() {
        try {
            return (Map<String, String>) executeJs(
                "var result = {};" +
                "try {" +
                "  for (var i = 0; i < localStorage.length; i++) {" +
                "    var key = localStorage.key(i);" +
                "    result[key] = localStorage.getItem(key);" +
                "  }" +
                "} catch(e) {}" +
                "return result;"
            );
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 恢复LocalStorage数据
     */
    @SuppressWarnings("unchecked")
    private void restoreLocalStorage(Map<String, String> data) {
        if (data == null) return;

        try {
            StringBuilder js = new StringBuilder("try {");
            for (Map.Entry<String, String> entry : data.entrySet()) {
                js.append("localStorage.setItem('").append(escapeJs(entry.getKey()))
                   .append("', '").append(escapeJs(entry.getValue())).append("');");
            }
            js.append("} catch(e) {}");
            executeJs(js.toString());
        } catch (Exception e) {
            logger.warn("Error restoring LocalStorage", e);
        }
    }

    /**
     * 获取Cookies数据
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCookiesData() {
        try {
            return (List<Map<String, Object>>) executeJs(
                "var result = [];" +
                "var cookies = document.cookie.split(';');" +
                "cookies.forEach(function(cookie) {" +
                "  var parts = cookie.split('=');" +
                "  if (parts.length >= 2) {" +
                "    result.push({name: parts[0].trim(), value: parts.slice(1).join('=')});" +
                "  }" +
                "});" +
                "return result;"
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 恢复Cookies数据
     */
    @SuppressWarnings("unchecked")
    private void restoreCookies(List<Map<String, Object>> cookies) {
        if (cookies == null) return;

        try {
            for (Map<String, Object> cookie : cookies) {
                String js = String.format(
                    "document.cookie = '%s=%s';",
                    escapeJs((String) cookie.get("name")),
                    escapeJs((String) cookie.get("value"))
                );
                executeJs(js);
            }
        } catch (Exception e) {
            logger.warn("Error restoring Cookies", e);
        }
    }

    // ==================== 辅助方法 ====================

    private String getCurrentUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "";
        }
    }

    private Object executeJs(String script) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            return js.executeScript(script);
        } catch (Exception e) {
            logger.debug("JavaScript execution failed: {}", e.getMessage());
            return null;
        }
    }

    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    private void takeScreenshot(String prefix) {
        if (cleanupConfig.getScreenshotDir() == null || !(driver instanceof org.openqa.selenium.TakesScreenshot)) {
            return;
        }

        try {
            java.io.File dir = new java.io.File(cleanupConfig.getScreenshotDir());
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String instanceId = instanceConfig != null ? instanceConfig.getInstanceId() : "unknown";
            String filename = String.format("%s-%s-%d.png", prefix, instanceId, System.currentTimeMillis());
            java.io.File screenshot = ((org.openqa.selenium.TakesScreenshot) driver)
                .getScreenshotAs(org.openqa.selenium.OutputType.FILE);
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

    // ==================== 公开方法 ====================

    public Instant getLastCleanupTime() {
        return lastCleanupTime;
    }

    public CleanupConfig getCleanupConfig() {
        return cleanupConfig;
    }

    public void updateCleanupConfig(CleanupConfig config) {
        this.cleanupConfig = config;
    }

    /**
     * 检查是否需要自动清理
     */
    public boolean needsAutoCleanup() {
        if (cleanupConfig.getAutoCleanupInterval() == null) {
            return false;
        }
        Duration sinceLastCleanup = Duration.between(lastCleanupTime, Instant.now());
        return sinceLastCleanup.compareTo(cleanupConfig.getAutoCleanupInterval()) > 0;
    }

    /**
     * 执行自动清理
     */
    public void autoCleanupIfNeeded() {
        if (needsAutoCleanup()) {
            cleanup();
        }
    }

    /**
     * 深度清理（清理所有可清理项）
     */
    public void deepCleanup() {
        cleanup(CleanupConfig.fullCleanup());
    }

    /**
     * 快速清理（仅清理存储）
     */
    public void quickCleanup() {
        cleanup(CleanupConfig.lightCleanup());
    }
}
