package cn.net.rjnetwork.chrome.operation.network;

import org.openqa.selenium.*;
import org.openqa.selenium.devtools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.*;

/**
 * 企业级网络请求监听器
 * 
 * 支持:
 * - HTTP/HTTPS 请求/响应捕获 (通过JavaScript注入)
 * - WebSocket 消息监听
 * - 请求日志记录和统计
 * 
 * @author Social SDK
 */
public class NetworkMonitor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(NetworkMonitor.class);

    // ==================== 配置 ====================

    public static class NetworkConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        private int maxRequestRecords = 10000;
        private int maxResponseRecords = 10000;
        private boolean logRequests = true;
        private boolean logResponses = true;
        private long batchSaveIntervalMs = 5000;

        public int getMaxRequestRecords() { return maxRequestRecords; }
        public void setMaxRequestRecords(int i) { this.maxRequestRecords = i; }
        public int getMaxResponseRecords() { return maxResponseRecords; }
        public void setMaxResponseRecords(int i) { this.maxResponseRecords = i; }
        public boolean isLogRequests() { return logRequests; }
        public void setLogRequests(boolean b) { this.logRequests = b; }
        public boolean isLogResponses() { return logResponses; }
        public void setLogResponses(boolean b) { this.logResponses = b; }
        public long getBatchSaveIntervalMs() { return batchSaveIntervalMs; }
        public void setBatchSaveIntervalMs(long l) { this.batchSaveIntervalMs = l; }

        public static NetworkConfig defaultConfig() { return new NetworkConfig(); }
        public static NetworkConfig performanceConfig() {
            NetworkConfig c = new NetworkConfig();
            c.setMaxRequestRecords(1000);
            c.setMaxResponseRecords(1000);
            c.setLogRequests(false);
            c.setLogResponses(false);
            return c;
        }
    }

    // ==================== 请求信息 ====================

    public static class RequestInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String requestId;
        private String url;
        private String method;
        private Map<String, String> headers = new HashMap<>();
        private String postData;
        private Instant timestamp;

        public RequestInfo() { this.timestamp = Instant.now(); }

        public String getRequestId() { return requestId; }
        public void setRequestId(String s) { this.requestId = s; }
        public String getUrl() { return url; }
        public void setUrl(String s) { this.url = s; }
        public String getMethod() { return method; }
        public void setMethod(String s) { this.method = s; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> m) { this.headers = m; }
        public String getPostData() { return postData; }
        public void setPostData(String s) { this.postData = s; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant i) { this.timestamp = i; }
    }

    // ==================== 响应信息 ====================

    public static class ResponseInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String requestId;
        private int status;
        private String url;
        private Instant timestamp;

        public ResponseInfo() { this.timestamp = Instant.now(); }

        public String getRequestId() { return requestId; }
        public void setRequestId(String s) { this.requestId = s; }
        public int getStatus() { return status; }
        public void setStatus(int i) { this.status = i; }
        public String getUrl() { return url; }
        public void setUrl(String s) { this.url = s; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant i) { this.timestamp = i; }

        public boolean isSuccess() { return status >= 200 && status < 400; }
        public boolean isError() { return status >= 400; }
    }

    // ==================== WebSocket消息 ====================

    public static class WebSocketMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum MessageType { SEND, RECEIVE, OPEN, CLOSE, ERROR }

        private String requestId;
        private MessageType type;
        private String payload;
        private Instant timestamp;

        public WebSocketMessage() { this.timestamp = Instant.now(); }

        public String getRequestId() { return requestId; }
        public void setRequestId(String s) { this.requestId = s; }
        public MessageType getType() { return type; }
        public void setType(MessageType t) { this.type = t; }
        public String getPayload() { return payload; }
        public void setPayload(String s) { this.payload = s; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant i) { this.timestamp = i; }
    }

    // ==================== 回调接口 ====================

    @FunctionalInterface
    public interface RequestCallback { void onRequest(RequestInfo request); }
    @FunctionalInterface
    public interface ResponseCallback { void onResponse(ResponseInfo response); }
    @FunctionalInterface
    public interface WebSocketCallback { void onWebSocketMessage(WebSocketMessage message); }

    // ==================== 实例属性 ====================

    private final WebDriver driver;
    private final NetworkConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final ConcurrentLinkedQueue<RequestInfo> requestQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ResponseInfo> responseQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WebSocketMessage> wsMessageQueue = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<String, RequestInfo> requestIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResponseInfo> responseIndex = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<RequestCallback> requestCallbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ResponseCallback> responseCallbacks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WebSocketCallback> wsCallbacks = new ConcurrentLinkedQueue<>();

    private volatile ScheduledExecutorService scheduler;

    // ==================== 构造函数 ====================

    public NetworkMonitor(WebDriver driver) {
        this(driver, NetworkConfig.defaultConfig());
    }

    public NetworkMonitor(WebDriver driver, NetworkConfig config) {
        this.driver = driver;
        this.config = config;
        logger.info("NetworkMonitor initialized");
    }

    // ==================== 生命周期管理 ====================

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting NetworkMonitor...");
            
            try {
                // 注入JavaScript网络监控脚本
                injectNetworkMonitor();
                
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "NetworkMonitor-Scheduler");
                    t.setDaemon(true);
                    return t;
                });

                if (config.getBatchSaveIntervalMs() > 0) {
                    scheduler.scheduleAtFixedRate(this::flushQueues,
                        config.getBatchSaveIntervalMs(),
                        config.getBatchSaveIntervalMs(),
                        TimeUnit.MILLISECONDS);
                }

                logger.info("NetworkMonitor started");
            } catch (Exception e) {
                running.set(false);
                throw new RuntimeException("Failed to start NetworkMonitor", e);
            }
        }
    }

    private void injectNetworkMonitor() {
        String script = """
            (function() {
                if (window.__networkMonitorInjected) return;
                window.__networkMonitorInjected = true;
                window.__networkEvents = [];
                
                function logEvent(type, data) {
                    window.__networkEvents.push({type: type, data: data, time: Date.now()});
                    if (window.__networkEvents.length > 1000) {
                        window.__networkEvents.shift();
                    }
                }
                
                // Fetch拦截
                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    const url = typeof args[0] === 'string' ? args[0] : args[0].url;
                    const method = args[1]?.method || 'GET';
                    const reqId = 'req_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5);
                    
                    logEvent('request', {requestId: reqId, url: url, method: method, time: Date.now()});
                    
                    return originalFetch.apply(this, args).then(response => {
                        logEvent('response', {requestId: reqId, url: url, status: response.status, time: Date.now()});
                        return response;
                    }).catch(error => {
                        logEvent('error', {requestId: reqId, url: url, error: error.message, time: Date.now()});
                        throw error;
                    });
                };
                
                // XHR拦截
                const origOpen = XMLHttpRequest.prototype.open;
                const origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this._reqId = 'xhr_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5);
                    this._url = url;
                    this._method = method;
                    return origOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function(body) {
                    const xhr = this;
                    logEvent('request', {requestId: xhr._reqId, url: xhr._url, method: xhr._method, time: Date.now()});
                    
                    xhr.addEventListener('load', function() {
                        logEvent('response', {requestId: xhr._reqId, url: xhr._url, status: xhr.status, time: Date.now()});
                    });
                    xhr.addEventListener('error', function() {
                        logEvent('error', {requestId: xhr._reqId, url: xhr._url, error: 'Network Error', time: Date.now()});
                    });
                    
                    return origSend.apply(this, arguments);
                };
                
                // WebSocket拦截
                const OrigWS = window.WebSocket;
                window.WebSocket = function(url, protocols) {
                    const wsId = 'ws_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5);
                    logEvent('ws_open', {requestId: wsId, url: url, time: Date.now()});
                    
                    const ws = protocols ? new OrigWS(url, protocols) : new OrigWS(url);
                    const origSendWS = ws.send;
                    
                    ws.addEventListener('message', function(e) {
                        logEvent('ws_msg', {requestId: wsId, type: 'RECEIVE', payload: String(e.data).substring(0, 1000), time: Date.now()});
                    });
                    ws.addEventListener('close', function() {
                        logEvent('ws_close', {requestId: wsId, time: Date.now()});
                    });
                    
                    ws.send = function(data) {
                        logEvent('ws_msg', {requestId: wsId, type: 'SEND', payload: String(data).substring(0, 1000), time: Date.now()});
                        return origSendWS.apply(this, arguments);
                    };
                    
                    return ws;
                };
            })();
            """;
        
        ((JavascriptExecutor) driver).executeScript(script);
        logger.debug("Network monitor script injected");
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping NetworkMonitor...");

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

            flushQueues();
            logger.info("NetworkMonitor stopped");
        }
    }

    @Override
    public void close() { stop(); }

    // ==================== 轮询获取事件 ====================

    /**
     * 轮询并处理网络事件
     * 建议在循环中调用此方法
     */
    public void pollEvents() {
        if (!running.get()) return;
        
        try {
            Object result = ((JavascriptExecutor) driver)
                .executeScript("return window.__networkEvents || [];");
            
            if (result instanceof List && !((List<?>) result).isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> events = (List<Map<String, Object>>) result;
                
                synchronized (driver) {
                    ((JavascriptExecutor) driver)
                        .executeScript("window.__networkEvents = [];");
                }
                
                for (Map<String, Object> event : events) {
                    processEvent(event);
                }
            }
        } catch (Exception e) {
            logger.debug("Poll events: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void processEvent(Map<String, Object> event) {
        String type = (String) event.get("type");
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        
        if (type == null || data == null) return;
        
        switch (type) {
            case "request" -> {
                RequestInfo req = new RequestInfo();
                req.setRequestId((String) data.get("requestId"));
                req.setUrl((String) data.get("url"));
                req.setMethod((String) data.get("method"));
                handleRequest(req);
            }
            case "response" -> {
                ResponseInfo resp = new ResponseInfo();
                resp.setRequestId((String) data.get("requestId"));
                resp.setUrl((String) data.get("url"));
                Object status = data.get("status");
                if (status != null) resp.setStatus(((Number) status).intValue());
                handleResponse(resp);
            }
            case "ws_open" -> {
                WebSocketMessage msg = new WebSocketMessage();
                msg.setRequestId((String) data.get("requestId"));
                msg.setType(WebSocketMessage.MessageType.OPEN);
                handleWebSocketMessage(msg);
            }
            case "ws_msg" -> {
                WebSocketMessage msg = new WebSocketMessage();
                msg.setRequestId((String) data.get("requestId"));
                msg.setType(WebSocketMessage.MessageType.valueOf((String) data.get("type")));
                msg.setPayload((String) data.get("payload"));
                handleWebSocketMessage(msg);
            }
            case "ws_close" -> {
                WebSocketMessage msg = new WebSocketMessage();
                msg.setRequestId((String) data.get("requestId"));
                msg.setType(WebSocketMessage.MessageType.CLOSE);
                handleWebSocketMessage(msg);
            }
        }
    }

    // ==================== 回调注册 ====================

    public NetworkMonitor onRequest(RequestCallback callback) {
        requestCallbacks.offer(callback);
        return this;
    }

    public NetworkMonitor onResponse(ResponseCallback callback) {
        responseCallbacks.offer(callback);
        return this;
    }

    public NetworkMonitor onWebSocket(WebSocketCallback callback) {
        wsCallbacks.offer(callback);
        return this;
    }

    // ==================== 内部处理方法 ====================

    private void handleRequest(RequestInfo request) {
        lock.readLock().lock();
        try {
            requestQueue.offer(request);
            requestIndex.put(request.getRequestId(), request);
            trimQueue(requestQueue, config.getMaxRequestRecords());
            trimMap(requestIndex, config.getMaxRequestRecords());

            requestCallbacks.forEach(c -> {
                try { c.onRequest(request); } 
                catch (Exception e) { logger.warn("Request callback: {}", e.getMessage()); }
            });

            if (config.isLogRequests()) {
                logger.info("Request: {} {}", request.getMethod(), request.getUrl());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private void handleResponse(ResponseInfo response) {
        lock.readLock().lock();
        try {
            responseQueue.offer(response);
            responseIndex.put(response.getRequestId(), response);
            trimQueue(responseQueue, config.getMaxResponseRecords());
            trimMap(responseIndex, config.getMaxResponseRecords());

            responseCallbacks.forEach(c -> {
                try { c.onResponse(response); } 
                catch (Exception e) { logger.warn("Response callback: {}", e.getMessage()); }
            });

            if (config.isLogResponses()) {
                logger.info("Response: {} {} ({})", response.getRequestId(), response.getStatus(), response.getUrl());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private void handleWebSocketMessage(WebSocketMessage message) {
        lock.readLock().lock();
        try {
            wsMessageQueue.offer(message);
            wsCallbacks.forEach(c -> {
                try { c.onWebSocketMessage(message); } 
                catch (Exception e) { logger.warn("WS callback: {}", e.getMessage()); }
            });
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== 查询方法 ====================

    public List<RequestInfo> getAllRequests() { return new ArrayList<>(requestQueue); }
    public List<ResponseInfo> getAllResponses() { return new ArrayList<>(responseQueue); }
    public Optional<RequestInfo> getRequest(String requestId) { return Optional.ofNullable(requestIndex.get(requestId)); }
    public Optional<ResponseInfo> getResponse(String requestId) { return Optional.ofNullable(responseIndex.get(requestId)); }

    public List<RequestInfo> getRequestsByUrl(String urlPattern) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        List<RequestInfo> result = new ArrayList<>();
        for (RequestInfo request : requestQueue) {
            if (pattern.matcher(request.getUrl()).matches()) {
                result.add(request);
            }
        }
        return result;
    }

    public void clearAll() {
        lock.writeLock().lock();
        try {
            requestQueue.clear();
            responseQueue.clear();
            wsMessageQueue.clear();
            requestIndex.clear();
            responseIndex.clear();
            ((JavascriptExecutor) driver).executeScript("window.__networkEvents = [];");
            logger.info("All network records cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== 辅助方法 ====================

    private <T> void trimQueue(Queue<T> queue, int maxSize) {
        while (queue.size() > maxSize) queue.poll();
    }

    private <K, V> void trimMap(ConcurrentHashMap<K, V> map, int maxSize) {
        if (map.size() > maxSize) {
            int toRemove = map.size() - maxSize;
            map.entrySet().stream().limit(toRemove).map(Map.Entry::getKey).forEach(map::remove);
        }
    }

    private void flushQueues() { logger.debug("Flushing queues"); }

    // ==================== 统计信息 ====================

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", requestQueue.size());
        stats.put("totalResponses", responseQueue.size());
        stats.put("totalWebSocketMessages", wsMessageQueue.size());
        
        Map<String, Integer> statusCounts = new HashMap<>();
        for (ResponseInfo r : responseQueue) {
            String cat = r.getStatus() < 200 ? "informational" :
                        r.getStatus() < 300 ? "success" :
                        r.getStatus() < 400 ? "redirect" :
                        r.getStatus() < 500 ? "clientError" : "serverError";
            statusCounts.merge(cat, 1, Integer::sum);
        }
        stats.put("statusCounts", statusCounts);
        return stats;
    }

    public void printStatistics() {
        logger.info("=== Network Statistics ===");
        logger.info("Requests: {}, Responses: {}, WS: {}", 
            requestQueue.size(), responseQueue.size(), wsMessageQueue.size());
    }

    // ==================== 模拟请求 ====================

    public CompletableFuture<String> simulateGet(String url) {
        return simulateRequest("GET", url, null, null);
    }

    public CompletableFuture<String> simulateRequest(String method, String url, 
                                                      Map<String, String> headers, 
                                                      String body) {
        logger.info("Simulating {} request to: {}", method, url);
        
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .method(method, body != null ? java.net.http.HttpRequest.BodyPublishers.ofString(body) 
                                          : java.net.http.HttpRequest.BodyPublishers.noBody());
        if (headers != null) headers.forEach(builder::header);

        return java.net.http.HttpClient.newHttpClient()
            .sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
            .thenApply(r -> { logger.info("Simulated: {} {}", r.statusCode(), url); return r.body(); })
            .exceptionally(e -> { logger.error("Simulate failed: {}", e.getMessage()); return null; });
    }

    // ==================== 静态工厂方法 ====================

    public static NetworkMonitor create(WebDriver driver) { return new NetworkMonitor(driver); }
    public static NetworkMonitor create(WebDriver driver, NetworkConfig config) { return new NetworkMonitor(driver, config); }
    public static NetworkMonitor createPerformance(WebDriver driver) { return new NetworkMonitor(driver, NetworkConfig.performanceConfig()); }
}
