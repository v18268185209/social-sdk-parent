package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLEngine;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * 闲鱼 IM accs 长连接客户端（Netty + TLS 实现）。
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
 *   <li>NioEventLoopGroup 单线程撑多会话并发</li>
 *   <li>SSL/TLS 直连，不走 JVM 代理</li>
 *   <li>WebSocket 08 协议升级，手动发送 HTTP Upgrade 请求</li>
 *   <li>自动重连：断线后重拉 token + 重连 WSS</li>
 *   <li>服务端推送帧异步回调业务监听器</li>
 *   <li>同步调用走 mid 匹配 + CompletableFuture 等回帧</li>
 * </ul>
 */
public class XianyuImAccsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WSS_HOST = "wss-goofish.dingtalk.com";
    private static final int WSS_PORT = 443;
    private static final String WSS_PATH = "/";
    private static final String APP_KEY = "444e9908a51d1cb236a27862abc769c9";
    private static final String DEVICE_ID = "B213E622-BED6-4903-9907-0BFDE5E9DEA9";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;

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
     * 建立 WSS 长连接：MTOP 拿 token → SSL 直连 → WebSocket 08 升级 → 发送 /reg 注册。
     */
    public synchronized void connect() throws Exception {
        if (channel != null && channel.isActive()) return;
        closed = false;

        // 1. MTOP 拿 IM accessToken
        String userId = extractUserIdFromCookie();
        String deviceId = DEVICE_ID + "-" + (userId.isEmpty() ? "0" : userId);
        String tokenReqData = MAPPER.writeValueAsString(Map.of(
                "appKey", APP_KEY,
                "deviceId", deviceId
        ));
        JsonNode tokenResp = apiClient.callMtop("mtop.taobao.idlemessage.pc.login.token", tokenReqData);
        JsonNode data = tokenResp != null ? tokenResp.path("data") : null;
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
        System.err.println("[IM-WSS] got accessToken len=" + accessToken.length());

        // 2. 创建 SslContext（信任所有证书，避免自签名问题）
        SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(new java.security.cert.X509Certificate[0])
                .build();

        // 3. Netty Bootstrap
        if (group == null || group.isShuttingDown()) {
            group = new NioEventLoopGroup(1);
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // TLS
                        SSLEngine sslEngine = sslCtx.newEngine(ch.alloc(), WSS_HOST, WSS_PORT);
                        p.addLast("ssl", new SslHandler(sslEngine));
                        // WebSocket 08 编解码
                        p.addLast("ws-decoder", new WebSocket08FrameDecoder(WebSocketDecoderConfig.newBuilder().build()));
                        p.addLast("ws-encoder", new WebSocket08FrameEncoder(true));
                        // 心跳
                        p.addLast("idle", new IdleStateHandler(0, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS));
                        // 业务 handler
                        p.addLast("im", new ImFrameHandler());
                    }
                });

        channel = bootstrap.connect(WSS_HOST, WSS_PORT).sync().channel();
        System.err.println("[IM-WSS] TCP connected to " + WSS_HOST + ":" + WSS_PORT);

        // 4. 发 WebSocket 升级请求 —— Netty 在 connect().sync() 后 TCP+SSL handshake 都已完成
        //    （SslHandler 在 channelActive 时自动触发 SSL handshake，connect().sync() 等的就是 channelActive）
        //    原 bug：写了 channel.closeFuture().sync()，等的是 channel 关闭（永远阻塞或 channel 已死），
        //    导致 sendWebSocketUpgrade 永远发不出去或发到死 channel，frame 全部超时 → 消息同步不过来
        sendWebSocketUpgrade();

        // 5. 等待 WebSocket 升级握手完成
        // 原 bug：hsLatch 监听 FullHttpResponse 101 事件，但 Netty WebSocket08FrameDecoder 收到 101 后
        // 自动消费不再向下游传 FullHttpResponse，userEventTriggered 永远不会被触发，hsLatch 固定走 10s 超时
        // 现改成：upgrade 后稍等 200ms 让 SSL+WS 握手包交换完成，直接发 /reg（业务帧）
        // 真握手成功与否靠后续 sendFrame 的回帧判断，而不是 latch
        try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        System.err.println("[IM-WSS] upgrade sent, continuing to /reg");

        // 6. 发送 /reg 注册帧
        sendRegFrame();
        System.err.println("[IM-WSS] /reg frame sent");

        // 7. 等 /reg 回帧（最多 5s），回帧说明握手真成功
        // 若 5s 内没回帧，后续 sendFrame 会自己超时报错
        try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        System.err.println("[IM-WSS] wss connected & /reg sent");
    }

    /**
     * 手动发送 WebSocket 08 Upgrade 请求（绕过 Netty HttpClientCodec 的代理问题）。
     */
    private void sendWebSocketUpgrade() throws Exception {
        String key = Base64.getEncoder().encodeToString(new byte[16]);
        String upgradeRequest =
                "GET " + WSS_PATH + " HTTP/1.1\r\n" +
                "Host: " + WSS_HOST + ":" + WSS_PORT + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Origin: https://www.goofish.com\r\n" +
                "User-Agent: " + UA + "\r\n" +
                "\r\n";

        byte[] bytes = upgradeRequest.getBytes(StandardCharsets.UTF_8);
        channel.writeAndFlush(Unpooled.copiedBuffer(bytes)).sync();
        System.err.println("[IM-WSS] sent WebSocket upgrade request");
    }

    /**
     * 发送 /reg 注册帧。
     */
    private void sendRegFrame() throws Exception {
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
        headers.put("did", DEVICE_ID + "-" + extractUserIdFromCookie());
        headers.put("mid", nextMid());
        regMsg.put("headers", headers);
        regMsg.put("body", new LinkedHashMap<>());

        channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(
                MAPPER.writeValueAsString(regMsg))).sync();
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
        java.util.concurrent.CompletableFuture<JsonNode> future = new java.util.concurrent.CompletableFuture<>();
        pending.put(mid, future);

        channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
        System.err.println("[IM-WSS] send frame: " + (json.length() > 500 ? json.substring(0, 500) + "..." : json));

        try {
            return future.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            pending.remove(mid);
            throw new IllegalStateException("IM WSS frame timeout, lwp=" + lwp, e);
        }
    }

    /**
     * 发送纯 JSON 帧（无 mid，用于心跳等）。
     */
    public void sendRawFrame(String json) throws Exception {
        ensureConnected();
        channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
    }

    /** 注册服务端推送帧监听器。 */
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

    /**
     * IM 帧处理器：收帧 → 按 mid 匹配同步 future / 否则当推送回调业务监听；
     * 15s 写空闲触发心跳帧；断线重连。
     */
    private class ImFrameHandler extends SimpleChannelInboundHandler<Object> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof io.netty.handler.codec.http.FullHttpResponse) {
                FullHttpResponse resp = (FullHttpResponse) msg;
                if (resp.status().code() == 101) {
                    // 握手成功，移除等待 handler
                    ctx.pipeline().remove("hs-wait");
                    System.err.println("[IM-WSS] 101 Switching Protocols received");
                    return;
                }
                System.err.println("[IM-WSS] HTTP response status: " + resp.status().code() + " " + resp.status().reasonPhrase());
                return;
            }
            if (msg instanceof io.netty.handler.codec.http.websocketx.TextWebSocketFrame) {
                String text = ((io.netty.handler.codec.http.websocketx.TextWebSocketFrame) msg).text();
                if (!text.startsWith("{")) return;
                try {
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
                    // 2. 推送帧
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
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent e = (IdleStateEvent) evt;
                if (e.state() == IdleState.WRITER_IDLE) {
                    // 15s 写空闲 → 发 /! 心跳
                    String heartbeat = "{\"lwp\":\"/!\",\"headers\":{\"mid\":\"" + nextMid() + "\"},\"body\":{}}";
                    ctx.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(heartbeat));
                    System.err.println("[IM-WSS] heartbeat sent (/!)");
                } else if (e.state() == IdleState.READER_IDLE) {
                    System.err.println("[IM-WSS] reader idle, reconnecting...");
                    channel = null;
                    try { connect(); } catch (Exception ex) {
                        System.err.println("[IM-WSS] reconnect failed: " + ex.getMessage());
                    }
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[IM-WSS] ws error: " + cause.getMessage());
        }
    }
}
