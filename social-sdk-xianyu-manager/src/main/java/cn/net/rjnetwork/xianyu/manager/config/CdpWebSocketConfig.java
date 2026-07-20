^package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.captcha.CdpWebSocketProxyHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * CDP DevTools WebSocket 代理配置。
 * <p>浏览器访问 /cdp-proxy/open 后，DevTools 前端通过 /cdp-ws/devtools/page/{id}
 * 连到本后端，本后端再转发到真实 CDP：192.168.1.127:9333。</p>
 */
@Configuration
@EnableWebSocket
public class CdpWebSocketConfig implements WebSocketConfigurer {

    private final CdpWebSocketProxyHandler cdpWebSocketProxyHandler;

    public CdpWebSocketConfig(CdpWebSocketProxyHandler cdpWebSocketProxyHandler) {
        this.cdpWebSocketProxyHandler = cdpWebSocketProxyHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cdpWebSocketProxyHandler, "/cdp-ws/**")
                .setAllowedOriginPatterns("*");
    }
}
