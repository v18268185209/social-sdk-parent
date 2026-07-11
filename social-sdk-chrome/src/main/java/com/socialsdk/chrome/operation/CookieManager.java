package com.socialsdk.chrome.operation;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cookie管理器
 * 提供Cookies的增删改查、保存加载、模拟登录等功能
 */
public class CookieManager {

    private static final Logger logger = LoggerFactory.getLogger(CookieManager.class);

    // ==================== Cookie配置 ====================

    public static class CookieConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean acceptAllCookies = true;
        private boolean httpOnlyEnabled = true;
        private boolean secureEnabled = false;
        private String defaultDomain;
        private String defaultPath = "/";
        private boolean autoSave = true;
        private Duration autoSaveInterval = Duration.ofMinutes(5);
        private boolean ignoreParseErrors = true;

        public boolean isAcceptAllCookies() { return acceptAllCookies; }
        public void setAcceptAllCookies(boolean acceptAllCookies) { this.acceptAllCookies = acceptAllCookies; }
        public boolean isHttpOnlyEnabled() { return httpOnlyEnabled; }
        public void setHttpOnlyEnabled(boolean httpOnlyEnabled) { this.httpOnlyEnabled = httpOnlyEnabled; }
        public boolean isSecureEnabled() { return secureEnabled; }
        public void setSecureEnabled(boolean secureEnabled) { this.secureEnabled = secureEnabled; }
        public String getDefaultDomain() { return defaultDomain; }
        public void setDefaultDomain(String defaultDomain) { this.defaultDomain = defaultDomain; }
        public String getDefaultPath() { return defaultPath; }
        public void setDefaultPath(String defaultPath) { this.defaultPath = defaultPath; }
        public boolean isAutoSave() { return autoSave; }
        public void setAutoSave(boolean autoSave) { this.autoSave = autoSave; }
        public Duration getAutoSaveInterval() { return autoSaveInterval; }
        public void setAutoSaveInterval(Duration autoSaveInterval) { this.autoSaveInterval = autoSaveInterval; }
        public boolean isIgnoreParseErrors() { return ignoreParseErrors; }
        public void setIgnoreParseErrors(boolean ignoreParseErrors) { this.ignoreParseErrors = ignoreParseErrors; }

