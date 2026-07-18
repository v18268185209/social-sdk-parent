package cn.net.rjnetwork.starter.properties;

import cn.net.rjnetwork.core.constant.SocialPlatform;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Social SDK 全局配置属性
 */
@Component
@ConfigurationProperties(prefix = "social-sdk")
public class SocialSdkProperties {

    private boolean enabled = true;
    private SocialPlatform defaultPlatform;
    private int timeout = 30000;
    private int connectTimeout = 10000;
    private int readTimeout = 30000;
    private Proxy proxy;
    private Map<String, Object> platforms = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SocialPlatform getDefaultPlatform() {
        return defaultPlatform;
    }

    public void setDefaultPlatform(SocialPlatform defaultPlatform) {
        this.defaultPlatform = defaultPlatform;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public Map<String, Object> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(Map<String, Object> platforms) {
        this.platforms = platforms;
    }

    /**
     * 代理配置
     */
    public static class Proxy {
        private String host;
        private int port = 8080;
        private String username;
        private String password;
        private boolean enabled = false;

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

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
