package cn.net.rjnetwork.xianyu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.net.rjnetwork.xianyu.config.XianyuConfig;
import cn.net.rjnetwork.xianyu.model.XianyuMessage;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 闲鱼实时消息客户端（基于 WebSocket）
 */
public class XianyuRealtimeClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(XianyuRealtimeClient.class);

    private final XianyuConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final ScheduledExecutorService workerScheduler;
    private final MessageListener listener;
    private final Map<String, String> sessionCookies;
    private final String myUserId;
    private final String deviceId;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean backgroundJobsStarted = new AtomicBoolean(false);

    private volatile boolean running = false;
    private volatile boolean closeRequested = false;
    private volatile WebSocket webSocket;
    private volatile String accessToken;
    private volatile CompletableFuture<String> pendingConversationFuture;
    private volatile long lastHeartbeatSentAtMillis = 0L;
    private volatile long lastHeartbeatAckAtMillis = 0L;
    private volatile int reconnectAttempts = 0;

    public interface MessageListener {
        void onMessage(XianyuMessage message);

        default void onSystemMessage(Map<String, Object> message) {
        }

        default void onError(Throwable throwable) {
        }
    }

    public XianyuRealtimeClient(
            XianyuConfig config,
            Map<String, String> sessionCookies,
            String myUserId,
            MessageListener listener) {
        this.config = config != null ? config : new XianyuConfig();
        this.sessionCookies = sessionCookies != null ? new LinkedHashMap<>(sessionCookies) : new LinkedHashMap<>();
        this.myUserId = myUserId;
        this.listener = listener != null ? listener : message -> {
        };
        this.deviceId = generateDeviceId(myUserId);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, this.config.getWebsocketConnectTimeoutSeconds())))
                .build();
        this.workerScheduler = Executors.newScheduledThreadPool(3, new NamedThreadFactory("Xianyu-Realtime"));
    }

    public synchronized void connect() throws IOException, InterruptedException {
        if (running && webSocket != null) {
            return;
        }
        closeRequested = false;
        running = true;

        try {
            connectInternal();
            ensureBackgroundJobsStarted();
        } catch (IOException | InterruptedException e) {
            running = false;
            closeWebSocketQuietly();
            throw e;
        } catch (RuntimeException e) {
            running = false;
            closeWebSocketQuietly();
            throw e;
        }
    }

    public boolean isRunning() {
        return running && webSocket != null;
    }

    public String createConversation(String toUserId, String itemId, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        ensureConnected();

        CompletableFuture<String> future = new CompletableFuture<>();
        this.pendingConversationFuture = future;

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("lwp", "/r/SingleChatConversation/create");
        message.put("headers", mapOf("mid", generateMid()));
        message.put("body", List.of(mapOf(
                "pairFirst", toGoofishId(toUserId),
                "pairSecond", toGoofishId(myUserId),
                "bizType", "1",
                "extension", mapOf("itemId", firstNonBlank(itemId, "891198795482")),
                "ctx", mapOf("appVersion", "1.0", "platform", "web")
        )));
        sendJson(message);

        Duration waitTimeout = timeout != null ? timeout : Duration.ofSeconds(15);
        return future.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void sendTextMessage(String chatId, String toUserId, String text) {
        ensureConnected();
        if (isBlank(chatId) || isBlank(toUserId) || isBlank(text)) {
            throw new IllegalArgumentException("chatId/toUserId/text cannot be blank");
        }

        sendJson(buildTextMessageEnvelope(chatId, toUserId, text));
    }

    public void sendImageMessage(String chatId, String toUserId, String imageUrl, int width, int height) {
        ensureConnected();
        if (isBlank(chatId) || isBlank(toUserId) || isBlank(imageUrl)) {
            throw new IllegalArgumentException("chatId/toUserId/imageUrl cannot be blank");
        }

        sendJson(buildImageMessageEnvelope(chatId, toUserId, imageUrl, width, height));
    }

    /**
     * 支持直接传入本地图片路径；若不是 http(s) 链接会先上传到闲鱼 CDN。
     */
    public void sendImageMessageAuto(String chatId, String toUserId, String imageUrlOrPath, int width, int height)
            throws IOException, InterruptedException {
        if (isBlank(imageUrlOrPath)) {
            throw new IllegalArgumentException("imageUrlOrPath cannot be blank");
        }

        String finalUrl = imageUrlOrPath;
        int finalWidth = Math.max(1, width);
        int finalHeight = Math.max(1, height);

        if (!isHttpUrl(imageUrlOrPath)) {
            Path imagePath = Path.of(imageUrlOrPath);
            if (!Files.exists(imagePath) || Files.isDirectory(imagePath)) {
                throw new IOException("Image file not found: " + imageUrlOrPath);
            }
            int[] size = readImageSize(imagePath);
            if (size != null) {
                finalWidth = size[0];
                finalHeight = size[1];
            }
            finalUrl = uploadImageToCdn(imagePath);
        }

        sendImageMessage(chatId, toUserId, finalUrl, finalWidth, finalHeight);
    }

    @Override
    public synchronized void close() {
        closeRequested = true;
        running = false;
        completePendingConversationExceptionally(new IllegalStateException("Realtime client closed"));
        closeWebSocketQuietly();
        workerScheduler.shutdownNow();
    }

    private synchronized void connectInternal() throws IOException, InterruptedException {
        this.accessToken = refreshRealtimeToken();
        if (isBlank(accessToken)) {
            throw new IOException("Xianyu realtime access token is empty");
        }

        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .header("Origin", "https://www.goofish.com")
                .header("User-Agent", config.getRealtimeUserAgent())
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache");

        String cookieHeader = buildCookieHeader(sessionCookies);
        if (!isBlank(cookieHeader)) {
            builder.header("Cookie", cookieHeader);
        }

        try {
            this.webSocket = builder.buildAsync(
                    URI.create(config.getWebsocketUrl()),
                    new InternalWebSocketListener()).join();
        } catch (Exception e) {
            throw new IOException("Connect Xianyu realtime websocket failed: " + e.getMessage(), e);
        }

        long now = System.currentTimeMillis();
        lastHeartbeatAckAtMillis = now;
        lastHeartbeatSentAtMillis = now;
        sendRegisterFrame();
        sendAckDiffFrame();
    }

    private void ensureBackgroundJobsStarted() {
        if (!backgroundJobsStarted.compareAndSet(false, true)) {
            return;
        }

        int heartbeatInterval = Math.max(5, config.getHeartbeatIntervalSeconds());
        workerScheduler.scheduleAtFixedRate(() -> {
            if (!running || closeRequested) {
                return;
            }
            try {
                sendHeartbeatFrame();
                lastHeartbeatSentAtMillis = System.currentTimeMillis();
            } catch (Exception e) {
                listener.onError(e);
                reconnectAsync("heartbeat-send-failed", e);
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);

        int watchdogInterval = Math.max(3, heartbeatInterval / 2);
        workerScheduler.scheduleAtFixedRate(() -> {
            if (!running || closeRequested) {
                return;
            }
            checkHeartbeatTimeout();
        }, watchdogInterval, watchdogInterval, TimeUnit.SECONDS);

        if (config.isRealtimeTokenRefreshEnabled()) {
            int refreshInterval = Math.max(300, config.getRealtimeTokenRefreshIntervalSeconds());
            workerScheduler.scheduleAtFixedRate(() -> {
                if (!running || closeRequested || reconnecting.get()) {
                    return;
                }
                try {
                    String freshToken = refreshRealtimeToken();
                    if (!isBlank(freshToken) && !freshToken.equals(accessToken)) {
                        accessToken = freshToken;
                        reconnectAsync("token-refreshed", null);
                    }
                } catch (Exception e) {
                    listener.onError(e);
                }
            }, refreshInterval, refreshInterval, TimeUnit.SECONDS);
        }
    }

    private void checkHeartbeatTimeout() {
        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(
                Math.max(15, config.getHeartbeatTimeoutSeconds()) * 1000L,
                Math.max(5, config.getHeartbeatIntervalSeconds()) * 3L * 1000L);

        if (lastHeartbeatAckAtMillis > 0 && now - lastHeartbeatAckAtMillis > timeoutMs) {
            IOException timeoutEx = new IOException(
                    "Heartbeat timeout, last ack at " + lastHeartbeatAckAtMillis + ", now=" + now);
            reconnectAsync("heartbeat-timeout", timeoutEx);
        }
    }

    private void reconnectAsync(String reason, Throwable cause) {
        if (!running || closeRequested || !config.isRealtimeAutoReconnect()) {
            if (cause != null) {
                listener.onError(cause);
            }
            return;
        }
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        workerScheduler.execute(() -> {
            try {
                reconnectBlocking(reason, cause);
            } finally {
                reconnecting.set(false);
            }
        });
    }

    private void reconnectBlocking(String reason, Throwable cause) {
        if (cause != null) {
            listener.onError(cause);
        }

        closeWebSocketQuietly();
        int maxAttempts = Math.max(0, config.getRealtimeMaxReconnectAttempts());

        while (running && !closeRequested) {
            reconnectAttempts++;
            if (maxAttempts > 0 && reconnectAttempts > maxAttempts) {
                running = false;
                listener.onError(new IOException("Reconnect exceeded max attempts: " + maxAttempts + ", reason=" + reason));
                return;
            }

            long delay = computeReconnectDelayMillis(reconnectAttempts);
            logger.warn("Reconnect Xianyu realtime websocket, attempt={}, delay={}ms, reason={}",
                    reconnectAttempts, delay, reason);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                listener.onError(e);
                return;
            }

            try {
                connectInternal();
                reconnectAttempts = 0;
                logger.info("Reconnect Xianyu realtime websocket success");
                return;
            } catch (Exception e) {
                listener.onError(e);
            }
        }
    }

    private long computeReconnectDelayMillis(int attempt) {
        long base = Math.max(500L, config.getRealtimeReconnectBaseDelayMillis());
        long max = Math.max(base, config.getRealtimeReconnectMaxDelayMillis());
        int exp = Math.max(0, Math.min(10, attempt - 1));
        long delay = base * (1L << exp);
        return Math.min(max, delay);
    }

    private void completePendingConversationExceptionally(Throwable error) {
        CompletableFuture<String> future = pendingConversationFuture;
        if (future != null && !future.isDone()) {
            future.completeExceptionally(error);
        }
    }

    private synchronized void closeWebSocketQuietly() {
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect").join();
            } catch (Exception e) {
                logger.debug("Close Xianyu realtime websocket ignored: {}", e.getMessage());
            }
        }
    }

    Map<String, Object> buildTextMessageEnvelope(String chatId, String toUserId, String text) {
        if (isBlank(chatId) || isBlank(toUserId) || isBlank(text)) {
            throw new IllegalArgumentException("chatId/toUserId/text cannot be blank");
        }
        Map<String, Object> textPayload = mapOf(
                "contentType", 1,
                "text", mapOf("text", text)
        );
        String encoded = Base64.getEncoder().encodeToString(toJson(textPayload).getBytes(StandardCharsets.UTF_8));
        return buildCustomPayloadEnvelope(chatId, toUserId, encoded);
    }

    Map<String, Object> buildImageMessageEnvelope(String chatId, String toUserId, String imageUrl, int width, int height) {
        if (isBlank(chatId) || isBlank(toUserId) || isBlank(imageUrl)) {
            throw new IllegalArgumentException("chatId/toUserId/imageUrl cannot be blank");
        }
        Map<String, Object> imagePayload = mapOf(
                "contentType", 2,
                "image", mapOf(
                        "pics", List.of(mapOf(
                                "height", Math.max(1, height),
                                "type", 0,
                                "url", imageUrl,
                                "width", Math.max(1, width)
                        ))
                )
        );
        String encoded = Base64.getEncoder().encodeToString(toJson(imagePayload).getBytes(StandardCharsets.UTF_8));
        return buildCustomPayloadEnvelope(chatId, toUserId, encoded);
    }

    Map<String, Object> buildCustomPayloadEnvelope(String chatId, String toUserId, String base64Data) {
        if (isBlank(chatId) || isBlank(toUserId) || isBlank(base64Data)) {
            throw new IllegalArgumentException("chatId/toUserId/base64Data cannot be blank");
        }
        String myReceiver = toGoofishId(myUserId);
        List<String> actualReceivers = isBlank(myReceiver)
                ? List.of(toGoofishId(toUserId))
                : List.of(toGoofishId(toUserId), myReceiver);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("lwp", "/r/MessageSend/sendByReceiverScope");
        envelope.put("headers", mapOf("mid", generateMid()));
        envelope.put("body", List.of(
                mapOf(
                        "uuid", generateUuid(),
                        "cid", toGoofishId(chatId),
                        "conversationType", 1,
                        "content", mapOf(
                                "contentType", 101,
                                "custom", mapOf("type", 1, "data", base64Data)
                        ),
                        "redPointPolicy", 0,
                        "extension", mapOf("extJson", "{}"),
                        "ctx", mapOf("appVersion", "1.0", "platform", "web"),
                        "mtags", mapOf(),
                        "msgReadStatusSetting", 1
                ),
                mapOf("actualReceivers", actualReceivers)
        ));
        return envelope;
    }

    private void sendRegisterFrame() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("lwp", "/reg");
        msg.put("headers", mapOf(
                "cache-header", "app-key token ua wv",
                "app-key", config.getRealtimeAppKey(),
                "token", accessToken,
                "ua", config.getRealtimeUserAgent(),
                "dt", "j",
                "wv", "im:3,au:3,sy:6",
                "sync", "0,0;0;0;",
                "did", deviceId,
                "mid", generateMid()
        ));
        sendJson(msg);
    }

    private void sendAckDiffFrame() {
        long now = System.currentTimeMillis();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("lwp", "/r/SyncStatus/ackDiff");
        msg.put("headers", mapOf("mid", generateMid()));
        msg.put("body", List.of(mapOf(
                "pipeline", "sync",
                "tooLong2Tag", "PNM,1",
                "channel", "sync",
                "topic", "sync",
                "highPts", 0,
                "pts", now * 1000,
                "seq", 0,
                "timestamp", now
        )));
        sendJson(msg);
    }

    private void sendHeartbeatFrame() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("lwp", "/!");
        msg.put("headers", mapOf("mid", generateMid()));
        sendJson(msg);
    }

    private void sendJson(Map<String, Object> payload) {
        ensureConnected();
        webSocket.sendText(toJson(payload), true);
    }

    String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize JSON failed: " + e.getMessage(), e);
        }
    }

    private void ensureConnected() {
        if (!running || webSocket == null) {
            throw new IllegalStateException("Xianyu realtime client is not connected");
        }
    }

    private String refreshRealtimeToken() throws IOException, InterruptedException {
        String cookieToken = extractTokenFromCookie(sessionCookies.get("_m_h5_tk"));
        if (isBlank(cookieToken)) {
            throw new IOException("Cookie token _m_h5_tk not found");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String dataJson = "{\"appKey\":\"" + escapeJson(config.getRealtimeAppKey())
                + "\",\"deviceId\":\"" + escapeJson(deviceId) + "\"}";
        String sign = md5Hex(cookieToken + "&" + timestamp + "&" + config.getMtopAppKey() + "&" + dataJson);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", firstNonBlank(config.getMtopJsv(), "2.7.2"));
        params.put("appKey", firstNonBlank(config.getMtopAppKey(), "34839810"));
        params.put("t", timestamp);
        params.put("sign", sign);
        params.put("v", "1.0");
        params.put("type", "originaljson");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", String.valueOf(Math.max(1000, config.getMtopTimeoutMillis())));
        params.put("api", firstNonBlank(config.getMtopLoginTokenApi(), "mtop.taobao.idlemessage.pc.login.token"));
        params.put("sessionOption", "AutoLoginOnly");
        params.put("dangerouslySetWindvaneParams", "[object Object]");
        params.put("smToken", "token");
        params.put("queryToken", "sm");
        params.put("sm", "sm");
        params.put("spm_cnt", "a21ybx.im.0.0");
        params.put("spm_pre", "a21ybx.home.sidebar.1.4c053da6vYwnmf");
        params.put("log_id", "social-sdk");

        String endpoint = "https://h5api.m.goofish.com/h5/"
                + params.get("api") + "/1.0/"
                + "?" + buildQuery(params);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(Math.max(2000, config.getMtopTimeoutMillis())))
                .header("Accept", "application/json")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://www.goofish.com")
                .header("Referer", "https://www.goofish.com/")
                .header("User-Agent", config.getRealtimeUserAgent())
                .header("Cookie", buildCookieHeader(sessionCookies))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "data=" + URLEncoder.encode(dataJson, StandardCharsets.UTF_8)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Realtime token API HTTP status: " + response.statusCode());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IOException("Parse realtime token response failed: " + e.getMessage(), e);
        }

        if (!isMtopSuccess(root)) {
            throw new IOException("Realtime token API failed: " + response.body());
        }

        String token = root.path("data").path("accessToken").asText(null);
        if (isBlank(token)) {
            throw new IOException("Realtime token API missing accessToken");
        }
        return token;
    }

    private String uploadImageToCdn(Path imagePath) throws IOException, InterruptedException {
        byte[] fileBytes = Files.readAllBytes(imagePath);
        if (fileBytes.length == 0) {
            throw new IOException("Image file is empty: " + imagePath);
        }

        String filename = imagePath.getFileName() != null ? imagePath.getFileName().toString() : "image.jpg";
        String contentType = firstNonBlank(Files.probeContentType(imagePath), "image/jpeg");
        String boundary = "----SocialSdkXianyu" + UUID.randomUUID().toString().replace("-", "");

        byte[] body = buildMultipartBody(boundary, "file", filename, contentType, fileBytes);

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getRealtimeImageUploadUrl()))
                .timeout(Duration.ofMillis(Math.max(2000, config.getMtopTimeoutMillis())))
                .header("Cookie", buildCookieHeader(sessionCookies))
                .header("Referer", "https://www.goofish.com/")
                .header("Origin", "https://www.goofish.com")
                .header("User-Agent", config.getRealtimeUserAgent())
                .header("x-requested-with", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Upload image HTTP status: " + response.statusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            String url = firstNonBlank(
                    root.path("data").path("url").asText(null),
                    root.path("object").path("url").asText(null),
                    root.path("url").asText(null),
                    root.path("result").path("url").asText(null),
                    root.path("data").path("fileUrl").asText(null),
                    root.path("data").path("file_url").asText(null));
            if (isBlank(url)) {
                throw new IOException("Upload image response missing URL: " + response.body());
            }
            return url;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Parse upload image response failed: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipartBody(
            String boundary,
            String fieldName,
            String filename,
            String contentType,
            byte[] fileData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(fileData);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private int[] readImageSize(Path imagePath) {
        try {
            BufferedImage bufferedImage = ImageIO.read(imagePath.toFile());
            if (bufferedImage != null && bufferedImage.getWidth() > 0 && bufferedImage.getHeight() > 0) {
                return new int[]{bufferedImage.getWidth(), bufferedImage.getHeight()};
            }
        } catch (Exception e) {
            logger.debug("Read image size failed: {}", e.getMessage());
        }
        return null;
    }

    boolean isMtopSuccess(JsonNode root) {
        JsonNode ret = root.path("ret");
        if (!ret.isArray() || ret.isEmpty()) {
            return false;
        }
        return ret.get(0).asText("").contains("SUCCESS");
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 calculation failed", e);
        }
    }

    String extractTokenFromCookie(String tokenCookie) {
        if (isBlank(tokenCookie)) {
            return null;
        }
        int idx = tokenCookie.indexOf('_');
        if (idx > 0) {
            return tokenCookie.substring(0, idx);
        }
        return tokenCookie;
    }

    String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private String generateDeviceId(String userId) {
        return UUID.randomUUID() + "-" + firstNonBlank(userId, "0");
    }

    private String generateMid() {
        long ts = System.currentTimeMillis();
        int random = (int) (Math.random() * 1000);
        return random + ts + " 0";
    }

    private String generateUuid() {
        return "-" + System.currentTimeMillis() + "1";
    }

    String toGoofishId(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.contains("@") ? trimmed : trimmed + "@goofish";
    }

    private String extractItemId(String reminderUrl) {
        if (isBlank(reminderUrl)) {
            return null;
        }
        int idx = reminderUrl.indexOf("itemId=");
        if (idx < 0) {
            return null;
        }
        String tail = reminderUrl.substring(idx + "itemId=".length());
        int amp = tail.indexOf("&");
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }

    private String extractItemIdRecursive(JsonNode node, int depth) {
        if (node == null || depth > 8) {
            return null;
        }
        if (node.isObject()) {
            JsonNode itemIdNode = node.get("itemId");
            if (itemIdNode != null && itemIdNode.isValueNode()) {
                String itemId = itemIdNode.asText(null);
                if (!isBlank(itemId)) {
                    return itemId;
                }
            }
            JsonNode itemIdAltNode = node.get("item_id");
            if (itemIdAltNode != null && itemIdAltNode.isValueNode()) {
                String itemId = itemIdAltNode.asText(null);
                if (!isBlank(itemId)) {
                    return itemId;
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String value = extractItemIdRecursive(field.getValue(), depth + 1);
                if (!isBlank(value)) {
                    return value;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String value = extractItemIdRecursive(child, depth + 1);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isHttpUrl(String value) {
        if (isBlank(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvs.length; i += 2) {
            if (kvs[i] != null) {
                map.put(kvs[i].toString(), kvs[i + 1]);
            }
        }
        return map;
    }

    JsonNode decodeSyncMessageNode(String encoded) {
        if (isBlank(encoded)) {
            return null;
        }

        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(encoded);
        } catch (Exception e) {
            return null;
        }

        String decodedText = new String(decodedBytes, StandardCharsets.UTF_8);
        try {
            return objectMapper.readTree(decodedText);
        } catch (Exception ignore) {
            // Fallback to MessagePack decode
        }

        Object msgPackObj = decodeMsgPack(decodedBytes);
        if (msgPackObj != null) {
            return objectMapper.valueToTree(msgPackObj);
        }
        return null;
    }

    private Object decodeMsgPack(byte[] bytes) {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes)) {
            return unpackMsgPackValue(unpacker);
        } catch (Exception e) {
            return null;
        }
    }

    private Object unpackMsgPackValue(MessageUnpacker unpacker) throws IOException {
        MessageFormat format = unpacker.getNextFormat();
        ValueType valueType = format.getValueType();

        if (valueType == ValueType.NIL) {
            unpacker.unpackNil();
            return null;
        }
        if (valueType == ValueType.BOOLEAN) {
            return unpacker.unpackBoolean();
        }
        if (valueType == ValueType.INTEGER) {
            return unpacker.unpackLong();
        }
        if (valueType == ValueType.FLOAT) {
            return unpacker.unpackDouble();
        }
        if (valueType == ValueType.STRING) {
            return unpacker.unpackString();
        }
        if (valueType == ValueType.BINARY) {
            int len = unpacker.unpackBinaryHeader();
            byte[] payload = unpacker.readPayload(len);
            return Base64.getEncoder().encodeToString(payload);
        }
        if (valueType == ValueType.ARRAY) {
            int size = unpacker.unpackArrayHeader();
            List<Object> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(unpackMsgPackValue(unpacker));
            }
            return list;
        }
        if (valueType == ValueType.MAP) {
            int size = unpacker.unpackMapHeader();
            Map<String, Object> map = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                Object keyObj = unpackMsgPackValue(unpacker);
                Object valueObj = unpackMsgPackValue(unpacker);
                map.put(keyObj != null ? keyObj.toString() : "null", valueObj);
            }
            return map;
        }
        if (valueType == ValueType.EXTENSION) {
            ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
            byte[] payload = unpacker.readPayload(extHeader.getLength());
            Map<String, Object> ext = new LinkedHashMap<>();
            ext.put("type", extHeader.getType());
            ext.put("payloadBase64", Base64.getEncoder().encodeToString(payload));
            return ext;
        }

        // Should not reach here, but keep parser resilient.
        return unpacker.unpackValue().toJson();
    }

    private String extractCardTitle(JsonNode oneNode) {
        if (oneNode == null || !oneNode.isObject()) {
            return null;
        }

        JsonNode jsonTextNode = oneNode.path("6").path("3").path("5");
        if (!jsonTextNode.isTextual()) {
            return null;
        }

        try {
            JsonNode cardRoot = objectMapper.readTree(jsonTextNode.asText());
            return cardRoot.path("dxCard")
                    .path("item")
                    .path("main")
                    .path("exContent")
                    .path("title")
                    .asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveMessageContent(JsonNode messageNode, JsonNode oneNode, JsonNode detailNode) {
        String content = firstNonBlank(
                detailNode.path("reminderContent").asText(null),
                detailNode.path("text").path("text").asText(null),
                detailNode.path("text").asText(null),
                detailNode.path("content").asText(null));
        if (!isBlank(content)) {
            return content;
        }

        String cardTitle = extractCardTitle(oneNode);
        if (!isBlank(cardTitle)) {
            return "[卡片消息] " + cardTitle;
        }

        content = firstNonBlank(
                detailNode.path("reminderTitle").asText(null),
                messageNode.path("operation").path("content").path("title").asText(null));
        return content;
    }

    String normalizeChatId(String chatId) {
        if (isBlank(chatId)) {
            return chatId;
        }
        return chatId.contains("@") ? chatId.substring(0, chatId.indexOf('@')) : chatId;
    }

    private String findFirstFieldValue(JsonNode node, String fieldName, int depth) {
        if (node == null || depth > 8) {
            return null;
        }
        if (node.isObject()) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isValueNode()) {
                String text = value.asText(null);
                if (!isBlank(text)) {
                    return text;
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String found = findFirstFieldValue(entry.getValue(), fieldName, depth + 1);
                if (!isBlank(found)) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findFirstFieldValue(child, fieldName, depth + 1);
                if (!isBlank(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private final class InternalWebSocketListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String payload = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    handleIncoming(payload);
                } catch (Exception e) {
                    listener.onError(e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            XianyuRealtimeClient.this.webSocket = null;
            logger.info("Xianyu realtime websocket closed: code={}, reason={}", statusCode, reason);
            if (running && !closeRequested) {
                reconnectAsync("ws-closed-" + statusCode, null);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            XianyuRealtimeClient.this.webSocket = null;
            listener.onError(error);
            if (running && !closeRequested) {
                reconnectAsync("ws-error", error);
            }
        }

        private void handleIncoming(String payload) throws IOException {
            JsonNode root = objectMapper.readTree(payload);
            if (root.path("code").asInt(-1) == 200) {
                lastHeartbeatAckAtMillis = System.currentTimeMillis();
            }

            sendAckIfNeeded(root);
            captureConversationId(root);

            JsonNode syncArray = root.path("body").path("syncPushPackage").path("data");
            if (!syncArray.isArray() || syncArray.isEmpty()) {
                return;
            }

            for (JsonNode syncNode : syncArray) {
                JsonNode encodedNode = syncNode.path("data");
                if (!encodedNode.isTextual()) {
                    continue;
                }

                JsonNode messageNode = decodeSyncMessageNode(encodedNode.asText());
                if (messageNode == null || messageNode.isMissingNode() || messageNode.isNull()) {
                    continue;
                }
                dispatchDecodedMessage(messageNode);
            }
        }

        private void dispatchDecodedMessage(JsonNode messageNode) {
            try {
                if (messageNode.has("chatType")) {
                    Map<String, Object> systemMsg = objectMapper.convertValue(
                            messageNode, new TypeReference<Map<String, Object>>() {
                            });
                    listener.onSystemMessage(systemMsg);
                    return;
                }

                JsonNode oneNode = messageNode.path("1");
                JsonNode detailNode = oneNode.path("10");
                if (!oneNode.isObject() || !detailNode.isObject()) {
                    Map<String, Object> systemMsg = objectMapper.convertValue(
                            messageNode, new TypeReference<Map<String, Object>>() {
                            });
                    listener.onSystemMessage(systemMsg);
                    return;
                }

                String senderUserId = detailNode.path("senderUserId").asText(null);
                String senderNick = firstNonBlank(
                        detailNode.path("senderNick").asText(null),
                        detailNode.path("reminderTitle").asText(null));
                String content = resolveMessageContent(messageNode, oneNode, detailNode);
                String reminderUrl = detailNode.path("reminderUrl").asText(null);

                String chatId = normalizeChatId(firstNonBlank(
                        oneNode.path("2").asText(null),
                        messageNode.path("2").asText(null)));

                long ts = oneNode.path("5").asLong(0L);
                if (ts <= 0L) {
                    ts = System.currentTimeMillis();
                }

                String itemId = firstNonBlank(
                        extractItemId(reminderUrl),
                        extractItemIdRecursive(messageNode, 0));

                XianyuMessage message = new XianyuMessage();
                message.setChatId(chatId);
                message.setSessionId(chatId);
                message.setSenderUserId(senderUserId);
                message.setSenderNick(senderNick);
                message.setReceiverUserId(myUserId);
                message.setItemId(itemId);
                message.setContent(content);
                message.setTimestamp(Instant.ofEpochMilli(ts));
                message.setOutgoing(myUserId != null && myUserId.equals(senderUserId));
                message.setRawData(messageNode.toString());
                listener.onMessage(message);
            } catch (Exception e) {
                listener.onError(e);
            }
        }

        private void captureConversationId(JsonNode root) {
            String cid = firstNonBlank(
                    root.path("body").path("singleChatConversation").path("cid").asText(null),
                    root.path("body").path("conversation").path("cid").asText(null),
                    findFirstFieldValue(root.path("body"), "cid", 0),
                    findFirstFieldValue(root, "cid", 0));
            cid = normalizeChatId(cid);

            if (isBlank(cid)) {
                return;
            }

            CompletableFuture<String> future = pendingConversationFuture;
            if (future != null && !future.isDone()) {
                future.complete(cid);
            }
        }

        private void sendAckIfNeeded(JsonNode root) {
            JsonNode headersNode = root.path("headers");
            if (!headersNode.isObject()) {
                return;
            }
            Map<String, Object> ackHeaders = new LinkedHashMap<>();
            ackHeaders.put("mid", firstNonBlank(headersNode.path("mid").asText(null), generateMid()));
            ackHeaders.put("sid", firstNonBlank(headersNode.path("sid").asText(null), ""));

            if (headersNode.has("app-key")) {
                ackHeaders.put("app-key", headersNode.path("app-key").asText());
            }
            if (headersNode.has("ua")) {
                ackHeaders.put("ua", headersNode.path("ua").asText());
            }
            if (headersNode.has("dt")) {
                ackHeaders.put("dt", headersNode.path("dt").asText());
            }

            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("code", 200);
            ack.put("headers", ackHeaders);
            try {
                sendJson(ack);
            } catch (Exception e) {
                logger.debug("Send ACK ignored: {}", e.getMessage());
            }
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
