package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * 闲鱼 IM accs 长连接客户端（真实抓包验证 2026-07-19 CDP）。
 * <p>闲鱼 IM 复用了钉钉的 IM PaaS 基础设施，长连接走：</p>
 * <ul>
 *   <li>wss://wss-goofish.dingtalk.com/ — 业务帧（发消息/拉历史/会话同步）</li>
 *   <li>wss://msgacs.m.taobao.com/accs/auth?token=... — 阿里 accs 鉴权 + 心跳</li>
 *   <li>帧格式：{"lwp":"/r/MessageSend/sendByReceiverScope","headers":{"mid":"... 0"},"body":[...]}</li>
 *   <li>鉴权：先调 mtop.taobao.idlemessage.pc.login.token 拿 accessToken，拼到 accs auth URL</li>
 * </ul>
 */
public class XianyuImAccsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WSS_HOST = "wss://wss-goofish.dingtalk.com/";

    private final XianyuMtopApiClient apiClient;
    private final AtomicLong midSeq = new AtomicLong(System.currentTimeMillis());
    private ImWsClient wsClient;
    private String accessToken;
    /** 缓存最近一次同步拉到的响应帧（按 mid 匹配） */
    private volatile String lastResponse;

    public XianyuImAccsClient(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 建立 accs 长连接：先 MTOP 拿 accessToken，再连 wss-goofish.dingtalk.com。
     */
    public synchronized void connect() throws Exception {
        if (wsClient != null && wsClient.isOpen()) return;

        // 1. MTOP 拿 IM accessToken
        JsonNode tokenResp = apiClient.callMtop("mtop.taobao.idlemessage.pc.login.token", "{}");
        JsonNode data = tokenResp != null ? tokenResp.path("data") : null;
        accessToken = data != null ? data.path("accessToken").asText("") : "";
        if (accessToken.isEmpty()) {
            throw new IllegalStateException("Failed to fetch IM accessToken via pc.login.token");
        }
        System.err.println("[IM-ACCS] got accessToken len=" + accessToken.length());

        // 2. 连 wss-goofish.dingtalk.com（鉴权信息在帧握手时下发，URL 不带 token）
        wsClient = new ImWsClient(URI(WSS_HOST));
        wsClient.connectBlocking();
        System.err.println("[IM-ACCS] wss connected");
    }

    /**
     * 发送 accs JSON 帧（业务路径如 /r/MessageSend/sendByReceiverScope），
     * 等同步响应（同 mid 的回帧），返回响应 JSON。
     */
    public JsonNode sendFrame(String lwp, Object body) throws Exception {
        if (wsClient == null || !wsClient.isOpen()) connect();

        String mid = nextMid();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("lwp", lwp);
        frame.put("headers", Map.of("mid", mid));
        frame.put("body", body);

        String json = MAPPER.writeValueAsString(frame);
        lastResponse = null;
        wsClient.send(json);
        System.err.println("[IM-ACCS] send frame: " + (json.length() > 500 ? json.substring(0, 500) + "..." : json));

        // 等同步响应（最多 8 秒）
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (lastResponse != null) {
                return MAPPER.readTree(lastResponse);
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("IM accs frame timeout, lwp=" + lwp);
    }

    public void close() {
        if (wsClient != null) try { wsClient.close(); } catch (Exception ignored) {}
        wsClient = null;
    }

    private String nextMid() {
        return String.valueOf(midSeq.incrementAndGet()) + " 0";
    }

    /** 内部 WebSocket 客户端，缓存最近收到的帧 */
    private class ImWsClient extends WebSocketClient {
        public ImWsClient(URI uri) {
            super(uri);
            this.connectTimeout = 10000;
        }
        @Override public void onOpen(ServerHandshake h) { System.err.println("[IM-ACCS] ws open"); }
        @Override public void onMessage(String msg) {
            // 缓存回帧（业务路径的响应，含 code/headers/body）
            if (msg != null && msg.startsWith("{")) {
                lastResponse = msg;
            }
        }
        @Override public void onMessage(ByteBuffer b) { /* binary frame, ignore */ }
        @Override public void onClose(int code, String reason, boolean remote) {
            System.err.println("[IM-ACCS] ws close code=" + code + " reason=" + reason);
        }
        @Override public void onError(Exception e) { System.err.println("[IM-ACCS] ws error: " + e.getMessage()); }
    }

    /** URL 编码兜底（accessToken 拼 accs auth URL 时需要） */
    private static String enc(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }
}
