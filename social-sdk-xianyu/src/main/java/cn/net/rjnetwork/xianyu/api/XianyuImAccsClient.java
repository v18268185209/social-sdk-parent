package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 闲鱼 IM accs 长连接客户端（纯 Java Socket + TLS + 手工 WebSocket 帧）。
 * <p>闲鱼 IM 复用了钉钉的 IM PaaS 基础设施，长连接走：</p>
 * <ul>
 *   <li>wss://wss-goofish.dingtalk.com/ — 业务帧（发消息/拉历史/会话同步）</li>
 *   <li>帧格式：{"lwp":"/r/MessageSend/sendByReceiverScope","headers":{"mid":"..."},"body":[...]}</li>
 *   <li>鉴权：先调 mtop.taobao.idlemessage.pc.login.token 拿 accessToken</li>
 *   <li>注册：连接成功后发 /reg LWP 帧注册设备</li>
 *   <li>心跳：服务端约 30s 推一帧，客户端 15s 主动发 /! 心跳</li>
 * </ul>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>纯 Socket 实现，不依赖 Netty（避免 Netty WebSocket 编解码与 HTTP Upgrade 事件竞态问题）</li>
 *   <li>SSL/TLS 直连，不走 JVM 代理</li>
 *   <li>手工实现 WebSocket 握手（GET / HTTP/1.1 Upgrade）和文本帧收发</li>
 *   <li>自动重连：断线后重拉 token + 重连 WSS</li>
 *   <li>服务端推送帧异步回调业务监听器</li>
 *   <li>同步调用走 mid 匹配 + CompletableFuture 等回帧</li>
 * </ul>
 */
