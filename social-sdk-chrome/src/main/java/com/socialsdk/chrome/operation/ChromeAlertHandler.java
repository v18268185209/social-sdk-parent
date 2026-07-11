package com.socialsdk.chrome.operation;

import org.openqa.selenium.Alert;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 浏览器弹窗处理器
 * 
 * 功能:
 * - 处理 alert、confirm、prompt 弹窗
 * - 自动拦截和自定义处理
 * - 弹窗历史记录
 * - 自定义弹窗模拟
 * 
 * @author Social SDK
 */
public class ChromeAlertHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ChromeAlertHandler.class);

    // ==================== 弹窗配置 ====================

    public static class AlertConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 是否自动处理弹窗
         */
        private boolean autoHandle = true;

        /**
         * 默认处理动作
         */
        private AlertAction defaultAction = AlertAction.ACCEPT;

        /**
         * 自动处理超时时间（毫秒）
         */
        private long autoHandleTimeoutMs = 5000;

        /**
         * 是否记录弹窗历史
         */
        private boolean recordHistory = true;

        /**
         * 最大历史记录数
         */
        private int maxHistorySize = 100;

        /**
         * 是否捕获alert
         */
        private boolean captureAlert = true;

        /**
         * 是否捕获confirm
         */
        private boolean captureConfirm = true;

        /**
         * 是否捕获prompt
         */
        private boolean capturePrompt = true;

        /**
         * 默认prompt输入值
         */
        private String defaultPromptValue = "";

        /**
         * 是否替换window.alert
         */
        private boolean replaceWindowAlert = true;

        /**
         * 是否替换window.confirm
         */
        private boolean replaceWindowConfirm = true;

        /**
         * 是否替换window.prompt
         */
        private boolean replaceWindowPrompt = true;

        // Getters and Setters
        public boolean isAutoHandle() { return autoHandle; }
        public void setAutoHandle(boolean b) { this.autoHandle = b; }
        public AlertAction getDefaultAction() { return defaultAction; }
        public void setDefaultAction(AlertAction a) { this.defaultAction = a; }
        public long getAutoHandleTimeoutMs() { return autoHandleTimeoutMs; }
        public void setAutoHandleTimeoutMs(long ms) { this.autoHandleTimeoutMs = ms; }
        public boolean isRecordHistory() { return recordHistory; }
        public void setRecordHistory(boolean b) { this.recordHistory = b; }
        public int getMaxHistorySize() { return maxHistorySize; }
        public void setMaxHistorySize(int n) { this.maxHistorySize = n; }
        public boolean isCaptureAlert() { return captureAlert; }
        public void setCaptureAlert(boolean b) { this.captureAlert = b; }
        public boolean isCaptureConfirm() { return captureConfirm; }
        public void setCaptureConfirm(boolean b) { this.captureConfirm = b; }
        public boolean isCapturePrompt() { return capturePrompt; }
        public void setCapturePrompt(boolean b) { this.capturePrompt = b; }
        public String getDefaultPromptValue() { return defaultPromptValue; }
        public void setDefaultPromptValue(String v) { this.defaultPromptValue = v; }
        public boolean isReplaceWindowAlert() { return replaceWindowAlert; }
        public void setReplaceWindowAlert(boolean b) { this.replaceWindowAlert = b; }
        public boolean isReplaceWindowConfirm() { return replaceWindowConfirm; }
        public void setReplaceWindowConfirm(boolean b) { this.replaceWindowConfirm = b; }
        public boolean isReplaceWindowPrompt() { return replaceWindowPrompt; }
        public void setReplaceWindowPrompt(boolean b) { this.replaceWindowPrompt = b; }

        public static AlertConfig defaultConfig() {
            return new AlertConfig();
        }

        public static AlertConfig autoAcceptConfig() {
            AlertConfig c = new AlertConfig();
            c.setAutoHandle(true);
            c.setDefaultAction(AlertAction.ACCEPT);
            c.setCaptureAlert(true);
            c.setCaptureConfirm(true);
            c.setCapturePrompt(true);
            return c;
        }

        public static AlertConfig silentConfig() {
            AlertConfig c = new AlertConfig();
            c.setAutoHandle(true);
            c.setDefaultAction(AlertAction.DISMISS);
            c.setCaptureAlert(true);
            c.setCaptureConfirm(true);
            c.setCapturePrompt(true);
            return c;
        }
    }

    // ==================== 弹窗动作 ====================

    public enum AlertAction {
        ACCEPT,     // 确认
        DISMISS,    // 取消
        WAIT        // 等待手动处理
    }

    // ==================== 弹窗类型 ====================

    public enum AlertType {
        ALERT,
        CONFIRM,
        PROMPT,
        UNKNOWN
    }

    // ==================== 弹窗记录 ====================

    public static class AlertRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private AlertType type;
        private String message;
        private Instant timestamp;
        private AlertAction action;
        private String userInput;
        private boolean autoHandled;
        private boolean success;
        private Map<String, Object> metadata;

        public AlertRecord() {
            this.id = UUID.randomUUID().toString();
            this.timestamp = Instant.now();
            this.metadata = new HashMap<>();
        }

        // Getters and Setters
        public String getId() { return id; }
        public AlertType getType() { return type; }
        public void setType(AlertType t) { this.type = t; }
        public String getMessage() { return message; }
        public void setMessage(String m) { this.message = m; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant t) { this.timestamp = t; }
        public AlertAction getAction() { return action; }
        public void setAction(AlertAction a) { this.action = a; }
        public String getUserInput() { return userInput; }
        public void setUserInput(String i) { this.userInput = i; }
        public boolean isAutoHandled() { return autoHandled; }
        public void setAutoHandled(boolean b) { this.autoHandled = b; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean s) { this.success = s; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> m) { this.metadata = m; }
        public String getError() { return (String) metadata.get("error"); }
        public void setError(String error) { this.metadata.put("error", error); }

        public String toString() {
            return String.format("AlertRecord[type=%s, message=%s, action=%s, auto=%s]",
                type, message, action, autoHandled);
        }
    }

    // ==================== 回调接口 ====================

    @FunctionalInterface
    public interface AlertCallback {
        void onAlert(AlertRecord record);
    }

    @FunctionalInterface
    public interface AlertHandler {
        AlertRecord handleAlert(AlertRecord record);
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final AlertConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean interceptorInjected = false;

    private final List<AlertRecord> alertHistory = Collections.synchronizedList(new ArrayList<>());

    private final ConcurrentLinkedQueue<AlertCallback> alertCallbacks = new ConcurrentLinkedQueue<>();
    private final Map<AlertType, AlertHandler> customHandlers = new HashMap<>();

    // ==================== 构造函数 ====================

    public ChromeAlertHandler(WebDriver driver) {
        this(driver, AlertConfig.defaultConfig());
    }

    public ChromeAlertHandler(WebDriver driver, AlertConfig config) {
        this.driver = driver;
        this.config = config;

        // 注册默认处理
        registerDefaultHandlers();

        logger.info("ChromeAlertHandler initialized");
    }

    /**
     * 注册默认处理逻辑
     */
    private void registerDefaultHandlers() {
        // Alert处理器
        customHandlers.put(AlertType.ALERT, record -> {
            record.setAction(config.getDefaultAction());
            record.setAutoHandled(true);
            return record;
        });

        // Confirm处理器
        customHandlers.put(AlertType.CONFIRM, record -> {
            record.setAction(config.getDefaultAction());
            record.setAutoHandled(true);
            return record;
        });

        // Prompt处理器
        customHandlers.put(AlertType.PROMPT, record -> {
            record.setAction(AlertAction.ACCEPT);
            record.setUserInput(config.getDefaultPromptValue());
            record.setAutoHandled(true);
            return record;
        });
    }

    // ==================== 生命周期管理 ====================

    /**
     * 启动弹窗拦截
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting ChromeAlertHandler...");

            try {
                injectInterceptor();
                running.set(true);
                logger.info("ChromeAlertHandler started");
            } catch (Exception e) {
                running.set(false);
                throw new RuntimeException("Failed to start ChromeAlertHandler", e);
            }
        }
    }

    /**
     * 注入JavaScript拦截器
     */
    private void injectInterceptor() {
        if (interceptorInjected) return;

        String script = """
            (function() {
                if (window.__alertInterceptorInjected) return;
                window.__alertInterceptorInjected = true;
                window.__alertQueue = [];
                window.__originalAlert = window.alert;
                window.__originalConfirm = window.confirm;
                window.__originalPrompt = window.prompt;
                
                function queueAlert(type, message) {
                    var alert = {
                        type: type,
                        message: message,
                        timestamp: new Date().toISOString(),
                        handled: false
                    };
                    window.__alertQueue.push(alert);
                    return alert;
                }
                
                window.alert = function(message) {
                    queueAlert('ALERT', String(message));
                    console.log('[Alert] ' + message);
                };
                
                window.confirm = function(message) {
                    queueAlert('CONFIRM', String(message));
                    console.log('[Confirm] ' + message);
                    return true;
                };
                
                window.prompt = function(message, defaultText) {
                    queueAlert('PROMPT', String(message));
                    console.log('[Prompt] ' + message);
                    return arguments[1] || '';
                };
                
                window.__getAlertQueue = function() {
                    var alerts = window.__alertQueue.slice();
                    window.__alertQueue = [];
                    return alerts;
                };
                
                console.log('Alert interceptor initialized');
            })();
            """;

        try {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(script);
            interceptorInjected = true;
            logger.debug("Alert interceptor injected");
        } catch (Exception e) {
            logger.warn("Failed to inject alert interceptor: {}", e.getMessage());
        }
    }

    /**
     * 停止弹窗拦截
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping ChromeAlertHandler...");
            interceptorInjected = false;
            logger.info("ChromeAlertHandler stopped");
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ==================== 弹窗处理 ====================

    /**
     * 获取当前弹窗
     */
    public Optional<Alert> getAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            return Optional.of(alert);
        } catch (NoAlertPresentException e) {
            return Optional.empty();
        }
    }

    /**
     * 获取弹窗文本
     */
    public String getAlertText() {
        return getAlert().map(Alert::getText).orElse(null);
    }

    /**
     * 接受弹窗
     */
    public boolean acceptAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            alert.accept();
            logger.debug("Alert accepted");
            return true;
        } catch (NoAlertPresentException e) {
            logger.debug("No alert present to accept");
            return false;
        }
    }

    /**
     * 拒绝弹窗
     */
    public boolean dismissAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            alert.dismiss();
            logger.debug("Alert dismissed");
            return true;
        } catch (NoAlertPresentException e) {
            logger.debug("No alert present to dismiss");
            return false;
        }
    }

    /**
     * 输入文本到prompt弹窗
     */
    public boolean sendAlertText(String text) {
        try {
            Alert alert = driver.switchTo().alert();
            alert.sendKeys(text);
            alert.accept();
            logger.debug("Alert text sent: {}", text);
            return true;
        } catch (NoAlertPresentException e) {
            logger.debug("No alert present to send text");
            return false;
        }
    }

    /**
     * 处理当前弹窗
     */
    public AlertRecord handleCurrentAlert() {
        AlertRecord record = new AlertRecord();

        try {
            Alert alert = driver.switchTo().alert();
            record.setMessage(alert.getText());

            // 确定弹窗类型
            AlertType type = detectAlertType(alert);
            record.setType(type);

            // 调用自定义处理
            AlertHandler handler = customHandlers.get(type);
            if (handler != null) {
                record = handler.handleAlert(record);
            } else {
                record.setAction(config.getDefaultAction());
                record.setAutoHandled(true);
            }

            // 执行动作
            if (record.getAction() == AlertAction.ACCEPT) {
                alert.accept();
            } else if (record.getAction() == AlertAction.DISMISS) {
                alert.dismiss();
            } else if (record.getAction() == AlertAction.WAIT) {
                // 等待手动处理
                return record;
            }

            // 如果是prompt，处理输入
            if (type == AlertType.PROMPT && record.getUserInput() != null) {
                alert.sendKeys(record.getUserInput());
                alert.accept();
            }

            record.setSuccess(true);
            logger.info("Alert handled: {}", record);

        } catch (NoAlertPresentException e) {
            record.setType(AlertType.UNKNOWN);
            record.setSuccess(false);
            record.setError("No alert present");
        }

        addToHistory(record);
        notifyAlert(record);

        return record;
    }

    /**
     * 自动处理所有待处理弹窗
     */
    public List<AlertRecord> handleAllPendingAlerts() {
        List<AlertRecord> records = new ArrayList<>();

        for (int i = 0; i < 10; i++) { // 最多处理10个弹窗
            Optional<Alert> alert = getAlert();
            if (alert.isEmpty()) {
                break;
            }
            records.add(handleCurrentAlert());
        }

        return records;
    }

    /**
     * 拉取并处理JavaScript拦截的弹窗
     */
    public List<AlertRecord> pollAndHandleAlerts() {
        List<AlertRecord> records = new ArrayList<>();

        try {
            Object result = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return window.__getAlertQueue();");

            if (result instanceof List && !((List<?>) result).isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsAlerts = (List<Map<String, Object>>) result;

                for (Map<String, Object> jsAlert : jsAlerts) {
                    AlertRecord record = new AlertRecord();
                    record.setMessage((String) jsAlert.get("message"));
                    record.setType(AlertType.valueOf((String) jsAlert.get("type")));
                    record.setAutoHandled(true);
                    record.setSuccess(true);
                    records.add(record);
                    notifyAlert(record);
                }
            }
        } catch (Exception e) {
            logger.debug("Poll alerts: {}", e.getMessage());
        }

        return records;
    }

    // ==================== 自定义处理 ====================

    /**
     * 注册自定义Alert处理器
     */
    public ChromeAlertHandler registerAlertHandler(AlertHandler handler) {
        customHandlers.put(AlertType.ALERT, handler);
        return this;
    }

    /**
     * 注册自定义Confirm处理器
     */
    public ChromeAlertHandler registerConfirmHandler(AlertHandler handler) {
        customHandlers.put(AlertType.CONFIRM, handler);
        return this;
    }

    /**
     * 注册自定义Prompt处理器
     */
    public ChromeAlertHandler registerPromptHandler(AlertHandler handler) {
        customHandlers.put(AlertType.PROMPT, handler);
        return this;
    }

    /**
     * 移除自定义处理器
     */
    public ChromeAlertHandler removeHandler(AlertType type) {
        customHandlers.remove(type);
        return this;
    }

    /**
     * 清空所有自定义处理器
     */
    public ChromeAlertHandler clearHandlers() {
        customHandlers.clear();
        registerDefaultHandlers();
        return this;
    }

    // ==================== 辅助方法 ====================

    private AlertType detectAlertType(Alert alert) {
        // 通过尝试操作来检测类型
        try {
            String text = alert.getText();
            // 暂时无法区分alert/confirm/prompt
            // 使用默认规则或JavaScript检测
            return detectViaJavaScript();
        } catch (Exception e) {
            return AlertType.UNKNOWN;
        }
    }

    private AlertType detectViaJavaScript() {
        try {
            Object result = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return window.__lastAlertType || 'UNKNOWN';");
            if (result != null) {
                return AlertType.valueOf(result.toString());
            }
        } catch (Exception e) {
            // ignore
        }
        return AlertType.ALERT; // 默认
    }

    private void addToHistory(AlertRecord record) {
        if (config.isRecordHistory()) {
            alertHistory.add(record);
            while (alertHistory.size() > config.getMaxHistorySize()) {
                alertHistory.remove(0);
            }
        }
    }

    private void notifyAlert(AlertRecord record) {
        alertCallbacks.forEach(cb -> {
            try { cb.onAlert(record); }
            catch (Exception e) { logger.warn("Alert callback: {}", e.getMessage()); }
        });
    }

    // ==================== 回调注册 ====================

    public ChromeAlertHandler onAlert(AlertCallback callback) {
        alertCallbacks.offer(callback);
        return this;
    }

    // ==================== 查询方法 ====================

    /**
     * 检查是否有弹窗
     */
    public boolean hasAlert() {
        return getAlert().isPresent();
    }

    /**
     * 获取弹窗历史
     */
    public List<AlertRecord> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    /**
     * 清空历史
     */
    public void clearHistory() {
        alertHistory.clear();
    }

    /**
     * 模拟alert弹窗
     */
    public void simulateAlert(String message) {
        ((org.openqa.selenium.JavascriptExecutor) driver)
            .executeScript("window.alert(arguments[0]);", message);
    }

    /**
     * 模拟confirm弹窗（接受）
     */
    public boolean simulateConfirm(String message, boolean accept) {
        String script = String.format(
            "return window.confirm('%s');",
            message.replace("'", "\\'")
        );
        return ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(script) != null;
    }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAlerts", alertHistory.size());

        int alertCount = 0, confirmCount = 0, promptCount = 0;
        for (AlertRecord record : alertHistory) {
            switch (record.getType()) {
                case ALERT: alertCount++; break;
                case CONFIRM: confirmCount++; break;
                case PROMPT: promptCount++; break;
            }
        }

        stats.put("alertCount", alertCount);
        stats.put("confirmCount", confirmCount);
        stats.put("promptCount", promptCount);
        stats.put("currentAlert", hasAlert());

        return stats;
    }

    public void printStatistics() {
        Map<String, Object> stats = getStatistics();
        logger.info("=== Alert Statistics ===");
        logger.info("Total: {}, Alerts: {}, Confirms: {}, Prompts: {}",
            stats.get("totalAlerts"), stats.get("alertCount"),
            stats.get("confirmCount"), stats.get("promptCount"));
    }

    // ==================== 静态工厂方法 ====================

    public static ChromeAlertHandler create(WebDriver driver) {
        return new ChromeAlertHandler(driver);
    }

    public static ChromeAlertHandler create(WebDriver driver, AlertConfig config) {
        return new ChromeAlertHandler(driver, config);
    }

    public static ChromeAlertHandler createAutoAccept(WebDriver driver) {
        return new ChromeAlertHandler(driver, AlertConfig.autoAcceptConfig());
    }

    public static ChromeAlertHandler createSilent(WebDriver driver) {
        return new ChromeAlertHandler(driver, AlertConfig.silentConfig());
    }
}
