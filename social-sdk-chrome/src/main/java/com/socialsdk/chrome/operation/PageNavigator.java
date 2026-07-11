package com.socialsdk.chrome.operation;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 页面导航器
 * 
 * 功能:
 * - 页面导航（前进/后退/刷新）
 * - 智能等待页面加载
 * - 导航历史管理
 * - 页面状态监控
 * - 导航事件回调
 * 
 * @author Social SDK
 */
public class PageNavigator implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PageNavigator.class);

    // ==================== 导航配置 ====================

    public static class NavigationConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 页面加载超时时间（秒）
         */
        private int pageLoadTimeout = 30;

        /**
         * 脚本执行超时时间（秒）
         */
        private int scriptTimeout = 15;

        /**
         * 隐式等待时间（秒）
         */
        private int implicitWait = 0;

        /**
         * 是否等待DOM就绪
         */
        private boolean waitForDomReady = true;

        /**
         * 是否等待网络空闲
         */
        private boolean waitForNetworkIdle = false;

        /**
         * 网络空闲等待时间（秒）
         */
        private int networkIdleTimeout = 5;

        /**
         * 最大重试次数
         */
        private int maxRetries = 1;

        /**
         * 是否记录导航历史
         */
        private boolean recordHistory = true;

        /**
         * 最大历史记录数
         */
        private int maxHistorySize = 50;

        /**
         * 是否在导航前清除本地存储
         */
        private boolean clearLocalStorageBeforeNav = false;

        /**
         * 是否在导航前清除会话存储
         */
        private boolean clearSessionStorageBeforeNav = false;

        // Getters and Setters
        public int getPageLoadTimeout() { return pageLoadTimeout; }
        public void setPageLoadTimeout(int t) { this.pageLoadTimeout = t; }
        public int getScriptTimeout() { return scriptTimeout; }
        public void setScriptTimeout(int t) { this.scriptTimeout = t; }
        public int getImplicitWait() { return implicitWait; }
        public void setImplicitWait(int w) { this.implicitWait = w; }
        public boolean isWaitForDomReady() { return waitForDomReady; }
        public void setWaitForDomReady(boolean b) { this.waitForDomReady = b; }
        public boolean isWaitForNetworkIdle() { return waitForNetworkIdle; }
        public void setWaitForNetworkIdle(boolean b) { this.waitForNetworkIdle = b; }
        public int getNetworkIdleTimeout() { return networkIdleTimeout; }
        public void setNetworkIdleTimeout(int t) { this.networkIdleTimeout = t; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int n) { this.maxRetries = n; }
        public boolean isRecordHistory() { return recordHistory; }
        public void setRecordHistory(boolean b) { this.recordHistory = b; }
        public int getMaxHistorySize() { return maxHistorySize; }
        public void setMaxHistorySize(int n) { this.maxHistorySize = n; }
        public boolean isClearLocalStorageBeforeNav() { return clearLocalStorageBeforeNav; }
        public void setClearLocalStorageBeforeNav(boolean b) { this.clearLocalStorageBeforeNav = b; }
        public boolean isClearSessionStorageBeforeNav() { return clearSessionStorageBeforeNav; }
        public void setClearSessionStorageBeforeNav(boolean b) { this.clearSessionStorageBeforeNav = b; }

        public static NavigationConfig defaultConfig() {
            return new NavigationConfig();
        }

        public static NavigationConfig aggressiveConfig() {
            NavigationConfig c = new NavigationConfig();
            c.setPageLoadTimeout(60);
            c.setWaitForNetworkIdle(true);
            c.setMaxRetries(2);
            return c;
        }

        public static NavigationConfig quickConfig() {
            NavigationConfig c = new NavigationConfig();
            c.setPageLoadTimeout(10);
            c.setWaitForDomReady(false);
            c.setMaxRetries(0);
            return c;
        }
    }

    // ==================== 导航状态 ====================

    public enum NavigationState {
        IDLE,
        NAVIGATING,
        PAGE_LOADING,
        JS_EXECUTING,
        NETWORK_ACTIVE,
        COMPLETE,
        FAILED,
        TIMEOUT
    }

    // ==================== 导航记录 ====================

    public static class NavigationRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String url;
        private String title;
        private Instant startTime;
        private Instant endTime;
        private long durationMs;
        private NavigationState state;
        private String previousUrl;
        private NavigationType type;
        private boolean success;
        private String error;
        private Map<String, Object> metadata;

        public enum NavigationType {
            NAVIGATE_TO,      // 直接导航
            FORWARD,          // 前进
            BACK,             // 后退
            REFRESH,          // 刷新
            SCRIPT_NAVIGATE   // 通过脚本导航
        }

        public NavigationRecord() {
            this.id = UUID.randomUUID().toString();
            this.startTime = Instant.now();
            this.metadata = new HashMap<>();
        }

        // Getters and Setters
        public String getId() { return id; }
        public String getUrl() { return url; }
        public void setUrl(String u) { this.url = u; }
        public String getTitle() { return title; }
        public void setTitle(String t) { this.title = t; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant t) { this.startTime = t; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant t) { this.endTime = t; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long ms) { this.durationMs = ms; }
        public NavigationState getState() { return state; }
        public void setState(NavigationState s) { this.state = s; }
        public String getPreviousUrl() { return previousUrl; }
        public void setPreviousUrl(String u) { this.previousUrl = u; }
        public NavigationType getType() { return type; }
        public void setType(NavigationType t) { this.type = t; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean s) { this.success = s; }
        public String getError() { return error; }
        public void setError(String e) { this.error = e; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> m) { this.metadata = m; }

        public void complete(boolean success) {
            this.endTime = Instant.now();
            this.durationMs = Duration.between(startTime, endTime).toMillis();
            this.success = success;
        }
    }

    // ==================== 回调接口 ====================

    @FunctionalInterface
    public interface NavigationCallback {
        void onNavigation(NavigationRecord record);
    }

    @FunctionalInterface
    public interface StateChangeCallback {
        void onStateChange(NavigationState oldState, NavigationState newState);
    }

    @FunctionalInterface
    public interface UrlChangeCallback {
        void onUrlChange(String oldUrl, String newUrl);
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final NavigationConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile NavigationState currentState = NavigationState.IDLE;
    private volatile String currentUrl = "";
    private volatile String previousUrl = "";

    private final List<NavigationRecord> navigationHistory = Collections.synchronizedList(new ArrayList<>());
    private final Deque<String> urlHistory = new ArrayDeque<>();

    private final ConcurrentLinkedQueue<NavigationCallback> navCallbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<StateChangeCallback> stateCallbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UrlChangeCallback> urlCallbacks = new ConcurrentLinkedQueue<>();

    // ==================== 构造函数 ====================

    public PageNavigator(WebDriver driver) {
        this(driver, NavigationConfig.defaultConfig());
    }

    public PageNavigator(WebDriver driver, NavigationConfig config) {
        this.driver = driver;
        this.config = config;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(config.getPageLoadTimeout()));

        // 设置超时
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getPageLoadTimeout()));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(config.getScriptTimeout()));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.getImplicitWait()));

        logger.info("PageNavigator initialized");
    }

    // ==================== 核心导航方法 ====================

    /**
     * 导航到URL
     */
    public NavigationRecord navigateTo(String url) {
        return navigateTo(url, NavigationRecord.NavigationType.NAVIGATE_TO);
    }

    /**
     * 导航到URL（指定类型）
     */
    public NavigationRecord navigateTo(String url, NavigationRecord.NavigationType type) {
        clearStorageIfNeeded();
        
        String oldUrl = getCurrentUrl();
        NavigationRecord record = createNavigationRecord(url, type);
        record.setPreviousUrl(oldUrl);

        // 更新状态
        setState(NavigationState.NAVIGATING);

        try {
            driver.get(url);
            currentUrl = url;

            // 等待加载完成
            waitForLoadComplete();

            record.setUrl(url);
            record.setTitle(driver.getTitle());
            record.complete(true);
            record.setState(currentState);

            // 更新URL历史
            if (config.isRecordHistory()) {
                urlHistory.add(oldUrl);
                if (urlHistory.size() > config.getMaxHistorySize()) {
                    urlHistory.pollFirst();
                }
            }

            notifyNavigation(record);
            logger.info("Navigated to: {} ({}ms)", url, record.getDurationMs());

            return record;
        } catch (TimeoutException e) {
            return handleNavigationError(record, "Page load timeout", e);
        } catch (WebDriverException e) {
            return handleNavigationError(record, "Navigation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 导航到URL（带重试）
     */
    public NavigationRecord navigateToWithRetry(String url, int maxRetries) {
        Exception lastError = null;

        for (int i = 0; i <= maxRetries; i++) {
            try {
                return navigateTo(url);
            } catch (Exception e) {
                lastError = e;
                logger.warn("Navigation attempt {} failed: {}", i + 1, e.getMessage());
                if (i < maxRetries) {
                    try {
                        Thread.sleep(1000 * (i + 1)); // 递增等待时间
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("Navigation failed after " + (maxRetries + 1) + " attempts", lastError);
    }

    /**
     * 后退
     */
    public NavigationRecord back() {
        if (urlHistory.isEmpty()) {
            logger.warn("No history available for back navigation");
            return null;
        }

        String targetUrl = urlHistory.peekLast();
        NavigationRecord record = createNavigationRecord(targetUrl, NavigationRecord.NavigationType.BACK);

        try {
            driver.navigate().back();
            waitForLoadComplete();

            record.setUrl(getCurrentUrl());
            record.setTitle(driver.getTitle());
            record.complete(true);
            record.setState(currentState);

            notifyNavigation(record);
            logger.info("Navigated back to: {} ({}ms)", record.getUrl(), record.getDurationMs());

            return record;
        } catch (Exception e) {
            return handleNavigationError(record, "Back navigation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 前进
     */
    public NavigationRecord forward() {
        NavigationRecord record = createNavigationRecord(getCurrentUrl(), NavigationRecord.NavigationType.FORWARD);

        try {
            driver.navigate().forward();
            waitForLoadComplete();

            record.setUrl(getCurrentUrl());
            record.setTitle(driver.getTitle());
            record.complete(true);
            record.setState(currentState);

            notifyNavigation(record);
            logger.info("Navigated forward to: {} ({}ms)", record.getUrl(), record.getDurationMs());

            return record;
        } catch (Exception e) {
            return handleNavigationError(record, "Forward navigation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 刷新页面
     */
    public NavigationRecord refresh() {
        return refresh(true);
    }

    /**
     * 刷新页面（指定是否等待加载）
     */
    public NavigationRecord refresh(boolean waitForLoad) {
        String oldUrl = getCurrentUrl();
        NavigationRecord record = createNavigationRecord(oldUrl, NavigationRecord.NavigationType.REFRESH);

        try {
            driver.navigate().refresh();

            if (waitForLoad) {
                waitForLoadComplete();
            }

            record.setUrl(getCurrentUrl());
            record.setTitle(driver.getTitle());
            record.complete(true);
            record.setState(currentState);

            notifyNavigation(record);
            logger.info("Refreshed page: {} ({}ms)", record.getUrl(), record.getDurationMs());

            return record;
        } catch (Exception e) {
            return handleNavigationError(record, "Refresh failed: " + e.getMessage(), e);
        }
    }

    // ==================== 等待方法 ====================

    /**
     * 等待页面加载完成
     */
    public void waitForLoadComplete() {
        setState(NavigationState.PAGE_LOADING);

        try {
            // 等待文档就绪
            if (config.isWaitForDomReady()) {
                wait.until(driver -> {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    String state = (String) js.executeScript("return document.readyState;");
                    return "complete".equals(state);
                });
            }

            // 等待网络空闲
            if (config.isWaitForNetworkIdle()) {
                waitForNetworkIdle(config.getNetworkIdleTimeout());
            }

            setState(NavigationState.COMPLETE);
        } catch (TimeoutException e) {
            setState(NavigationState.TIMEOUT);
            logger.warn("Page load timeout");
        } catch (Exception e) {
            setState(NavigationState.FAILED);
            logger.warn("Page load failed: {}", e.getMessage());
        }
    }

    /**
     * 等待网络空闲
     */
    public void waitForNetworkIdle() {
        waitForNetworkIdle(config.getNetworkIdleTimeout());
    }

    /**
     * 等待网络空闲（指定超时）
     */
    public void waitForNetworkIdle(int timeoutSeconds) {
        try {
            setState(NavigationState.NETWORK_ACTIVE);

            wait.until(driver -> {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Boolean isIdle = (Boolean) js.executeScript(
                    "return window.performance.timing.loadEventEnd > 0 && " +
                    "(window.performance.timing.domContentLoadedEventEnd - window.performance.timing.navigationStart) > 0;"
                );
                return Boolean.TRUE.equals(isIdle);
            });

            setState(NavigationState.COMPLETE);
        } catch (TimeoutException e) {
            setState(NavigationState.NETWORK_ACTIVE);
            logger.debug("Network did not idle within timeout");
        }
    }

    /**
     * 等待元素存在
     */
    public WebElement waitForElement(By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    /**
     * 等待元素可见
     */
    public WebElement waitForElementVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * 等待元素可点击
     */
    public WebElement waitForElementClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * 等待元素消失
     */
    public boolean waitForElementAbsent(By locator) {
        return wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    /**
     * 等待URL变化
     */
    public boolean waitForUrlChange(String expectedUrl) {
        return wait.until(driver -> {
            String currentUrl = driver.getCurrentUrl();
            return currentUrl != null && currentUrl.contains(expectedUrl);
        });
    }

    /**
     * 等待URL匹配正则
     */
    public boolean waitForUrlMatch(String regex) {
        return wait.until(driver -> {
            String currentUrl = driver.getCurrentUrl();
            return currentUrl != null && currentUrl.matches(regex);
        });
    }

    /**
     * 等待标题匹配
     */
    public boolean waitForTitleMatch(String regex) {
        return wait.until(driver -> {
            String title = driver.getTitle();
            return title != null && title.matches(regex);
        });
    }

    // ==================== 查询方法 ====================

    /**
     * 获取当前URL
     */
    public String getCurrentUrl() {
        try {
            String url = driver.getCurrentUrl();
            currentUrl = url != null ? url : "";
            return currentUrl;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取页面标题
     */
    public String getPageTitle() {
        try {
            return driver.getTitle();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取导航状态
     */
    public NavigationState getState() {
        return currentState;
    }

    /**
     * 获取导航历史
     */
    public List<NavigationRecord> getNavigationHistory() {
        return new ArrayList<>(navigationHistory);
    }

    /**
     * 获取URL历史
     */
    public List<String> getUrlHistory() {
        return new ArrayList<>(urlHistory);
    }

    /**
     * 获取历史记录数
     */
    public int getHistorySize() {
        return urlHistory.size();
    }

    /**
     * 检查是否可以后退
     */
    public boolean canGoBack() {
        return !urlHistory.isEmpty();
    }

    /**
     * 检查URL是否匹配
     */
    public boolean isUrlMatching(String pattern) {
        return getCurrentUrl() != null && getCurrentUrl().matches(pattern);
    }

    // ==================== 辅助方法 ====================

    private NavigationRecord createNavigationRecord(String url, NavigationRecord.NavigationType type) {
        NavigationRecord record = new NavigationRecord();
        record.setUrl(url);
        record.setType(type);
        record.setState(currentState);
        return record;
    }

    private NavigationRecord handleNavigationError(NavigationRecord record, String errorMessage, Exception e) {
        record.complete(false);
        record.setError(errorMessage);
        record.setState(NavigationState.FAILED);

        notifyNavigation(record);
        logger.error("Navigation failed: {} -> {}", errorMessage, record.getUrl());

        return record;
    }

    private void clearStorageIfNeeded() {
        if (config.isClearLocalStorageBeforeNav() || config.isClearSessionStorageBeforeNav()) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            if (config.isClearLocalStorageBeforeNav()) {
                js.executeScript("localStorage.clear();");
            }
            if (config.isClearSessionStorageBeforeNav()) {
                js.executeScript("sessionStorage.clear();");
            }
        }
    }

    private void setState(NavigationState newState) {
        NavigationState oldState = currentState;
        currentState = newState;

        // 检测URL变化
        String newUrl = getCurrentUrl();
        if (!previousUrl.equals(newUrl)) {
            notifyUrlChange(previousUrl, newUrl);
            previousUrl = newUrl;
        }

        // 通知状态变化
        stateCallbacks.forEach(cb -> {
            try { cb.onStateChange(oldState, newState); }
            catch (Exception e) { logger.warn("State callback: {}", e.getMessage()); }
        });
    }

    private void notifyNavigation(NavigationRecord record) {
        if (config.isRecordHistory()) {
            navigationHistory.add(record);
            while (navigationHistory.size() > config.getMaxHistorySize()) {
                navigationHistory.remove(0);
            }
        }

        navCallbacks.forEach(cb -> {
            try { cb.onNavigation(record); }
            catch (Exception e) { logger.warn("Navigation callback: {}", e.getMessage()); }
        });
    }

    private void notifyUrlChange(String oldUrl, String newUrl) {
        urlCallbacks.forEach(cb -> {
            try { cb.onUrlChange(oldUrl, newUrl); }
            catch (Exception e) { logger.warn("URL change callback: {}", e.getMessage()); }
        });
    }

    // ==================== 回调注册 ====================

    public PageNavigator onNavigation(NavigationCallback callback) {
        navCallbacks.offer(callback);
        return this;
    }

    public PageNavigator onStateChange(StateChangeCallback callback) {
        stateCallbacks.offer(callback);
        return this;
    }

    public PageNavigator onUrlChange(UrlChangeCallback callback) {
        urlCallbacks.offer(callback);
        return this;
    }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentUrl", getCurrentUrl());
        stats.put("currentState", currentState);
        stats.put("navigationCount", navigationHistory.size());
        
        long totalDuration = 0;
        int successCount = 0;
        for (NavigationRecord record : navigationHistory) {
            totalDuration += record.getDurationMs();
            if (record.isSuccess()) successCount++;
        }
        stats.put("totalNavigationTimeMs", totalDuration);
        stats.put("successRate", navigationHistory.isEmpty() ? 0 : 
            (double) successCount / navigationHistory.size() * 100);
        
        return stats;
    }

    public void printStatistics() {
        Map<String, Object> stats = getStatistics();
        logger.info("=== Navigation Statistics ===");
        logger.info("Current URL: {}", stats.get("currentUrl"));
        logger.info("Navigation Count: {}, Success Rate: {}%", 
            stats.get("navigationCount"), String.format("%.1f", stats.get("successRate")));
    }

    // ==================== AutoCloseable ====================

    @Override
    public void close() {
        navCallbacks.clear();
        stateCallbacks.clear();
        urlCallbacks.clear();
        logger.info("PageNavigator closed");
    }

    // ==================== 静态工厂方法 ====================

    public static PageNavigator create(WebDriver driver) {
        return new PageNavigator(driver);
    }

    public static PageNavigator create(WebDriver driver, NavigationConfig config) {
        return new PageNavigator(driver, config);
    }

    public static PageNavigator createAggressive(WebDriver driver) {
        return new PageNavigator(driver, NavigationConfig.aggressiveConfig());
    }

    public static PageNavigator createQuick(WebDriver driver) {
        return new PageNavigator(driver, NavigationConfig.quickConfig());
    }
}
