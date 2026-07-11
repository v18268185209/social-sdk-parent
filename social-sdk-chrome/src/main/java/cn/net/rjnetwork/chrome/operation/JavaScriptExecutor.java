package cn.net.rjnetwork.chrome.operation;

import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * JavaScript执行器
 * 
 * 功能:
 * - 安全JS执行（异常捕获）
 * - 异步JS执行
 * - JS执行结果转换
 * - JS执行超时控制
 * - 等待条件执行
 * - 内置常用JS片段
 * 
 * @author Social SDK
 */
public class JavaScriptExecutor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptExecutor.class);

    // ==================== 执行配置 ====================

    public static class JSConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 执行超时时间（毫秒）
         */
        private long timeoutMs = 10000;

        /**
         * 是否捕获异常
         */
        private boolean catchExceptions = true;

        /**
         * 是否等待页面稳定
         */
        private boolean waitForPageStable = false;

        /**
         * 页面稳定等待时间（毫秒）
         */
        private long stableWaitMs = 500;

        /**
         * 重试次数
         */
        private int retryCount = 0;

        /**
         * 重试间隔（毫秒）
         */
        private long retryIntervalMs = 100;

        /**
         * 是否记录执行日志
         */
        private boolean logExecution = true;

        /**
         * 结果类型
         */
        private ResultType resultType = ResultType.AUTO;

        public enum ResultType {
            AUTO, STRING, NUMBER, BOOLEAN, LIST, MAP
        }

        // Getters and Setters
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long ms) { this.timeoutMs = ms; }
        public boolean isCatchExceptions() { return catchExceptions; }
        public void setCatchExceptions(boolean b) { this.catchExceptions = b; }
        public boolean isWaitForPageStable() { return waitForPageStable; }
        public void setWaitForPageStable(boolean b) { this.waitForPageStable = b; }
        public long getStableWaitMs() { return stableWaitMs; }
        public void setStableWaitMs(long ms) { this.stableWaitMs = ms; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int n) { this.retryCount = n; }
        public long getRetryIntervalMs() { return retryIntervalMs; }
        public void setRetryIntervalMs(long ms) { this.retryIntervalMs = ms; }
        public boolean isLogExecution() { return logExecution; }
        public void setLogExecution(boolean b) { this.logExecution = b; }
        public ResultType getResultType() { return resultType; }
        public void setResultType(ResultType t) { this.resultType = t; }

        public static JSConfig defaultConfig() {
            return new JSConfig();
        }

        public static JSConfig quickConfig() {
            JSConfig c = new JSConfig();
            c.setTimeoutMs(3000);
            c.setRetryCount(1);
            return c;
        }

        public static JSConfig asyncConfig() {
            JSConfig c = new JSConfig();
            c.setTimeoutMs(30000);
            c.setCatchExceptions(true);
            return c;
        }
    }

    // ==================== 执行结果 ====================

    public static class JSResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean success;
        private Object value;
        private String error;
        private long executionTimeMs;
        private Instant timestamp;
        private String script;
        private boolean timeout;
        private String exceptionType;
        private String stackTrace;

        public JSResult() {
            this.timestamp = Instant.now();
        }

        public static JSResult success(Object value) {
            JSResult r = new JSResult();
            r.success = true;
            r.value = value;
            return r;
        }

        public static JSResult error(String error) {
            JSResult r = new JSResult();
            r.success = false;
            r.error = error;
            return r;
        }

        public static JSResult error(Exception e) {
            JSResult r = new JSResult();
            r.success = false;
            r.error = e.getMessage();
            r.exceptionType = e.getClass().getSimpleName();
            if (e.getStackTrace().length > 0) {
                r.stackTrace = Arrays.toString(e.getStackTrace());
            }
            return r;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean s) { this.success = s; }
        public Object getValue() { return value; }
        public void setValue(Object v) { this.value = v; }
        public String getError() { return error; }
        public void setError(String e) { this.error = e; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long ms) { this.executionTimeMs = ms; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant t) { this.timestamp = t; }
        public String getScript() { return script; }
        public void setScript(String s) { this.script = s; }
        public boolean isTimeout() { return timeout; }
        public void setTimeout(boolean t) { this.timeout = t; }
        public String getExceptionType() { return exceptionType; }
        public void setExceptionType(String t) { this.exceptionType = t; }
        public String getStackTrace() { return stackTrace; }
        public void setStackTrace(String s) { this.stackTrace = s; }

        public <T> T as(Class<T> type) {
            if (value == null) return null;
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            if (type == String.class) {
                return type.cast(String.valueOf(value));
            }
            if (type == Integer.class || type == int.class) {
                if (value instanceof Number) {
                    return type.cast(((Number) value).intValue());
                }
            }
            if (type == Long.class || type == long.class) {
                if (value instanceof Number) {
                    return type.cast(((Number) value).longValue());
                }
            }
            if (type == Double.class || type == double.class) {
                if (value instanceof Number) {
                    return type.cast(((Number) value).doubleValue());
                }
            }
            if (type == Boolean.class || type == boolean.class) {
                if (value instanceof Boolean) {
                    return type.cast(value);
                }
            }
            return null;
        }

        public String asString() {
            return as(String.class);
        }

        public Integer asInt() {
            return as(Integer.class);
        }

        public Long asLong() {
            return as(Long.class);
        }

        public Double asDouble() {
            return as(Double.class);
        }

        public Boolean asBoolean() {
            return as(Boolean.class);
        }

        @SuppressWarnings("unchecked")
        public List<Object> asList() {
            if (value instanceof List) {
                return (List<Object>) value;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> asMap() {
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            return null;
        }

        public String toString() {
            if (success) {
                return String.format("JSResult[success=true, value=%s, time=%dms]", value, executionTimeMs);
            } else {
                return String.format("JSResult[success=false, error=%s, time=%dms]", error, executionTimeMs);
            }
        }
    }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final JSConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ConcurrentLinkedQueue<BiConsumer<String, JSResult>> executionListeners = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // ==================== 构造函数 ====================

    public JavaScriptExecutor(WebDriver driver) {
        this(driver, JSConfig.defaultConfig());
    }

    public JavaScriptExecutor(WebDriver driver, JSConfig config) {
        this.driver = driver;
        this.config = config;
        logger.info("JavaScriptExecutor initialized");
    }

    // ==================== 同步执行 ====================

    /**
     * 执行JavaScript
     */
    public JSResult execute(String script) {
        return execute(script, null);
    }

    /**
     * 执行JavaScript（带参数）
     */
    public JSResult execute(String script, Object[] args) {
        long startTime = System.currentTimeMillis();

        try {
            // 等待页面稳定
            if (config.isWaitForPageStable()) {
                waitForPageStable();
            }

            // 执行重试
            Exception lastException = null;
            for (int i = 0; i <= config.getRetryCount(); i++) {
                try {
                    Object result;
                    
                    if (config.isCatchExceptions()) {
                        String wrappedScript = wrapWithTryCatch(script);
                        result = ((JavascriptExecutor) driver).executeScript(wrappedScript, args);
                    } else {
                        result = ((JavascriptExecutor) driver).executeScript(script, args);
                    }

                    long duration = System.currentTimeMillis() - startTime;

                    // 转换结果
                    Object converted = convertResult(result);

                    JSResult jsResult = JSResult.success(converted);
                    jsResult.setExecutionTimeMs(duration);
                    jsResult.setScript(script);

                    logExecution(script, jsResult);
                    notifyListeners(script, jsResult);

                    return jsResult;
                } catch (Exception e) {
                    lastException = e;
                    if (i < config.getRetryCount()) {
                        Thread.sleep(config.getRetryIntervalMs());
                    }
                }
            }

            throw lastException;
        } catch (java.util.concurrent.TimeoutException e) {
            JSResult result = JSResult.error("Script execution timeout");
            result.setTimeout(true);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            result.setScript(script);
            logExecution(script, result);
            notifyListeners(script, result);
            return result;
        } catch (Exception e) {
            JSResult result = JSResult.error(e);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            result.setScript(script);
            logExecution(script, result);
            notifyListeners(script, result);
            return result;
        }
    }

    /**
     * 执行JavaScript并等待结果
     */
    public <T> T executeAndWait(String script, Class<T> resultType) {
        JSResult result = execute(script);
        return result.as(resultType);
    }

    // ==================== 异步执行 ====================

    /**
     * 异步执行JavaScript
     */
    public CompletableFuture<JSResult> executeAsync(String script) {
        return executeAsync(script, null);
    }

    /**
     * 异步执行JavaScript（带参数）
     */
    public CompletableFuture<JSResult> executeAsync(String script, Object[] args) {
        return CompletableFuture.supplyAsync(() -> execute(script, args), scheduler);
    }

    /**
     * 异步执行JavaScript（带超时）
     */
    public JSResult executeAsyncWithTimeout(String script, long timeoutMs) {
        try {
            return executeAsync(script).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            JSResult result = JSResult.error("Async execution timeout");
            result.setTimeout(true);
            return result;
        } catch (Exception e) {
            return JSResult.error(e);
        }
    }

    // ==================== 条件执行 ====================

    /**
     * 等待条件满足后执行
     */
    public JSResult executeWhen(String script, Predicate<WebDriver> condition) {
        return executeWhen(script, null, condition, config.getTimeoutMs());
    }

    /**
     * 等待条件满足后执行（指定超时）
     */
    public JSResult executeWhen(String script, Object[] args, Predicate<WebDriver> condition, long timeoutMs) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition.test(driver)) {
                return execute(script, args);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return JSResult.error("Condition not satisfied within timeout");
    }

    /**
     * 等待元素存在后执行
     */
    public JSResult executeWhenElementExists(String script, String selector) {
        Predicate<WebDriver> condition = d -> {
            try {
                d.findElement(By.cssSelector(selector));
                return true;
            } catch (Exception e) {
                return false;
            }
        };
        return executeWhen(script, null, condition, config.getTimeoutMs());
    }

    // ==================== 等待功能 ====================

    /**
     * 等待JavaScript返回true
     */
    public boolean waitForScript(String script) {
        return waitForScript(script, config.getTimeoutMs());
    }

    /**
     * 等待JavaScript返回true（指定超时）
     */
    public boolean waitForScript(String script, long timeoutMs) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Object result = ((JavascriptExecutor) driver).executeScript(script);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            } catch (Exception e) {
                // continue
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return false;
    }

    /**
     * 等待页面稳定（无活跃网络请求）
     */
    public void waitForPageStable() {
        waitForPageStable(config.getStableWaitMs());
    }

    /**
     * 等待页面稳定（指定时间）
     */
    public void waitForPageStable(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待jQuery加载完成
     */
    public boolean waitForJQuery() {
        return waitForScript("return typeof jQuery === 'undefined' || jQuery.isReady;");
    }

    /**
     * 等待Angular加载完成
     */
    public boolean waitForAngular() {
        return waitForScript(
            "return typeof angular === 'undefined' || !angular.element(document.body).injector() || angular.element(document.body).injector().get('$http').pendingRequests.length === 0;"
        );
    }

    /**
     * 等待React加载完成
     */
    public boolean waitForReact() {
        return waitForScript(
            "return document.querySelector('[data-reactroot]') || document.querySelector('[data-reactid]') === null || (typeof __REACT_DEVTOOLS_GLOBAL_HOOK__ === 'undefined' || __REACT_DEVTOOLS_GLOBAL_HOOK__.__renderedByReactDOM === undefined);",
            5000
        );
    }

    // ==================== 内置脚本 ====================

    /**
     * 获取元素属性
     */
    public String getAttribute(WebElement element, String attribute) {
        return executeAndWait(
            "return arguments[0].getAttribute(arguments[1]);",
            String.class
        );
    }

    /**
     * 设置元素属性
     */
    public boolean setAttribute(WebElement element, String attribute, String value) {
        return executeAndWait(
            "arguments[0].setAttribute(arguments[1], arguments[2]); return true;",
            Boolean.class
        );
    }

    /**
     * 获取元素CSS值
     */
    public String getCssValue(WebElement element, String property) {
        return executeAndWait(
            "return arguments[0].style[arguments[1]] || window.getComputedStyle(arguments[0])[arguments[1]];",
            String.class
        );
    }

    /**
     * 获取元素文本
     */
    public String getText(WebElement element) {
        return executeAndWait(
            "return arguments[0].innerText || arguments[0].textContent;",
            String.class
        );
    }

    /**
     * 获取页面标题
     */
    public String getTitle() {
        return executeAndWait("return document.title;", String.class);
    }

    /**
     * 获取当前URL
     */
    public String getUrl() {
        return executeAndWait("return window.location.href;", String.class);
    }

    /**
     * 获取页面源代码
     */
    public String getPageSource() {
        return executeAndWait("return document.documentElement.outerHTML;", String.class);
    }

    /**
     * 滚动到元素
     */
    public void scrollToElement(WebElement element) {
        execute("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", 
            new Object[]{element});
    }

    /**
     * 滚动到顶部
     */
    public void scrollToTop() {
        execute("window.scrollTo({top: 0, behavior: 'smooth'});");
    }

    /**
     * 滚动到底部
     */
    public void scrollToBottom() {
        execute("window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'});");
    }

    /**
     * 滚动指定像素
     */
    public void scrollBy(int x, int y) {
        execute("window.scrollBy(arguments[0], arguments[1]);", new Object[]{x, y});
    }

    /**
     * 高亮元素
     */
    public void highlightElement(WebElement element) {
        execute(
            "arguments[0].style.outline = '3px solid red'; arguments[0].style.outlineOffset = '2px';",
            new Object[]{element}
        );
    }

    /**
     * 移除元素高亮
     */
    public void unhighlightElement(WebElement element) {
        execute(
            "arguments[0].style.outline = ''; arguments[0].style.outlineOffset = '';",
            new Object[]{element}
        );
    }

    /**
     * 模拟鼠标移动
     */
    public void simulateMouseMove(WebElement element, int offsetX, int offsetY) {
        execute(
            """
            var event = new MouseEvent('mousemove', {
                view: window,
                bubbles: true,
                cancelable: true,
                clientX: arguments[0].getBoundingClientRect().left + arguments[1],
                clientY: arguments[0].getBoundingClientRect().top + arguments[2]
            });
            arguments[0].dispatchEvent(event);
            """,
            new Object[]{element, offsetX, offsetY}
        );
    }

    /**
     * 检查元素是否可见
     */
    public boolean isElementVisible(WebElement element) {
        return executeAndWait(
            "return arguments[0].offsetWidth > 0 && arguments[0].offsetHeight > 0 && window.getComputedStyle(arguments[0]).visibility !== 'hidden';",
            Boolean.class
        );
    }

    /**
     * 检查元素是否在视口内
     */
    public boolean isElementInViewport(WebElement element) {
        return executeAndWait(
            """
            var rect = arguments[0].getBoundingClientRect();
            return (
                rect.top >= 0 &&
                rect.left >= 0 &&
                rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
                rect.right <= (window.innerWidth || document.documentElement.clientWidth)
            );
            """,
            Boolean.class
        );
    }

    /**
     * 获取元素位置
     */
    @SuppressWarnings("unchecked")
    public Map<String, Number> getElementPosition(WebElement element) {
        return executeAndWait(
            """
            var rect = arguments[0].getBoundingClientRect();
            return {
                x: rect.x,
                y: rect.y,
                width: rect.width,
                height: rect.height,
                top: rect.top,
                left: rect.left,
                bottom: rect.bottom,
                right: rect.right
            };
            """,
            Map.class
        );
    }

    /**
     * 获取本地存储
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getLocalStorage() {
        return executeAndWait(
            "var result = {}; for (var i = 0; i < localStorage.length; i++) { var key = localStorage.key(i); result[key] = localStorage.getItem(key); } return result;",
            Map.class
        );
    }

    /**
     * 获取会话存储
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getSessionStorage() {
        return executeAndWait(
            "var result = {}; for (var i = 0; i < sessionStorage.length; i++) { var key = sessionStorage.key(i); result[key] = sessionStorage.getItem(key); } return result;",
            Map.class
        );
    }

    /**
     * 设置本地存储
     */
    public void setLocalStorage(String key, String value) {
        execute("localStorage.setItem(arguments[0], arguments[1]);", new Object[]{key, value});
    }

    /**
     * 清空本地存储
     */
    public void clearLocalStorage() {
        execute("localStorage.clear();");
    }

    /**
     * 清空会话存储
     */
    public void clearSessionStorage() {
        execute("sessionStorage.clear();");
    }

    /**
     * 注入CSS
     */
    public void injectCss(String css) {
        execute(
            """
            var style = document.createElement('style');
            style.textContent = arguments[0];
            document.head.appendChild(style);
            return true;
            """,
            new Object[]{css}
        );
    }

    /**
     * 移除所有脚本标签
     */
    public void removeAllScripts() {
        execute(
            "var scripts = document.querySelectorAll('script'); scripts.forEach(function(s) { s.remove(); }); return scripts.length;"
        );
    }

    /**
     * 模拟页面加载完成
     */
    public void simulatePageLoad() {
        execute(
            """
            if (document.readyState !== 'complete') {
                Object.defineProperty(document, 'readyState', { get: function() { return 'complete'; } });
            }
            if (window.onload) { window.onload(); }
            """
        );
    }

    /**
     * 触发自定义事件
     */
    public void dispatchCustomEvent(String eventName, Map<String, Object> detail) {
        try {
            String detailJson = detail != null ? 
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail) : "{}";
            String script = String.format(
                "var event = new CustomEvent('%s', {detail: %s}); window.dispatchEvent(event);",
                eventName,
                detailJson
            );
            execute(script);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to serialize event detail", e);
            execute(String.format(
                "var event = new CustomEvent('%s', {detail: {}}); window.dispatchEvent(event);",
                eventName
            ));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 包装脚本以捕获异常
     */
    private String wrapWithTryCatch(String script) {
        return """
            try {
                return (function() {
                    %s
                }).apply(this, arguments);
            } catch (e) {
                return {error: true, message: e.message, stack: e.stack};
            }
            """.formatted(script);
    }

    /**
     * 转换结果类型
     */
    private Object convertResult(Object result) {
        if (result == null) return null;

        // 处理包装的错误对象
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            if (map.containsKey("error") && map.containsKey("message")) {
                throw new RuntimeException(map.get("message").toString());
            }
        }

        // 根据配置转换类型
        switch (config.getResultType()) {
            case STRING:
                return String.valueOf(result);
            case NUMBER:
                if (result instanceof Number) {
                    return ((Number) result).doubleValue();
                }
                return result;
            case BOOLEAN:
                return Boolean.valueOf(String.valueOf(result));
            case LIST:
                if (result instanceof List) {
                    return result;
                }
                return Arrays.asList(result);
            case MAP:
                if (result instanceof Map) {
                    return result;
                }
                return result;
            case AUTO:
            default:
                return result;
        }
    }

    private void logExecution(String script, JSResult result) {
        if (config.isLogExecution()) {
            if (result.isSuccess()) {
                logger.debug("JS executed: {} -> {} ({}ms)", 
                    script.substring(0, Math.min(100, script.length())), 
                    result.getValue(), 
                    result.getExecutionTimeMs());
            } else {
                logger.warn("JS failed: {} -> {} ({}ms)", 
                    script.substring(0, Math.min(100, script.length())), 
                    result.getError(), 
                    result.getExecutionTimeMs());
            }
        }
    }

    private void notifyListeners(String script, JSResult result) {
        executionListeners.forEach(listener -> {
            try {
                listener.accept(script, result);
            } catch (Exception e) {
                logger.warn("Execution listener: {}", e.getMessage());
            }
        });
    }

    /**
     * 添加执行监听器
     */
    public JavaScriptExecutor addExecutionListener(BiConsumer<String, JSResult> listener) {
        executionListeners.offer(listener);
        return this;
    }

    // ==================== AutoCloseable ====================

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        logger.info("JavaScriptExecutor closed");
    }

    // ==================== 静态工厂方法 ====================

    public static JavaScriptExecutor create(WebDriver driver) {
        return new JavaScriptExecutor(driver);
    }

    public static JavaScriptExecutor create(WebDriver driver, JSConfig config) {
        return new JavaScriptExecutor(driver, config);
    }

    public static JavaScriptExecutor createQuick(WebDriver driver) {
        return new JavaScriptExecutor(driver, JSConfig.quickConfig());
    }

    public static JavaScriptExecutor createAsync(WebDriver driver) {
        return new JavaScriptExecutor(driver, JSConfig.asyncConfig());
    }
}
