package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * 闲鱼 IM accs 长连接客户端（Netty 实现，真实抓包验证 2026-07-19 CDP）。
 * <p>闲鱼 IM 复用了钉钉的 IM PaaS 基础设施，长连接走：</p>
 * <ul>
 *   <li>wss://wss-cntaobao.dingtalk.com/ — 业务帧（发消息/拉历史/会话同步）</li>
 *   <li>wss://msgacs.m.taobao.com/accs/auth?token=... — 阿里 accs 鉴权 + 心跳（type=ACK protocol=HEARTBEAT_ACCS_H5）</li>
 *   <li>帧格式：{"lwp":"/r/MessageSend/sendByReceiverScope","headers":{"mid":"... 0"},"body":[...]}</li>
 *   <li>鉴权：先调 mtop.taobao.idlemessage.pc.login.token 拿 accessToken</li>
 *   <li>心跳：accs 长连接服务端约 30s 推一帧 HEARTBEAT，客户端要回 ACK；IdleStateHandler 兜底主动发心跳</li>
 * </ul>
 *
 * <p>Netty 设计要点：</p>
 * <ul>
 *   <li>NioEventLoopGroup 单线程撑多会话并发</li>
 *   <li>IdleStateHandler 30s 写空闲触发心跳帧</li>
 *   <li>自动重连：断线后重拉 token + 重连 wss</li>
 *   <li>服务端推送帧（对方发新消息）异步回调业务监听器</li>
 *   <li>同步调用走 mid 匹配 + CompletableFuture 等回帧</li>
 * </ul>
 */