public class XianyuImAccsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WSS_HOST = "wss-goofish.dingtalk.com";
    private static final int WSS_PORT = 443;
    private static final String APP_KEY = "444e9908a51d1cb236a27862abc769c9";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 DingTalk(2.1.5) OS(Windows/10) Browser(Chrome/146.0.0.0) DingWeb/2.1.5 IMPaaS DingWeb/2.1.5";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final XianyuMtopApiClient apiClient;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Consumer<JsonNode>> pushListeners = new ConcurrentHashMap<>();

    private volatile SSLSocket socket;
    private volatile boolean closed = false;
    private volatile boolean connected = false;
    private Thread readThread;
    private volatile String deviceId;

    public XianyuImAccsClient(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 建立 WSS 长连接：MTOP 拿 token → TLS 直连 → WebSocket 08 升级 → 发送 /reg 注册。
     *
     * @throws Exception 连接失败或帧超时
     */
    public synchronized void connect() throws Exception {
        if (socket != null && connected) return;
        closed = false;

        // 1. MTOP 拿 IM accessToken
        String userId = extractUserIdFromCookie();
        this.deviceId = generateDeviceId(userId);
        String tokenReqData = MAPPER.writeValueAsString(Map.of(
                "appKey", APP_KEY,
                "deviceId", this.deviceId
        ));
        JsonNode tokenResp = apiClient.callMtop("mtop.taobao.idlemessage.pc.login.token", tokenReqData);
        JsonNode data = tokenResp != null ? tokenResp.path("data") : null;
        String accessToken = "";
        if (data != null) {
            accessToken = data.path("accessToken").asText("");
            if (accessToken.isEmpty()) accessToken = data.path("access_token").asText("");
            if (accessToken.isEmpty()) accessToken = data.path("token").asText("");
            if (accessToken.isEmpty()) accessToken = data.path("tk").asText("");
        }
        if (accessToken.isEmpty()) {
            throw new IllegalStateException("Failed to fetch IM accessToken via pc.login.token, resp=" + tokenResp);
        }
        System.err.println("[IM-WSS] got accessToken len=" + accessToken.length());

        // 2. TLS 直连
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = (SSLSocket) factory.createSocket(WSS_HOST, WSS_PORT);
        socket.setSoTimeout(0); // 设置框架读取超时，而非 socket timeout
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
        ((javax.net.ssl.SSLSocket) socket).startHandshake();
        System.err.println("[IM-WSS] TLS handshake complete");

        // 3. WebSocket 08 升级握手
        performWebSocketUpgrade();

        // 4. 启动后台读线程
        startReadThread();
        connected = true;

        // 5. 发送 /reg 注册帧（同步等待回帧确认注册成功）
        sendRegFrame(accessToken);

        // 6. 注册后必须同步并确认 sync 状态，否则业务 LWP 容易返回 code=400
        initializeSyncState();
        try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        System.err.println("[IM-WSS] wss connected, /reg and sync initialized");
    }

    /**
     * 手工 WebSocket 08 升级握手。
     * 发送 GET / HTTP/1.1 Upgrade 请求，验证 101 Switching Protocols。
     */
    private void performWebSocketUpgrade() throws Exception {
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        String key = Base64.getEncoder().encodeToString(keyBytes);

        String upgradeRequest =
                "GET / HTTP/1.1\r\n" +
                "Host: " + WSS_HOST + ":" + WSS_PORT + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Origin: https://www.goofish.com\r\n" +
                "User-Agent: " + UA + "\r\n" +
                "Cookie: " + apiClient.getMergedCookie() + "\r\n" +
                "\r\n";

        OutputStream out = socket.getOutputStream();
        out.write(upgradeRequest.getBytes(StandardCharsets.UTF_8));
        out.flush();
        System.err.println("[IM-WSS] sent WebSocket upgrade request");

        InputStream in = socket.getInputStream();
        // 读取响应头
        byte[] buf = new byte[4096];
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int totalRead = 0;
        while (totalRead < buf.length) {
            int n = in.read(buf, 0, buf.length - totalRead);
            if (n == -1) {
                throw new java.io.IOException("WebSocket upgrade: connection closed");
            }
            headerBuf.write(buf, 0, n);
            totalRead += n;
            // 检查是否收到完整的 HTTP 响应头
            byte[] all = headerBuf.toByteArray();
            int headerEnd = findHeaderEnd(all);
            if (headerEnd >= 0) {
                String response = new String(all, 0, headerEnd, StandardCharsets.UTF_8);
                if (!response.contains("101")) {
                    throw new java.io.IOException("WebSocket upgrade failed: " + response.substring(0, Math.min(200, response.length())));
                }
                System.err.println("[IM-WSS] 101 Switching Protocols received");
                break;
            }
        }
    }

    private int findHeaderEnd(byte[] data) {
        for (int i = 0; i <= data.length - 4; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    /**
     * 启动后台读线程，持续读取 WebSocket 帧并分发。
     */
    private void startReadThread() {
        readThread = new Thread(() -> {
            try {
                InputStream in = socket.getInputStream();
                byte[] readBuf = new byte[8192];
                ByteArrayOutputStream fragmentBuffer = new ByteArrayOutputStream();

                while (!closed && !socket.isClosed()) {
                    int n = in.read(readBuf);
                    if (n == -1) {
                        System.err.println("[IM-WSS] stream ended, reconnecting...");
                        closeQuietly();
                        connected = false;
                        // 尝试重连（通过外层 ensureConnected）
                        try {
                            Thread.sleep(2000);
                            connect();
                        } catch (Exception re) {
                            System.err.println("[IM-WSS] reconnect failed: " + re.getMessage());
                        }
                        return;
                    }
                    fragmentBuffer.write(readBuf, 0, n);

                    // 尝试从缓冲区解析完整帧
                    processAvailableFrames(fragmentBuffer);
                }
            } catch (Exception e) {
                if (!closed) {
                    System.err.println("[IM-WSS] read thread error: " + e.getMessage());
                    closeQuietly();
                    connected = false;
                }
            }
        }, "xianyu-im-wss-reader");
        readThread.setDaemon(true);
        readThread.start();
    }

    /**
     * 从 fragmentBuffer 中持续读取完整 WebSocket 文本帧。
     */
    private void processAvailableFrames(ByteArrayOutputStream fragmentBuffer) {
        while (true) {
            byte[] buf = fragmentBuffer.toByteArray();
            if (buf.length < 2) return; // 帧头不完整

            int offset = 0;
            int opcode = buf[offset] & 0x0F;

            // 只处理文本帧（opcode=1）和关闭帧（opcode=8）
            if (opcode != 1 && opcode != 8) {
                // 二进制帧或非帧数据忽略
                fragmentBuffer.reset();
                return;
            }

            // 读取长度
            int secondByte = buf[offset + 1] & 0xFF;
            long payloadLen = secondByte & 0x7F;
            offset += 2;

            if (payloadLen == 126) {
                if (buf.length < offset + 2) return;
                payloadLen = ((long)(buf[offset] & 0xFF) << 8) | (buf[offset + 1] & 0xFF);
                offset += 2;
            } else if (payloadLen == 127) {
                if (buf.length < offset + 8) return;
                payloadLen = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLen = (payloadLen << 8) | (buf[offset + i] & 0xFF);
                }
                offset += 8;
            }

            // 检查是否收到掩码密钥（服务端不应该发送masked帧，但以防万一）
            boolean masked = (secondByte & 0x80) != 0;
            int maskKeyLen = masked ? 4 : 0;
            if (buf.length < offset + maskKeyLen + payloadLen) return;

            offset += maskKeyLen;

            byte[] payload = new byte[(int) payloadLen];
            System.arraycopy(buf, offset, payload, 0, payload.length);

            // 如果服务端发送了masked帧，需要解掩码
            if (masked) {
                byte[] maskKey = new byte[4];
                System.arraycopy(buf, offset - 4, maskKey, 0, 4);
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }

            // 删除已处理的帧
            fragmentBuffer.reset();
            if (buf.length > offset + payload.length) {
                fragmentBuffer.write(buf, offset + payload.length, buf.length - offset - payload.length);
            }

            // 处理帧
            if (opcode == 8) {
                System.err.println("[IM-WSS] server sent close frame");
                return;
            }

            // 文本帧处理
            String text = new String(payload, StandardCharsets.UTF_8);
            handleFrame(text);
        }
    }

    /**
     * 处理收到的 LWP JSON 帧：按 mid 匹配回帧 / 推送给监听器。
     */
    private void handleFrame(String text) {
        if (!text.startsWith("{")) return;
        try {
            JsonNode json = MAPPER.readTree(text);
            String mid = json.path("headers").path("mid").asText("");
            ackFrame(json, mid);

            // 1. mid 匹配的同步回帧
            if (!mid.isEmpty()) {
                CompletableFuture<JsonNode> f = pending.remove(mid);
                if (f != null) {
                    f.complete(json);
                    return;
                }
            }

            // 2. 推送帧（无 mid 或 mid 未匹配）
            String lwp = json.path("lwp").asText("");
            System.err.println("[IM-WSS] push frame lwp=" + lwp + " mid=" + mid);
            for (Consumer<JsonNode> listener : pushListeners.values()) {
                try {
                    listener.accept(json);
                } catch (Exception e) {
                    System.err.println("[IM-WSS] listener err: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[IM-WSS] parse frame error: " + e.getMessage());
        }
    }

    /**
     * 发送 /reg 注册帧。
     */
    private void sendRegFrame(String accessToken) throws Exception {
        Map<String, Object> regMsg = new LinkedHashMap<>();
        regMsg.put("lwp", "/reg");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("cache-header", "app-key token ua wv");
        headers.put("app-key", APP_KEY);
        headers.put("token", accessToken);
        headers.put("ua", UA);
        headers.put("dt", "j");
        headers.put("wv", "im:3,au:3,sy:6");
        headers.put("sync", "0,0;0;0;");
        headers.put("did", deviceId != null && !deviceId.isEmpty() ? deviceId : generateDeviceId(extractUserIdFromCookie()));
        String frameId = nextMid();
        headers.put("mid", frameId);
        regMsg.put("headers", headers);
        regMsg.put("body", new LinkedHashMap<>());

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(frameId, future);

        writeFrame(MAPPER.writeValueAsString(regMsg));

        try {
            JsonNode resp = future.get(10, TimeUnit.SECONDS);
            System.err.println("[IM-WSS] /reg response received: " + resp.path("ret").path(0).asText("ok"));
        } catch (Exception e) {
            pending.remove(frameId);
            throw new IllegalStateException("IM /reg timeout", e);
        }
    }

    private void ackFrame(JsonNode json, String mid) {
        try {
            if (mid == null || mid.isEmpty()) return;
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("code", 200);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("mid", mid);
            String sid = json.path("headers").path("sid").asText("");
            if (!sid.isEmpty()) headers.put("sid", sid);
            for (String key : new String[]{"app-key", "ua", "dt"}) {
                String value = json.path("headers").path(key).asText("");
                if (!value.isEmpty()) headers.put(key, value);
            }
            ack.put("headers", headers);
            writeFrame(MAPPER.writeValueAsString(ack));
        } catch (Exception e) {
            System.err.println("[IM-WSS] ack frame failed: " + e.getMessage());
        }
    }

    private void initializeSyncState() throws Exception {
        long currentTime = System.currentTimeMillis();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("pipeline", "sync");
        state.put("tooLong2Tag", "PNM,1");
        state.put("channel", "sync");
        state.put("topic", "sync");
        state.put("highPts", 0);
        state.put("pts", currentTime * 1000);
        state.put("seq", 0);
        state.put("timestamp", currentTime);
        sendRawFrame(MAPPER.writeValueAsString(Map.of(
                "lwp", "/r/SyncStatus/ackDiff",
                "headers", Map.of("mid", nextMid()),
                "body", new Object[]{state}
        )));
        try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        System.err.println("[IM-WSS] sync ackDiff sent");
    }

    /**
     * 发送 LWP 业务帧（如 /r/Conversation/listNewestPagination、/r/MessageManager/listUserMessages）。
     */
    public JsonNode sendFrame(String lwp, Object body) throws Exception {
        ensureConnected();
        String mid = nextMid();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("lwp", lwp);
        frame.put("headers", Map.of("mid", mid));
        frame.put("body", body);

        String json = MAPPER.writeValueAsString(frame);
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(mid, future);

        writeFrame(json);
        System.err.println("[IM-WSS] send frame lwp=" + lwp + " mid=" + mid);

        try {
            return future.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            pending.remove(mid);
            throw new IllegalStateException("IM WSS frame timeout, lwp=" + lwp, e);
        }
    }

    /**
     * 发送纯文本帧（无 mid，用于心跳等）。
     */
    public void sendRawFrame(String json) throws Exception {
        ensureConnected();
        writeFrame(json);
    }

    /**
     * 向 WebSocket 连接写入一个文本帧（opcode=0x81）。
     */
    private void writeFrame(String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] maskKey = new byte[4];
        RANDOM.nextBytes(maskKey);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x81); // FIN + text opcode

        int len = payload.length;
        if (len < 126) {
            out.write(0x80 | len);
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            for (int i = 56; i >= 0; i -= 8) {
                out.write((len >> i) & 0xFF);
            }
        }

        out.write(maskKey);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ maskKey[i % 4]);
        }
        socket.getOutputStream().write(out.toByteArray());
        socket.getOutputStream().flush();
    }

    /**
     * 确保连接已建立；若断开则自动重连。
     */
    private void ensureConnected() throws Exception {
        if (!connected || socket == null || socket.isClosed()) {
            connect();
        }
    }

    /**
     * 注册服务端推送帧监听器。
     */
    public void addPushListener(String key, Consumer<JsonNode> listener) {
        pushListeners.put(key, listener);
    }

    public void removePushListener(String key) {
        pushListeners.remove(key);
    }

    public synchronized void close() {
        closed = true;
        closeQuietly();
        connected = false;
    }

    private void closeQuietly() {
        if (readThread != null) {
            try { readThread.interrupt(); } catch (Exception ignored) {}
        }
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
        socket = null;
    }

    private String nextMid() {
        int randomPart = RANDOM.nextInt(1000);
        return String.valueOf(randomPart) + System.currentTimeMillis() + " 0";
    }

    private String generateDeviceId(String userId) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder result = new StringBuilder(36);
        for (int i = 0; i < 36; i++) {
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                result.append('-');
            } else if (i == 14) {
                result.append('4');
            } else if (i == 19) {
                result.append(chars.charAt((RANDOM.nextInt(16) & 0x3) | 0x8));
            } else {
                result.append(chars.charAt(RANDOM.nextInt(16)));
            }
        }
        if (userId != null && !userId.isBlank()) {
            result.append('-').append(userId.trim());
        }
        return result.toString();
    }

    private String extractUserIdFromCookie() {
        String cookie = apiClient.getCookie();
        if (cookie == null || cookie.isEmpty()) return "";
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("unb=")) {
                String v = p.substring(4).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }
}