        public static CookieConfig defaultConfig() { return new CookieConfig(); }
        public static CookieConfig secureConfig() {
            CookieConfig config = new CookieConfig();
            config.setSecureEnabled(true);
            config.setHttpOnlyEnabled(true);
            return config;
        }
    }

    // ==================== 保存的Cookie数据 ====================

    public static class SavedCookieData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String value;
        private String domain;
        private String path;
        private String sameSite;
        private boolean secure;
        private boolean httpOnly;
        private LocalDateTime expiry;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public boolean isHttpOnly() { return httpOnly; }
        public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
        public LocalDateTime getExpiry() { return expiry; }
        public void setExpiry(LocalDateTime expiry) { this.expiry = expiry; }

        public Cookie toSeleniumCookie() {
            Cookie.Builder builder = new Cookie.Builder(name, value)
                .path(path != null ? path : "/")
                .domain(domain != null ? domain : "");

            // Note: secure and httpOnly are set based on browser defaults
            // Explicit setting may not be supported in all Selenium versions

            if (expiry != null) {
                builder.expiresOn(Date.from(expiry.atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }

            return builder.build();
        }

        public static SavedCookieData fromSeleniumCookie(Cookie cookie) {
            SavedCookieData data = new SavedCookieData();
            data.setName(cookie.getName());
            data.setValue(cookie.getValue());
            data.setDomain(cookie.getDomain());
            data.setPath(cookie.getPath());
            data.setSecure(cookie.isSecure());
            data.setHttpOnly(cookie.isHttpOnly());

            if (cookie.getExpiry() != null) {
                data.setExpiry(
                    LocalDateTime.ofInstant(cookie.getExpiry().toInstant(), java.time.ZoneId.systemDefault())
                );
            }

            return data;
        }
    }

    // ==================== Cookie会话数据 ====================

    public static class CookieSession implements Serializable {
        private static final long serialVersionUID = 1L;

        private String domain;
        private LocalDateTime saveTime;
        private List<SavedCookieData> cookies;
        private String userAgent;

        public CookieSession() {
            this.cookies = new ArrayList<>();
            this.saveTime = LocalDateTime.now();
        }

        public void addCookie(SavedCookieData cookie) { cookies.add(cookie); }
        public List<SavedCookieData> getCookies() { return cookies; }
        public void setCookies(List<SavedCookieData> cookies) { this.cookies = cookies; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public LocalDateTime getSaveTime() { return saveTime; }
        public void setSaveTime(LocalDateTime saveTime) { this.saveTime = saveTime; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final CookieConfig config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, SavedCookieData> cookieCache = new ConcurrentHashMap<>();
    private volatile Instant lastModified;

    // ==================== 构造函数 ====================

    public CookieManager(WebDriver driver) {
        this(driver, CookieConfig.defaultConfig());
    }

    public CookieManager(WebDriver driver, CookieConfig config) {
        if (driver == null) {
            throw new IllegalArgumentException("WebDriver cannot be null");
        }
        this.driver = driver;
        this.config = config;
        this.lastModified = Instant.now();
        initialize();
    }

    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            logger.debug("CookieManager initialized");
        }
    }

    // ==================== 添加Cookie ====================

    public CookieManager addCookie(String name, String value) {
        return addCookie(name, value, config.getDefaultDomain(), config.getDefaultPath());
    }

    public CookieManager addCookie(String name, String value, String domain, String path) {
        try {
            Cookie.Builder builder = new Cookie.Builder(name, value)
                .domain(domain != null ? domain : getCurrentDomain())
                .path(path != null ? path : "/");

            Cookie cookie = builder.build();
            driver.manage().addCookie(cookie);

            cookieCache.put(name, SavedCookieData.fromSeleniumCookie(cookie));
            lastModified = Instant.now();

            logger.debug("Added cookie: {} for domain: {}", name, domain);
        } catch (WebDriverException e) {
            logger.warn("Failed to add cookie: {} - {}", name, e.getMessage());
        }
        return this;
    }

    public CookieManager addCookies(Map<String, String> cookies) {
        String domain = getCurrentDomain();
        cookies.forEach((name, value) -> addCookie(name, value, domain, "/"));
        return this;
    }

    // ==================== 获取Cookie ====================

    public Optional<String> getCookie(String name) {
        try {
            Cookie cookie = driver.manage().getCookieNamed(name);
            if (cookie != null) {
                return Optional.of(cookie.getValue());
            }
        } catch (WebDriverException e) {
            logger.debug("Failed to get cookie: {} - {}", name, e.getMessage());
        }
        return Optional.empty();
    }

    public Map<String, String> getAllCookies() {
        Map<String, String> cookies = new HashMap<>();
        try {
            driver.manage().getCookies().forEach(cookie ->
                cookies.put(cookie.getName(), cookie.getValue())
            );
        } catch (WebDriverException e) {
            logger.warn("Failed to get all cookies: {}", e.getMessage());
        }
        return cookies;
    }

    public boolean hasCookie(String name) {
        return getCookie(name).isPresent();
    }

    public int getCookieCount() {
        try {
            return driver.manage().getCookies().size();
        } catch (WebDriverException e) {
            return 0;
        }
    }

    // ==================== 删除Cookie ====================

    public CookieManager deleteCookie(String name) {
        try {
            driver.manage().deleteCookieNamed(name);
            cookieCache.remove(name);
            lastModified = Instant.now();
            logger.debug("Deleted cookie: {}", name);
        } catch (WebDriverException e) {
            logger.warn("Failed to delete cookie: {} - {}", name, e.getMessage());
        }
        return this;
    }

    public CookieManager deleteAllCookies() {
        try {
            driver.manage().deleteAllCookies();
            cookieCache.clear();
            lastModified = Instant.now();
            logger.info("Deleted all cookies");
        } catch (WebDriverException e) {
            logger.warn("Failed to delete all cookies: {}", e.getMessage());
        }
        return this;
    }

    // ==================== 保存和加载 ====================

    public void saveToFile(String filePath) {
        try {
            CookieSession session = buildSession();

            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(session);

            java.nio.file.Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            logger.info("Cookies saved to: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to save cookies: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save cookies", e);
        }
    }

    public CookieSession loadFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileNotFoundException("Cookie file not found: " + filePath);
            }

            String json = java.nio.file.Files.readString(file.toPath());
            CookieSession session = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, CookieSession.class);

            applySession(session);
            logger.info("Cookies loaded from: {} (count: {})", filePath, session.getCookies().size());
            return session;
        } catch (Exception e) {
            logger.error("Failed to load cookies: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load cookies", e);
        }
    }

    public CookieSession buildSession() {
        CookieSession session = new CookieSession();
        session.setDomain(getCurrentDomain());

        getAllCookies().keySet().stream().map(name ->
            driver.manage().getCookieNamed(name)
        ).filter(Objects::nonNull).forEach(cookie ->
            session.addCookie(SavedCookieData.fromSeleniumCookie(cookie))
        );

        return session;
    }

    public void applySession(CookieSession session) {
        deleteAllCookies();
        session.getCookies().forEach(cookie -> {
            try {
                driver.manage().addCookie(cookie.toSeleniumCookie());
            } catch (Exception e) {
                logger.warn("Failed to apply cookie: {} - {}", cookie.getName(), e.getMessage());
            }
        });
        lastModified = Instant.now();
    }

    // ==================== 辅助方法 ====================

    private String getCurrentDomain() {
        try {
            String url = driver.getCurrentUrl();
            if (url != null && !url.isEmpty()) {
                java.net.URL parsedUrl = new java.net.URL(url);
                return parsedUrl.getHost();
            }
        } catch (Exception e) { /* 忽略 */ }
        return config.getDefaultDomain() != null ? config.getDefaultDomain() : "";
    }

    public CookieManager refresh() {
        cookieCache.clear();
        getAllCookies().keySet().stream().map(name ->
            driver.manage().getCookieNamed(name)
        ).filter(Objects::nonNull).forEach(cookie ->
            cookieCache.put(cookie.getName(), SavedCookieData.fromSeleniumCookie(cookie))
        );
        return this;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    // ==================== 静态方法 ====================

    public static CookieManager create(WebDriver driver) {
        return new CookieManager(driver);
    }

    public static CookieManager create(WebDriver driver, CookieConfig config) {
        return new CookieManager(driver, config);
    }
}