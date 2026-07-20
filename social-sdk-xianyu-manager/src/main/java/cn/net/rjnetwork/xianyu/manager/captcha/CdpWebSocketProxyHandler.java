^package cn.net.rjnetwork.xianyu.manager.captcha;

import cn.net.rjnetwork.xianyu.captcha.config.CdpCaptchaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DevTools WebSocket 代理：浏览器 DevTools 前端 -> manager 后端 -> CDP Chrome。
 */
@Component
public class CdpWebSocketProxyHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CdpWebSocketProxyHandler.class);

    private final CdpCaptchaConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Map<String, WebSocket> remotes = new ConcurrentHashMap<>();

    public CdpWebSocketProxyHandler(CdpCaptchaConfig config) {
        this.config = config;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String cdpPath = path.replaceFirst("^/cdp-ws", "");
        if (!cdpPath.startsWith("/devtools/")) {
            session.close(CloseStatus.BAD_DATA.withReason("invalid cdp ws path"));
            return;
        }
        String remoteUrl = "ws://" + config.getHost() + ":" + config.getPort() + cdpPath;
        log.info("[CDP-PROXY] websocket proxy open: {} -> {}", session.getId(), remoteUrl);
        WebSocket remote = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(remoteUrl), new RemoteListener(session))
                .join();
        remotes.put(session.getId(), remote);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocket remote = remotes.get(session.getId());
        if (remote == null) return;
        remote.sendText(message.getPayload(), true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocket remote = remotes.remove(session.getId());
        if (remote != null) {
            try { remote.sendClose(WebSocket.NORMAL_CLOSURE, "client closed"); } catch (Exception ignored) {}
        }
        log.info("[CDP-PROXY] websocket proxy closed: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[CDP-PROXY] websocket proxy transport error: {} {}", session.getId(), exception.getMessage());
    }

    private static class RemoteListener implements WebSocket.Listener {
        private final WebSocketSession client;
        private final StringBuilder textBuffer = new StringBuilder();

        RemoteListener(WebSocketSession client) {
            this.client = client;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String text = textBuffer.toString();
                textBuffer.setLength(0);
                try {
                    if (client.isOpen()) {
                        client.sendMessage(new TextMessage(text));
                    }
                } catch (Exception e) {
                    try { client.close(CloseStatus.SERVER_ERROR.withReason(e.getMessage())); } catch (Exception ignored) {}
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            try {
                if (client.isOpen()) client.close(new CloseStatus(statusCode, reason));
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            try {
                if (client.isOpen()) client.close(CloseStatus.SERVER_ERROR.withReason(error.getMessage()));
            } catch (Exception ignored) {}
        }
    }
}
