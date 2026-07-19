package cn.net.rjnetwork.xianyu.captcha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CDP 滑块验证配置 - 从 application.yml 读取
 *
 * <pre>
 * cdp:
 *   host: 192.168.1.127
 *   port: 9333
 *   timeout-seconds: 300
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "cdp")
public class CdpCaptchaConfig {

    /** Chrome DevTools Protocol 主机地址 */
    private String host = "192.168.1.127";

    /** CDP WebSocket 端口 */
    private int port = 9333;

    /** 超时秒数（默认 5 分钟） */
    private int timeoutSeconds = 300;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getCdpEndpoint() {
        return String.format("http://%s:%d", host, port);
    }
}
