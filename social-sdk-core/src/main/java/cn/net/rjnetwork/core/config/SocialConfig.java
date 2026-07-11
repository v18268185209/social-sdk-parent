package cn.net.rjnetwork.core.config;

import cn.net.rjnetwork.core.constant.SocialPlatform;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * SDK配置类
 */
public class SocialConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = true;
    private SocialPlatform defaultPlatform;
    private int timeout = 30000;
    private int connectTimeout = 5000;
    private int readTimeout = 30000;
    private String proxyHost;
    private int proxyPort;
    private String proxyUsername;
    private String proxyPassword;
    private Map<String, Object> platformConfigs = new HashMap<>();
    private Map<String, String> globalHeaders = new HashMap<>();

    public SocialConfig() {
    }

    // Getters and Setters
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

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public Map<String, Object> getPlatformConfigs() {
        return platformConfigs;
    }

    public void setPlatformConfigs(Map<String, Object> platformConfigs) {
        this.platformConfigs = platformConfigs;
    }

    public Map<String, String> getGlobalHeaders() {
        return globalHeaders;
    }

    public void setGlobalHeaders(Map<String, String> globalHeaders) {
        this.globalHeaders = globalHeaders;
    }

    public void addPlatformConfig(String platform, Object config) {
        this.platformConfigs.put(platform, config);
    }

    public void addGlobalHeader(String key, String value) {
        this.globalHeaders.put(key, value);
    }

    public boolean hasProxy() {
        return proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;
    }

    @Override
    public String toString() {
        return "SocialConfig{" +
                "enabled=" + enabled +
                ", defaultPlatform=" + defaultPlatform +
                ", timeout=" + timeout +
                ", hasProxy=" + hasProxy() +
                ", platformCount=" + platformConfigs.size() +
                '}';
    }
}