public class XianyuImAccsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WSS_HOST = "wss-cntaobao.dingtalk.com";
    private static final String WSS_URL = "wss://wss-cntaobao.dingtalk.com/";
    private static final int HEARTBEAT_IDLE_SECONDS = 30;

    private final XianyuMtopApiClient apiClient;
    private final AtomicLong midSeq = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<JsonNode>> pending
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Consumer<JsonNode>> pushListeners = new ConcurrentHashMap<>();

    private NioEventLoopGroup group;
    private Channel channel;
    private volatile String accessToken;
    private volatile boolean closed = false;

    public XianyuImAccsClient(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 建立 accs 长连接：先 MTOP 拿 accessToken，再连 wss-goofish.dingtalk.com。
     * 阻塞等握手完成。
     */
    public synchronized void connect() throws Exception {
        if (channel != null && channel.isActive()) return;
        closed = false;

        // 禁用 JVM 代理（避免 Netty 通过 HTTP 代理连 WSS 时拿到 301）
        System.setProperty("https.proxyHost", "");
        System.setProperty("http.proxyHost", "");
        System.clearProperty("https.proxyPort");
        System.clearProperty("http.proxyPort");
        System.clearProperty("proxySet");
        
        // 1. MTOP 拿 IM accessToken（真实抓包 2026-07-19 CDP：data 需带 appKey + deviceId，否则 FAIL_SYS_BIZPARAM_MISSED）
        //    deviceId 格式 = "<固定 UUID>-<userId>"，userId 从 cookie 的 unb 字段解析
        String userId = extractUserIdFromCookie();
        String deviceId = "B213E622-BED6-4903-9907-0BFDE5E9DEA9-" + (userId.isEmpty() ? "0" : userId);
        String tokenReqData = MAPPER.writeValueAsString(Map.of(
                "appKey", "444e9908a51d1cb236a27862abc769c9",
                "deviceId", deviceId
        ));
        JsonNode tokenResp = apiClient.callMtop("mtop.taobao.idlemessage.pc.login.token", tokenReqData);
        JsonNode data = tokenResp != null ? tokenResp.path("data") : null;
        // 兼容多种字段名：accessToken / access_token / token
        accessToken = "";
        if (data != null) {
            accessToken = data.path("accessToken").asText("");
            if (accessToken.isEmpty()) accessToken = data.path("access_token").asText("");
            if (accessToken.isEmpty()) accessToken = data.path("token").asText("");
            if (accessToken.isEmpty()) accessToken = data.path("tk").asText("");
        }
        if (accessToken.isEmpty()) {
            throw new IllegalStateException("Failed to fetch IM accessToken via pc.login.token, resp=" + tokenResp);
        }
        System.err.println("[IM-ACCS] got accessToken len=" + accessToken.length());

        // 2. Netty Bootstrap 直连 wss-cntaobao.dingtalk.com（禁用代理，避免 301）
        if (group == null || group.isShuttingDown()) group = new NioEventLoopGroup(1);
        
        URI uri = URI.create(WSS_URL);
        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true,
                new DefaultHttpHeaders()
                        .add("Origin", "https://www.goofish.com")
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"),
                65536);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast("http", new HttpClientCodec());
                        p.addLast("aggregator", new HttpObjectAggregator(65536));
                        // 30s 写空闲触发心跳 + 60s 读空闲判定断线
                        p.addLast("idle", new IdleStateHandler(60, HEARTBEAT_IDLE_SECONDS, 0, TimeUnit.SECONDS));
                        p.addLast("ws", new WebSocketClientProtocolHandler(handshaker,
                                WebSocketClientProtocolConfig.newBuilder()
                                        .handleCloseFrames(true)
                                        .dropPongFrames(true)
                                        .webSocketUri(uri)
                                        .build()));
                        p.addLast("im", new ImFrameHandler());
                    }
                });

        // 直连目标 host:port，不走 JVM 代理
        channel = bootstrap.connect(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()).sync().channel();
        // 等握手完成 — Netty 4.1.x 没有 WebSocketClientProtocolHandler.handshakeFuture()，
        // 改在 pipeline 上加一个 handler 监听 HANDSHAKE_COMPLETE 事件
        final CountDownLatch hsLatch = new CountDownLatch(1);
        channel.pipeline().addAfter("ws", "hs-wait", new ChannelInboundHandlerAdapter() {
            @Override public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    hsLatch.countDown();
                }
            }
        });
        hsLatch.await(10, TimeUnit.SECONDS);
        System.err.println("[IM-ACCS] wss connected & handshake done");
    }

    /**
     * 发送 accs JSON 帧（业务路径如 /r/MessageSend/sendByReceiverScope），
     * 等同步响应（同 mid 的回帧），返回响应 JSON。
     */
    public JsonNode sendFrame(String lwp, Object body) throws Exception {
        ensureConnected();

        String mid = nextMid();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("lwp", lwp);
        frame.put("headers", Map.of("mid", mid));
        frame.put("body", body);

        String json = MAPPER.writeValueAsString(frame);
        java.util.concurrent.CompletableFuture<JsonNode> future = new java.util.concurrent.CompletableFuture<>();
        pending.put(mid, future);

        channel.writeAndFlush(new TextWebSocketFrame(json));
        System.err.println("[IM-ACCS] send frame: " + (json.length() > 500 ? json.substring(0, 500) + "..." : json));

        try {
            // 同步等响应最多 8 秒
            return future.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            pending.remove(mid);
            throw new IllegalStateException("IM accs frame timeout, lwp=" + lwp, e);
        }
    }

    /**
     * 注册服务端推送帧监听器（对方发新消息等异步推送）。
     * @param key 监听 key（如 "message"），同名覆盖
     */
    public void addPushListener(String key, Consumer<JsonNode> listener) {
        pushListeners.put(key, listener);
    }

    public void removePushListener(String key) {
        pushListeners.remove(key);
    }

    public synchronized void close() {
        closed = true;
        if (channel != null) try { channel.close(); } catch (Exception ignored) {}
        channel = null;
        if (group != null) try { group.shutdownGracefully(100, 1000, TimeUnit.MILLISECONDS).sync(); } catch (Exception ignored) {}
        group = null;
    }

    private void ensureConnected() throws Exception {
        if (channel == null || !channel.isActive()) connect();
    }

    private String nextMid() {
        return String.valueOf(midSeq.incrementAndGet()) + " 0";
    }

    /** 从 cookie 的 unb 字段解析当前登录用户 id（形如 "2215024781926"），找不到返回空字符串。 */
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

    /** accs 心跳帧（HEARTBEAT_ACCS_H5）真实抓包：{"type":"ACK","protocol":"HEARTBEAT_ACCS_H5","data":""} */
    private String heartbeatFrame() {
        return "{\"type\":\"ACK\",\"protocol\":\"HEARTBEAT_ACCS_H5\",\"data\":\"\"}";
    }

    /**
     * Netty IM 帧处理器：收帧 → 按 mid 匹配同步 future / 否则当推送回调业务监听；
     * IdleStateHandler 触发时发心跳帧；断线重连。
     */
    private class ImFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

        @Override protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
            if (frame instanceof TextWebSocketFrame) {
                String text = ((TextWebSocketFrame) frame).text();
                if (!text.startsWith("{")) return;
                JsonNode json = MAPPER.readTree(text);
                String mid = json.path("headers").path("mid").asText("");
                // 1. mid 匹配的同步回帧
                if (!mid.isEmpty()) {
                    java.util.concurrent.CompletableFuture<JsonNode> f = pending.remove(mid);
                    if (f != null) {
                        f.complete(json);
                        return;
                    }
                }
                // 2. 否则当推送帧回调业务监听器（对方发新消息/会话变更等）
                String lwp = json.path("lwp").asText("");
                System.err.println("[IM-ACCS] push frame lwp=" + lwp + " mid=" + mid);
                for (Consumer<JsonNode> listener : pushListeners.values()) {
                    try { listener.accept(json); } catch (Exception e) { System.err.println("[IM-ACCS] listener err: " + e.getMessage()); }
                }
            } else if (frame instanceof PongWebSocketFrame) {
                // pong 不处理
            }
        }

        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                if (e.state() == IdleState.WRITER_IDLE) {
                    // 30s 写空闲 → 主动发心跳
                    ctx.writeAndFlush(new TextWebSocketFrame(heartbeatFrame()));
                    System.err.println("[IM-ACCS] heartbeat sent");
                } else if (e.state() == IdleState.READER_IDLE) {
                    // 60s 读空闲 → 服务端断连，触发重连
                    System.err.println("[IM-ACCS] reader idle, reconnecting...");
                    channel = null;
                    try { connect(); } catch (Exception ex) { System.err.println("[IM-ACCS] reconnect failed: " + ex.getMessage()); }
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[IM-ACCS] ws error: " + cause.getMessage());
        }
    }
}
