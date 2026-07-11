package com.socialsdk.chrome.operation;

import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 控制台日志处理器
 * 
 * 功能:
 * - 捕获页面JavaScript console.log/error/warn/info/debug
 * - 捕获JavaScript异常
 * - 支持日志级别过滤
 * - 注册回调处理日志
 * 
 * @author Social SDK
 */
public class ConsoleLogHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleLogHandler.class);

    // ==================== 日志级别 ====================

    public enum LogLevel {
        DEBUG("DEBUG"),
        INFO("INFO"),
        WARNING("WARNING"),
        ERROR("ERROR"),
        LOG("LOG"); // console.log 默认级别

        private final String name;

        LogLevel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static LogLevel fromString(String level) {
            if (level == null) return LOG;
            try {
                return LogLevel.valueOf(level.toUpperCase());
            } catch (Exception e) {
                return LOG;
            }
        }
    }

    // ==================== 日志条目 ====================

    public static class LogEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private String type;
        private LogLevel level;
        private String message;
        private String url;
        private int lineNumber;
        private int columnNumber;
        private Instant timestamp;
        private String traceId;
        private List<String> stackTrace;
        private List<Object> args;
        private boolean isException;
        private String exceptionMessage;

        public LogEntry() {
            this.timestamp = Instant.now();
        }

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String t) { this.type = t; }
        public LogLevel getLevel() { return level; }
        public void setLevel(LogLevel l) { this.level = l; }
        public String getMessage() { return message; }
        public void setMessage(String m) { this.message = m; }
        public String getUrl() { return url; }
        public void setUrl(String u) { this.url = u; }
        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int n) { this.lineNumber = n; }
        public int getColumnNumber() { return columnNumber; }
        public void setColumnNumber(int n) { this.columnNumber = n; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant t) { this.timestamp = t; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String id) { this.traceId = id; }
        public List<String> getStackTrace() { return stackTrace; }
        public void setStackTrace(List<String> st) { this.stackTrace = st; }
        public List<Object> getArgs() { return args; }
        public void setArgs(List<Object> a) { this.args = a; }
        public boolean isException() { return isException; }
        public void setException(boolean e) { isException = e; }
        public String getExceptionMessage() { return exceptionMessage; }
        public void setExceptionMessage(String m) { this.exceptionMessage = m; }

        public boolean isError() {
            return level == LogLevel.ERROR || isException;
        }

        public boolean isWarning() {
            return level == LogLevel.WARNING;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timestamp).append("]");
            sb.append("[").append(level.getName()).append("]");
            if (url != null) {
                sb.append("[").append(url).append(":").append(lineNumber).append("]");
            }
            sb.append(" ").append(message);
            return sb.toString();
        }
    }

    // ==================== 回调接口 ====================

    @FunctionalInterface
    public interface LogCallback {
        void onLog(LogEntry entry);
    }

    @FunctionalInterface
    public interface ErrorCallback {
        void onError(LogEntry entry);
    }

    @FunctionalInterface
    public interface WarningCallback {
        void onWarning(LogEntry entry);
    }

    // ==================== 配置 ====================

    public static class ConsoleConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 是否启用日志捕获
         */
        private boolean enabled = true;

        /**
         * 最小日志级别
         */
        private LogLevel minLevel = LogLevel.DEBUG;

        /**
         * 是否捕获console.log
         */
        private boolean captureLog = true;

        /**
         * 是否捕获console.info
         */
        private boolean captureInfo = true;

        /**
         * 是否捕获console.warn
         */
        private boolean captureWarn = true;

        /**
         * 是否捕获console.error
         */
        private boolean captureError = true;

        /**
         * 是否捕获console.debug
         */
        private boolean captureDebug = true;

        /**
         * 是否捕获JS异常
         */
        private boolean captureExceptions = true;

        /**
         * 是否捕获网络请求失败
         */
        private boolean captureNetworkErrors = true;

        /**
         * 最大日志条目数
         */
        private int maxEntries = 1000;

        /**
         * 是否自动清除旧日志
         */
        private boolean autoClear = true;

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean b) { this.enabled = b; }
        public LogLevel getMinLevel() { return minLevel; }
        public void setMinLevel(LogLevel l) { this.minLevel = l; }
        public boolean isCaptureLog() { return captureLog; }
        public void setCaptureLog(boolean b) { this.captureLog = b; }
        public boolean isCaptureInfo() { return captureInfo; }
        public void setCaptureInfo(boolean b) { this.captureInfo = b; }
        public boolean isCaptureWarn() { return captureWarn; }
        public void setCaptureWarn(boolean b) { this.captureWarn = b; }
        public boolean isCaptureError() { return captureError; }
        public void setCaptureError(boolean b) { this.captureError = b; }
        public boolean isCaptureDebug() { return captureDebug; }
        public void setCaptureDebug(boolean b) { this.captureDebug = b; }
        public boolean isCaptureExceptions() { return captureExceptions; }
        public void setCaptureExceptions(boolean b) { this.captureExceptions = b; }
        public boolean isCaptureNetworkErrors() { return captureNetworkErrors; }
        public void setCaptureNetworkErrors(boolean b) { this.captureNetworkErrors = b; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int i) { this.maxEntries = i; }
        public boolean isAutoClear() { return autoClear; }
        public void setAutoClear(boolean b) { this.autoClear = b; }

        public static ConsoleConfig defaultConfig() {
            return new ConsoleConfig();
        }

        public static ConsoleConfig productionConfig() {
            ConsoleConfig c = new ConsoleConfig();
            c.setCaptureInfo(true);
            c.setCaptureWarn(true);
            c.setCaptureError(true);
            c.setCaptureExceptions(true);
            c.setMinLevel(LogLevel.INFO);
            return c;
        }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final ConsoleConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ConcurrentLinkedQueue<LogEntry> logQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LogCallback> logCallbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ErrorCallback> errorCallbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WarningCallback> warningCallbacks = new ConcurrentLinkedQueue<>();

    private volatile boolean injected = false;

    // ==================== 构造函数 ====================

    public ConsoleLogHandler(WebDriver driver) {
        this(driver, ConsoleConfig.defaultConfig());
    }

    public ConsoleLogHandler(WebDriver driver, ConsoleConfig config) {
        this.driver = driver;
        this.config = config;
        logger.info("ConsoleLogHandler initialized");
    }

    // ==================== 生命周期管理 ====================

    /**
     * 启动日志捕获
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting ConsoleLogHandler...");

            try {
                injectConsoleInterceptor();
                running.set(true);
                logger.info("ConsoleLogHandler started");
            } catch (Exception e) {
                running.set(false);
                throw new RuntimeException("Failed to start ConsoleLogHandler", e);
            }
        }
    }

    /**
     * 注入控制台拦截脚本
     */
    private void injectConsoleInterceptor() {
        if (injected) return;

        String script = """
            (function() {
                if (window.__consoleInterceptorInjected) return;
                window.__consoleInterceptorInjected = true;
                window.__consoleLogs = [];
                window.__errorLogs = [];
                
                function sendLog(type, level, args) {
                    const entry = {
                        type: type,
                        level: level,
                        message: args.map(a => String(a)).join(' '),
                        timestamp: new Date().toISOString(),
                        args: args,
                        url: window.location.href,
                        traceId: 'log_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
                    };
                    
                    if (window.__consoleLogs.length > 1000) {
                        window.__consoleLogs.shift();
                    }
                    window.__consoleLogs.push(entry);
                    
                    if (level === 'ERROR') {
                        if (window.__errorLogs.length > 100) {
                            window.__errorLogs.shift();
                        }
                        window.__errorLogs.push(entry);
                    }
                }
                
                // 保存原始方法
                window.__originalConsole = {
                    log: console.log,
                    info: console.info,
                    warn: console.warn,
                    error: console.error,
                    debug: console.debug
                };
                
                console.log = function(...args) {
                    sendLog('console.log', 'LOG', args);
                    window.__originalConsole.log.apply(console, args);
                };
                
                console.info = function(...args) {
                    sendLog('console.info', 'INFO', args);
                    window.__originalConsole.info.apply(console, args);
                };
                
                console.warn = function(...args) {
                    sendLog('console.warn', 'WARNING', args);
                    window.__originalConsole.warn.apply(console, args);
                };
                
                console.error = function(...args) {
                    sendLog('console.error', 'ERROR', args);
                    window.__originalConsole.error.apply(console, args);
                };
                
                console.debug = function(...args) {
                    sendLog('console.debug', 'DEBUG', args);
                    window.__originalConsole.debug.apply(console, args);
                };
                
                // 监听未捕获的异常
                window.addEventListener('error', function(event) {
                    if (event.error) {
                        const entry = {
                            type: 'exception',
                            level: 'ERROR',
                            message: event.error.message || String(event.error),
                            timestamp: new Date().toISOString(),
                            url: window.location.href,
                            traceId: 'err_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9),
                            isException: true,
                            exceptionMessage: event.error.message,
                            stackTrace: event.error.stack ? event.error.stack.split('\n') : []
                        };
                        window.__consoleLogs.push(entry);
                        window.__errorLogs.push(entry);
                    }
                });
                
                // Promise异常监听
                window.addEventListener('unhandledrejection', function(event) {
                    const entry = {
                        type: 'promise_rejection',
                        level: 'ERROR',
                        message: event.reason ? String(event.reason) : 'Unhandled Promise Rejection',
                        timestamp: new Date().toISOString(),
                        url: window.location.href,
                        traceId: 'promise_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9),
                        isException: true,
                        exceptionMessage: event.reason ? String(event.reason) : 'No reason provided'
                    };
                    window.__consoleLogs.push(entry);
                    window.__errorLogs.push(entry);
                });
                
                console.log('Console interceptor initialized');
            })();
            """;

        try {
            ((JavascriptExecutor) driver).executeScript(script);
            injected = true;
            logger.debug("Console interceptor injected");
        } catch (Exception e) {
            logger.warn("Failed to inject console interceptor: {}", e.getMessage());
        }
    }

    /**
     * 停止日志捕获
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping ConsoleLogHandler...");
            injected = false;
            logger.info("ConsoleLogHandler stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ==================== 日志获取 ====================

    /**
     * 获取所有日志
     */
    public List<LogEntry> getAllLogs() {
        return new ArrayList<>(logQueue);
    }

    /**
     * 获取日志队列
     */
    public List<LogEntry> pollLogs() {
        List<LogEntry> logs = new ArrayList<>(logQueue);
        clearLogs();
        return logs;
    }

    /**
     * 拉取最新日志
     */
    public List<LogEntry> pullLogs() {
        try {
            Object result = ((JavascriptExecutor) driver)
                .executeScript("return window.__consoleLogs || [];");

            if (result instanceof List && !((List<?>) result).isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsLogs = (List<Map<String, Object>>) result;

                // 清空JS端日志
                ((JavascriptExecutor) driver)
                    .executeScript("window.__consoleLogs = [];");

                List<LogEntry> newLogs = new ArrayList<>();

                for (Map<String, Object> jsLog : jsLogs) {
                    LogEntry entry = parseLogEntry(jsLog);
                    if (entry != null && shouldCapture(entry)) {
                        processLogEntry(entry);
                        newLogs.add(entry);
                    }
                }

                return newLogs;
            }
        } catch (Exception e) {
            logger.debug("Pull logs: {}", e.getMessage());
        }

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private LogEntry parseLogEntry(Map<String, Object> jsLog) {
        try {
            LogEntry entry = new LogEntry();
            entry.setType((String) jsLog.get("type"));
            entry.setLevel(LogLevel.fromString((String) jsLog.get("level")));
            entry.setMessage((String) jsLog.get("message"));
            entry.setUrl((String) jsLog.get("url"));
            entry.setTimestamp(Instant.parse((String) jsLog.get("timestamp")));
            entry.setTraceId((String) jsLog.get("traceId"));

            Object args = jsLog.get("args");
            if (args instanceof List) {
                entry.setArgs(new ArrayList<>((List<Object>) args));
            }

            Object isEx = jsLog.get("isException");
            if (isEx != null) {
                entry.setException((Boolean) isEx);
            }

            Object exMsg = jsLog.get("exceptionMessage");
            if (exMsg != null) {
                entry.setExceptionMessage((String) exMsg);
            }

            Object stackTrace = jsLog.get("stackTrace");
            if (stackTrace instanceof List) {
                entry.setStackTrace(new ArrayList<>((List<String>) stackTrace));
            }

            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean shouldCapture(LogEntry entry) {
        if (!config.isEnabled()) return false;

        // 检查日志级别
        if (entry.getLevel().ordinal() < config.getMinLevel().ordinal()) {
            return false;
        }

        // 检查类型捕获开关
        switch (entry.getType()) {
            case "console.log": return config.isCaptureLog();
            case "console.info": return config.isCaptureInfo();
            case "console.warn": return config.isCaptureWarn();
            case "console.error": return config.isCaptureError();
            case "console.debug": return config.isCaptureDebug();
            case "exception": return config.isCaptureExceptions();
            case "promise_rejection": return config.isCaptureExceptions();
            default: return true;
        }
    }

    private void processLogEntry(LogEntry entry) {
        // 添加到队列
        logQueue.offer(entry);

        // 限制队列大小
        while (logQueue.size() > config.getMaxEntries()) {
            logQueue.poll();
        }

        // 执行回调
        switch (entry.getLevel()) {
            case ERROR:
                errorCallbacks.forEach(cb -> {
                    try { cb.onError(entry); }
                    catch (Exception e) { logger.warn("Error callback: {}", e.getMessage()); }
                });
                if (config.isCaptureError()) {
                    logger.error("[Browser] {}", entry.getMessage());
                }
                break;
            case WARNING:
                warningCallbacks.forEach(cb -> {
                    try { cb.onWarning(entry); }
                    catch (Exception e) { logger.warn("Warning callback: {}", e.getMessage()); }
                });
                if (config.isCaptureWarn()) {
                    logger.warn("[Browser] {}", entry.getMessage());
                }
                break;
            default:
                logCallbacks.forEach(cb -> {
                    try { cb.onLog(entry); }
                    catch (Exception e) { logger.warn("Log callback: {}", e.getMessage()); }
                });
                if (entry.getLevel() == LogLevel.LOG && config.isCaptureLog()) {
                    logger.info("[Browser] {}", entry.getMessage());
                } else if (entry.getLevel() == LogLevel.INFO && config.isCaptureInfo()) {
                    logger.info("[Browser] {}", entry.getMessage());
                } else if (entry.getLevel() == LogLevel.DEBUG && config.isCaptureDebug()) {
                    logger.debug("[Browser] {}", entry.getMessage());
                }
                break;
        }
    }

    // ==================== 回调注册 ====================

    public ConsoleLogHandler onLog(LogCallback callback) {
        logCallbacks.offer(callback);
        return this;
    }

    public ConsoleLogHandler onError(ErrorCallback callback) {
        errorCallbacks.offer(callback);
        return this;
    }

    public ConsoleLogHandler onWarning(WarningCallback callback) {
        warningCallbacks.offer(callback);
        return this;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取错误日志
     */
    public List<LogEntry> getErrors() {
        List<LogEntry> errors = new ArrayList<>();
        for (LogEntry entry : logQueue) {
            if (entry.isError()) {
                errors.add(entry);
            }
        }
        return errors;
    }

    /**
     * 获取警告日志
     */
    public List<LogEntry> getWarnings() {
        List<LogEntry> warnings = new ArrayList<>();
        for (LogEntry entry : logQueue) {
            if (entry.isWarning()) {
                warnings.add(entry);
            }
        }
        return warnings;
    }

    /**
     * 按消息内容搜索
     */
    public List<LogEntry> search(String keyword) {
        List<LogEntry> results = new ArrayList<>();
        for (LogEntry entry : logQueue) {
            if (entry.getMessage() != null && entry.getMessage().contains(keyword)) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * 清空日志
     */
    public void clearLogs() {
        logQueue.clear();
        try {
            ((JavascriptExecutor) driver).executeScript("window.__consoleLogs = []; window.__errorLogs = [];");
        } catch (Exception e) {
            // ignore
        }
    }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", logQueue.size());
        
        int errorCount = 0;
        int warnCount = 0;
        for (LogEntry entry : logQueue) {
            if (entry.isError()) errorCount++;
            else if (entry.isWarning()) warnCount++;
        }
        
        stats.put("errorCount", errorCount);
        stats.put("warningCount", warnCount);
        stats.put("infoCount", logQueue.size() - errorCount - warnCount);
        
        return stats;
    }

    public void printStatistics() {
        Map<String, Object> stats = getStatistics();
        logger.info("=== Console Log Statistics ===");
        logger.info("Total: {}, Errors: {}, Warnings: {}", 
            stats.get("totalLogs"), stats.get("errorCount"), stats.get("warningCount"));
    }

    // ==================== 静态工厂方法 ====================

    public static ConsoleLogHandler create(WebDriver driver) {
        return new ConsoleLogHandler(driver);
    }

    public static ConsoleLogHandler create(WebDriver driver, ConsoleConfig config) {
        return new ConsoleLogHandler(driver, config);
    }

    public static ConsoleLogHandler createProduction(WebDriver driver) {
        return new ConsoleLogHandler(driver, ConsoleConfig.productionConfig());
    }
}