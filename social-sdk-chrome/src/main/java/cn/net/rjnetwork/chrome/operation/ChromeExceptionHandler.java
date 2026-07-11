package cn.net.rjnetwork.chrome.operation;

import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * 异常处理器
 * 
 * 功能:
 * - 全局异常捕获
 - 页面JS异常监控
 - 元素查找失败处理
 - 自定义异常处理规则
 - 异常历史记录
 * 
 * @author Social SDK
 */
public class ChromeExceptionHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ChromeExceptionHandler.class);

    // ==================== 异常配置 ====================

    public static class ExceptionConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 是否启用JS异常捕获
         */
        private boolean captureJsExceptions = true;

        /**
         * 是否捕获页面未处理的异常
         */
        private boolean captureUnhandledExceptions = true;

        /**
         * 是否捕获Promise拒绝
         */
        private boolean capturePromiseRejections = true;

        /**
         * 是否捕获资源加载失败
         */
        private boolean captureResourceErrors = true;

        /**
         * 是否捕获网络错误
         */
        private boolean captureNetworkErrors = true;

        /**
         * 是否启用自动重试
         */
        private boolean enableAutoRetry = false;

        /**
         * 最大重试次数
         */
        private int maxRetryCount = 3;

        /**
         * 重试间隔（毫秒）
         */
        private long retryIntervalMs = 500;

        /**
         * 是否记录异常历史
         */
        private boolean recordHistory = true;

        /**
         * 最大历史记录数
         */
        private int maxHistorySize = 100;

        /**
         * 是否在控制台打印异常
         */
        private boolean printToConsole = true;

        /**
         * 是否在日志中记录异常
         */
        private boolean logExceptions = true;

        /**
         * 忽略的异常类型列表
         */
        private Set<Class<? extends Exception>> ignoredExceptions = new HashSet<>();

        /**
         * 异常处理策略
         */
        private ExceptionHandlingStrategy strategy = ExceptionHandlingStrategy.LOG;

        // Getters and Setters
        public boolean isCaptureJsExceptions() { return captureJsExceptions; }
        public void setCaptureJsExceptions(boolean b) { this.captureJsExceptions = b; }
        public boolean isCaptureUnhandledExceptions() { return captureUnhandledExceptions; }
        public void setCaptureUnhandledExceptions(boolean b) { this.captureUnhandledExceptions = b; }
        public boolean isCapturePromiseRejections() { return capturePromiseRejections; }
        public void setCapturePromiseRejections(boolean b) { this.capturePromiseRejections = b; }
        public boolean isCaptureResourceErrors() { return captureResourceErrors; }
        public void setCaptureResourceErrors(boolean b) { this.captureResourceErrors = b; }
        public boolean isCaptureNetworkErrors() { return captureNetworkErrors; }
        public void setCaptureNetworkErrors(boolean b) { this.captureNetworkErrors = b; }
        public boolean isEnableAutoRetry() { return enableAutoRetry; }
        public void setEnableAutoRetry(boolean b) { this.enableAutoRetry = b; }
        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int n) { this.maxRetryCount = n; }
        public long getRetryIntervalMs() { return retryIntervalMs; }
        public void setRetryIntervalMs(long ms) { this.retryIntervalMs = ms; }
        public boolean isRecordHistory() { return recordHistory; }
        public void setRecordHistory(boolean b) { this.recordHistory = b; }
        public int getMaxHistorySize() { return maxHistorySize; }
        public void setMaxHistorySize(int n) { this.maxHistorySize = n; }
        public boolean isPrintToConsole() { return printToConsole; }
        public void setPrintToConsole(boolean b) { this.printToConsole = b; }
        public boolean isLogExceptions() { return logExceptions; }
        public void setLogExceptions(boolean b) { this.logExceptions = b; }
        public Set<Class<? extends Exception>> getIgnoredExceptions() { return ignoredExceptions; }
        public void setIgnoredExceptions(Set<Class<? extends Exception>> s) { this.ignoredExceptions = s; }
        public ExceptionHandlingStrategy getStrategy() { return strategy; }
        public void setStrategy(ExceptionHandlingStrategy s) { this.strategy = s; }

        public ExceptionConfig ignoreException(Class<? extends Exception> exceptionClass) {
            this.ignoredExceptions.add(exceptionClass);
            return this;
        }

        public ExceptionConfig ignoreExceptions(Class<? extends Exception>... classes) {
            this.ignoredExceptions.addAll(Arrays.asList(classes));
            return this;
        }

        public static ExceptionConfig defaultConfig() {
            return new ExceptionConfig();
        }

        public static ExceptionConfig strictConfig() {
            ExceptionConfig c = new ExceptionConfig();
            c.setCaptureJsExceptions(true);
            c.setCaptureUnhandledExceptions(true);
            c.setCapturePromiseRejections(true);
            c.setCaptureResourceErrors(true);
            c.setCaptureNetworkErrors(true);
            c.setStrategy(ExceptionHandlingStrategy.THROW);
            return c;
        }

        public static ExceptionConfig lenientConfig() {
            ExceptionConfig c = new ExceptionConfig();
            c.setCaptureJsExceptions(true);
            c.setCaptureUnhandledExceptions(true);
            c.setCapturePromiseRejections(false);
            c.setCaptureResourceErrors(false);
            c.setCaptureNetworkErrors(false);
            c.setStrategy(ExceptionHandlingStrategy.LOG);
            return c;
        }
    }

    // ==================== 异常处理策略 ====================

    public enum ExceptionHandlingStrategy {
        LOG,        // 仅记录
        THROW,      // 抛出异常
        RETRY,      // 重试操作
        CUSTOM      // 使用自定义处理器
    }

    // ==================== 异常类型 ====================

    public enum ExceptionCategory {
        JAVASCRIPT,         // JS异常
        ELEMENT_NOT_FOUND,  // 元素未找到
        STALE_ELEMENT,      // 元素过期
        TIMEOUT,            // 超时
        NETWORK,            // 网络异常
        RESOURCE,           // 资源加载异常
        NAVIGATION,         // 导航异常
        ASSERTION,          // 断言失败
        UNKNOWN             // 未知异常
    }

    // ==================== 异常记录 ====================

    public static class ExceptionRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private ExceptionCategory category;
        private String type;
        private String message;
        private String stackTrace;
        private Instant timestamp;
        private String url;
        private String lineNumber;
        private String columnNumber;
        private String sourceId;
        private boolean handled;
        private String handlingStrategy;
        private int retryCount;
        private Map<String, Object> metadata;

        public ExceptionRecord() {
            this.id = UUID.randomUUID().toString();
            this.timestamp = Instant.now();
            this.metadata = new HashMap<>();
        }

        public ExceptionRecord(Exception e) {
            this();
            this.type = e.getClass().getSimpleName();
            this.message = e.getMessage();
            this.category = categorizeException(e);
        }

        private ExceptionCategory categorizeException(Exception e) {
            if (e instanceof org.openqa.selenium.NoSuchElementException || e instanceof org.openqa.selenium.NoSuchFrameException) {
                return ExceptionCategory.ELEMENT_NOT_FOUND;
            } else if (e instanceof org.openqa.selenium.StaleElementReferenceException) {
                return ExceptionCategory.STALE_ELEMENT;
            } else if (e instanceof org.openqa.selenium.TimeoutException) {
                return ExceptionCategory.TIMEOUT;
            } else if (e instanceof org.openqa.selenium.WebDriverException) {
                org.openqa.selenium.WebDriverException wde = (org.openqa.selenium.WebDriverException) e;
                if (wde.getMessage() != null && wde.getMessage().contains("net::")) {
                    return ExceptionCategory.NETWORK;
                }
                return ExceptionCategory.UNKNOWN;
            } else if (e instanceof org.openqa.selenium.JavascriptException) {
                return ExceptionCategory.JAVASCRIPT;
            } else if (e instanceof org.openqa.selenium.InvalidSelectorException) {
                return ExceptionCategory.ASSERTION;
            }
            return ExceptionCategory.UNKNOWN;
        }

        // Getters and Setters
        public String getId() { return id; }
        public ExceptionCategory getCategory() { return category; }
        public void setCategory(ExceptionCategory c) { this.category = c; }
        public String getType() { return type; }
        public void setType(String t) { this.type = t; }
        public String getMessage() { return message; }
        public void setMessage(String m) { this.message = m; }
        public String getStackTrace() { return stackTrace; }
        public void setStackTrace(String s) { this.stackTrace = s; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant t) { this.timestamp = t; }
        public String getUrl() { return url; }
        public void setUrl(String u) { this.url = u; }
        public String getLineNumber() { return lineNumber; }
        public void setLineNumber(String n) { this.lineNumber = n; }
        public String getColumnNumber() { return columnNumber; }
        public void setColumnNumber(String n) { this.columnNumber = n; }
        public String getSourceId() { return sourceId; }
        public void setSourceId(String s) { this.sourceId = s; }
        public boolean isHandled() { return handled; }
        public void setHandled(boolean h) { this.handled = h; }
        public String getHandlingStrategy() { return handlingStrategy; }
        public void setHandlingStrategy(String s) { this.handlingStrategy = s; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int n) { this.retryCount = n; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> m) { this.metadata = m; }

        public String getFullDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timestamp).append("]");
            sb.append("[").append(category).append("]");
            sb.append("[").append(type).append("]");
            sb.append(" ").append(message);
            if (stackTrace != null && !stackTrace.isEmpty()) {
                sb.append("\n").append(stackTrace);
            }
            return sb.toString();
        }

        public boolean isCritical() {
            return category == ExceptionCategory.NETWORK ||
                   category == ExceptionCategory.NAVIGATION ||
                   category == ExceptionCategory.TIMEOUT;
        }
    }

    // ==================== 回调接口 ====================

    @FunctionalInterface
    public interface ExceptionCallback {
        void onException(ExceptionRecord record);
    }

    @FunctionalInterface
    public interface ExceptionHandler {
        ExceptionHandlingStrategy handle(ExceptionRecord record, Exception originalException);
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final ExceptionConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean jsInterceptorInjected = false;

    private final List<ExceptionRecord> exceptionHistory = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentLinkedQueue<ExceptionCallback> exceptionCallbacks = new ConcurrentLinkedQueue<>();

    private final Map<ExceptionCategory, ExceptionHandler> categoryHandlers = new HashMap<>();
    private final Map<Class<? extends Exception>, ExceptionHandler> typeHandlers = new HashMap<>();

    private BiPredicate<ExceptionRecord, Exception> retryCondition = (r, e) -> true;

    // ==================== 构造函数 ====================

    public ChromeExceptionHandler(WebDriver driver) {
        this(driver, ExceptionConfig.defaultConfig());
    }

    public ChromeExceptionHandler(WebDriver driver, ExceptionConfig config) {
        this.driver = driver;
        this.config = config;

        // 注册默认处理器
        registerDefaultHandlers();

        logger.info("ChromeExceptionHandler initialized");
    }

    /**
     * 注册默认处理器
     */
    private void registerDefaultHandlers() {
        // NoSuchElement异常 - 重试
        categoryHandlers.put(ExceptionCategory.ELEMENT_NOT_FOUND, (record, e) -> {
            if (config.isEnableAutoRetry()) {
                return ExceptionHandlingStrategy.RETRY;
            }
            return config.getStrategy();
        });

        // StaleElement异常 - 重试
        categoryHandlers.put(ExceptionCategory.STALE_ELEMENT, (record, e) -> {
            return ExceptionHandlingStrategy.RETRY;
        });

        // 超时异常 - 记录
        categoryHandlers.put(ExceptionCategory.TIMEOUT, (record, e) -> {
            return ExceptionHandlingStrategy.LOG;
        });

        // 网络异常 - 记录并继续
        categoryHandlers.put(ExceptionCategory.NETWORK, (record, e) -> {
            return ExceptionHandlingStrategy.LOG;
        });

        // JS异常 - 抛出
        categoryHandlers.put(ExceptionCategory.JAVASCRIPT, (record, e) -> {
            return ExceptionHandlingStrategy.THROW;
        });

        // 默认处理器
        categoryHandlers.put(ExceptionCategory.UNKNOWN, (record, e) -> {
            return config.getStrategy();
        });
    }

    // ==================== 生命周期管理 ====================

    /**
     * 启动异常监控
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting ChromeExceptionHandler...");

            try {
                injectJsInterceptor();
                running.set(true);
                logger.info("ChromeExceptionHandler started");
            } catch (Exception e) {
                running.set(false);
                throw new RuntimeException("Failed to start ChromeExceptionHandler", e);
            }
        }
    }

    /**
     * 注入JS异常拦截器
     */
    private void injectJsInterceptor() {
        if (jsInterceptorInjected || !config.isCaptureJsExceptions()) return;

        String script = """
            (function() {
                if (window.__exceptionInterceptorInjected) return;
                window.__exceptionInterceptorInjected = true;
                window.__jsExceptions = [];
                
                function logException(type, message, stack, filename, lineno, colno) {
                    var ex = {
                        type: type,
                        message: message,
                        stack: stack,
                        filename: filename,
                        lineno: lineno,
                        colno: colno,
                        timestamp: new Date().toISOString()
                    };
                    window.__jsExceptions.push(ex);
                    console.error('[JS Exception] ' + type + ': ' + message);
                }
                
                // 全局error事件
                window.addEventListener('error', function(event) {
                    logException(
                        event.error ? event.error.name : 'Error',
                        event.message,
                        event.error ? event.error.stack : null,
                        event.filename,
                        event.lineno,
                        event.colno
                    );
                });
                
                // Promise拒绝事件
                window.addEventListener('unhandledrejection', function(event) {
                    var reason = event.reason;
                    var message = reason ? (reason.message || String(reason)) : 'Unhandled Promise Rejection';
                    var stack = reason && reason.stack ? reason.stack : null;
                    logException('PromiseRejection', message, stack, window.location.href, 0, 0);
                });
                
                console.log('Exception interceptor initialized');
            })();
            """;

        try {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(script);
            jsInterceptorInjected = true;
            logger.debug("JS exception interceptor injected");
        } catch (Exception e) {
            logger.warn("Failed to inject JS exception interceptor: {}", e.getMessage());
        }
    }

    /**
     * 停止异常监控
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping ChromeExceptionHandler...");
            jsInterceptorInjected = false;
            logger.info("ChromeExceptionHandler stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ==================== 异常处理 ====================

    /**
     * 处理异常
     */
    public ExceptionHandlingStrategy handleException(Exception e) {
        return handleException(e, null);
    }

    /**
     * 处理异常（带上下文）
     */
    public ExceptionHandlingStrategy handleException(Exception e, Map<String, Object> context) {
        ExceptionRecord record = new ExceptionRecord(e);
        record.setStackTrace(getStackTrace(e));

        if (context != null) {
            record.getMetadata().putAll(context);
        }

        // 获取当前URL
        try {
            record.setUrl(driver.getCurrentUrl());
        } catch (Exception ex) {
            // ignore
        }

        // 检查是否忽略
        if (shouldIgnore(e)) {
            record.setHandled(true);
            record.setHandlingStrategy("IGNORED");
            addToHistory(record);
            return ExceptionHandlingStrategy.LOG;
        }

        // 获取处理器
        ExceptionHandler handler = getHandler(e, record);
        ExceptionHandlingStrategy strategy = handler.handle(record, e);

        // 执行策略
        executeStrategy(strategy, record, e);

        return strategy;
    }

    /**
     * 包装操作并处理异常
     */
    public <T> T wrapOperation(java.util.function.Supplier<T> operation) {
        return wrapOperation(operation, null);
    }

    /**
     * 包装操作并处理异常（带名称）
     */
    public <T> T wrapOperation(java.util.function.Supplier<T> operation, String operationName) {
        try {
            return operation.get();
        } catch (Exception e) {
            Map<String, Object> context = new HashMap<>();
            if (operationName != null) {
                context.put("operation", operationName);
            }
            ExceptionHandlingStrategy strategy = handleException(e, context);

            if (strategy == ExceptionHandlingStrategy.THROW) {
                throw e;
            }

            return null;
        }
    }

    /**
     * 包装Runnable并处理异常
     */
    public Runnable wrapRunnable(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                handleException(e);
            }
        };
    }

    /**
     * 重试操作
     */
    public <T> T retryOperation(java.util.function.Supplier<T> operation) {
        return retryOperation(operation, config.getMaxRetryCount());
    }

    /**
     * 重试操作（指定次数）
     */
    public <T> T retryOperation(java.util.function.Supplier<T> operation, int maxRetries) {
        Exception lastException = null;

        for (int i = 0; i <= maxRetries; i++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                if (i < maxRetries && shouldRetry(e)) {
                    logger.debug("Retry attempt {} failed: {}", i + 1, e.getMessage());
                    try {
                        Thread.sleep(config.getRetryIntervalMs() * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
        }

        throw new RuntimeException("Operation failed after " + (maxRetries + 1) + " attempts", lastException);
    }

    // ==================== JS异常监控 ====================

    /**
     * 拉取并处理JS异常
     */
    public List<ExceptionRecord> pollJsExceptions() {
        List<ExceptionRecord> records = new ArrayList<>();

        try {
            Object result = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return window.__jsExceptions || [];");

            if (result instanceof List && !((List<?>) result).isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsEx = (List<Map<String, Object>>) result;

                for (Map<String, Object> jsException : jsEx) {
                    ExceptionRecord record = new ExceptionRecord();
                    record.setCategory(ExceptionCategory.JAVASCRIPT);
                    record.setType((String) jsException.get("type"));
                    record.setMessage((String) jsException.get("message"));
                    record.setStackTrace((String) jsException.get("stack"));
                    record.setUrl((String) jsException.get("filename"));
                    record.setLineNumber(jsException.get("lineno") != null ? 
                        String.valueOf(jsException.get("lineno")) : null);
                    record.setColumnNumber(jsException.get("colno") != null ? 
                        String.valueOf(jsException.get("colno")) : null);

                    handleJsException(record);
                    records.add(record);
                }

                // 清空JS端异常队列
                ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.__jsExceptions = [];");
            }
        } catch (Exception e) {
            logger.debug("Poll JS exceptions: {}", e.getMessage());
        }

        return records;
    }

    private void handleJsException(ExceptionRecord record) {
        record.setHandled(true);
        record.setHandlingStrategy("LOG");

        if (config.isPrintToConsole()) {
            ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("console.error('[JS Exception]', arguments[0]);", record.getMessage());
        }

        addToHistory(record);
        notifyException(record);
    }

    // ==================== 自定义处理器 ====================

    /**
     * 按类别注册处理器
     */
    public ChromeExceptionHandler registerCategoryHandler(ExceptionCategory category, ExceptionHandler handler) {
        categoryHandlers.put(category, handler);
        return this;
    }

    /**
     * 按类型注册处理器
     */
    public ChromeExceptionHandler registerTypeHandler(Class<? extends Exception> exceptionClass, ExceptionHandler handler) {
        typeHandlers.put(exceptionClass, handler);
        return this;
    }

    /**
     * 设置重试条件
     */
    public ChromeExceptionHandler setRetryCondition(BiPredicate<ExceptionRecord, Exception> condition) {
        this.retryCondition = condition;
        return this;
    }

    // ==================== 辅助方法 ====================

    private boolean shouldIgnore(Exception e) {
        for (Class<? extends Exception> ignored : config.getIgnoredExceptions()) {
            if (ignored.isInstance(e)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRetry(Exception e) {
        return retryCondition.test(new ExceptionRecord(e), e);
    }

    private ExceptionHandler getHandler(Exception e, ExceptionRecord record) {
        // 先检查类型处理器
        for (Map.Entry<Class<? extends Exception>, ExceptionHandler> entry : typeHandlers.entrySet()) {
            if (entry.getKey().isInstance(e)) {
                return entry.getValue();
            }
        }

        // 再检查类别处理器
        ExceptionCategory category = record.getCategory();
        if (categoryHandlers.containsKey(category)) {
            return categoryHandlers.get(category);
        }

        // 返回默认处理器
        return (r, ex) -> config.getStrategy();
    }

    private void executeStrategy(ExceptionHandlingStrategy strategy, ExceptionRecord record, Exception e) {
        record.setHandled(true);
        record.setHandlingStrategy(strategy.name());

        switch (strategy) {
            case LOG:
                if (config.isLogExceptions()) {
                    logger.warn("Exception caught: {}", record.getFullDescription());
                }
                break;

            case THROW:
                addToHistory(record);
                notifyException(record);
                throw new RuntimeException("Re-throwing handled exception", e);

            case RETRY:
                if (config.isLogExceptions()) {
                    logger.debug("Retrying after exception: {}", e.getMessage());
                }
                break;

            case CUSTOM:
                // 自定义处理已在getHandler中执行
                break;
        }

        addToHistory(record);
        notifyException(record);
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private void addToHistory(ExceptionRecord record) {
        if (config.isRecordHistory()) {
            exceptionHistory.add(record);
            while (exceptionHistory.size() > config.getMaxHistorySize()) {
                exceptionHistory.remove(0);
            }
        }
    }

    private void notifyException(ExceptionRecord record) {
        exceptionCallbacks.forEach(cb -> {
            try { cb.onException(record); }
            catch (Exception e) { logger.warn("Exception callback: {}", e.getMessage()); }
        });
    }

    // ==================== 回调注册 ====================

    public ChromeExceptionHandler onException(ExceptionCallback callback) {
        exceptionCallbacks.offer(callback);
        return this;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取异常历史
     */
    public List<ExceptionRecord> getExceptionHistory() {
        return new ArrayList<>(exceptionHistory);
    }

    /**
     * 获取关键异常
     */
    public List<ExceptionRecord> getCriticalExceptions() {
        List<ExceptionRecord> critical = new ArrayList<>();
        for (ExceptionRecord record : exceptionHistory) {
            if (record.isCritical()) {
                critical.add(record);
            }
        }
        return critical;
    }

    /**
     * 按类别查询异常
     */
    public List<ExceptionRecord> getExceptionsByCategory(ExceptionCategory category) {
        List<ExceptionRecord> filtered = new ArrayList<>();
        for (ExceptionRecord record : exceptionHistory) {
            if (record.getCategory() == category) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    /**
     * 清空历史
     */
    public void clearHistory() {
        exceptionHistory.clear();
    }

    /**
     * 获取异常统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalExceptions", exceptionHistory.size());

        Map<ExceptionCategory, Integer> byCategory = new HashMap<>();
        for (ExceptionRecord record : exceptionHistory) {
            byCategory.merge(record.getCategory(), 1, Integer::sum);
        }
        stats.put("byCategory", byCategory);

        int criticalCount = 0;
        for (ExceptionRecord record : exceptionHistory) {
            if (record.isCritical()) criticalCount++;
        }
        stats.put("criticalCount", criticalCount);

        return stats;
    }

    public void printStatistics() {
        Map<String, Object> stats = getStatistics();
        logger.info("=== Exception Statistics ===");
        logger.info("Total: {}, Critical: {}", 
            stats.get("totalExceptions"), stats.get("criticalCount"));
        @SuppressWarnings("unchecked")
        Map<ExceptionCategory, Integer> byCategory = (Map<ExceptionCategory, Integer>) stats.get("byCategory");
        byCategory.forEach((k, v) -> logger.info("  {}: {}", k, v));
    }

    // ==================== 静态工厂方法 ====================

    public static ChromeExceptionHandler create(WebDriver driver) {
        return new ChromeExceptionHandler(driver);
    }

    public static ChromeExceptionHandler create(WebDriver driver, ExceptionConfig config) {
        return new ChromeExceptionHandler(driver, config);
    }

    public static ChromeExceptionHandler createStrict(WebDriver driver) {
        return new ChromeExceptionHandler(driver, ExceptionConfig.strictConfig());
    }

    public static ChromeExceptionHandler createLenient(WebDriver driver) {
        return new ChromeExceptionHandler(driver, ExceptionConfig.lenientConfig());
    }
}
