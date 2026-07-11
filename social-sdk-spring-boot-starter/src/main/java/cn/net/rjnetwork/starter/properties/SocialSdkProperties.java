package cn.net.rjnetwork.starter.properties;

import cn.net.rjnetwork.chrome.config.ChromeConfig;
import cn.net.rjnetwork.core.constant.SocialPlatform;
import cn.net.rjnetwork.xianyu.config.XianyuConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Social SDK Spring Boot配置属性类
 * 支持 application.yml 中的 social-sdk 配置
 */
@Component
@ConfigurationProperties(prefix = "social-sdk")
public class SocialSdkProperties {

    /**
     * 是否启用SDK
     */
    private boolean enabled = true;

    /**
     * 默认平台
     */
    private SocialPlatform defaultPlatform;

    /**
     * 超时时间（毫秒）
     */
    private int timeout = 30000;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 30000;

    /**
     * 代理配置
     */
    private Proxy proxy = new Proxy();

    /**
     * Chrome配置
     */
    private ChromeProperties chrome = new ChromeProperties();

    /**
     * 其他平台配置
     */
    private Map<String, Object> platforms = new HashMap<>();

    /**
     * 闲鱼配置
     */
    private XianyuProperties xianyu = new XianyuProperties();

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

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public ChromeProperties getChrome() {
        return chrome;
    }

    public void setChrome(ChromeProperties chrome) {
        this.chrome = chrome;
    }

    public Map<String, Object> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(Map<String, Object> platforms) {
        this.platforms = platforms;
    }

    public XianyuProperties getXianyu() {
        return xianyu;
    }

    public void setXianyu(XianyuProperties xianyu) {
        this.xianyu = xianyu;
    }

    /**
     * 代理配置
     */
    public static class Proxy {
        private String host;
        private int port;
        private String username;
        private String password;
        private boolean enabled;

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

    /**
     * Chrome浏览器配置
     */
    public static class ChromeProperties {
        private boolean enabled = false;
        private String driverPath;
        private String executablePath;
        private String windowSize = "1920,1080";
        private boolean headless = false;
        private String userDataDir;
        private String downloadDir;
        private long pageLoadTimeout = 30000;
        private long implicitWaitTimeout = 5000;
        private long scriptTimeout = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getExecutablePath() {
            return executablePath;
        }

        public void setExecutablePath(String executablePath) {
            this.executablePath = executablePath;
        }

        public String getDriverPath() {
            return driverPath;
        }

        public void setDriverPath(String driverPath) {
            this.driverPath = driverPath;
        }

        public String getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(String windowSize) {
            this.windowSize = windowSize;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public String getUserDataDir() {
            return userDataDir;
        }

        public void setUserDataDir(String userDataDir) {
            this.userDataDir = userDataDir;
        }

        public String getDownloadDir() {
            return downloadDir;
        }

        public void setDownloadDir(String downloadDir) {
            this.downloadDir = downloadDir;
        }

        public long getPageLoadTimeout() {
            return pageLoadTimeout;
        }

        public void setPageLoadTimeout(long pageLoadTimeout) {
            this.pageLoadTimeout = pageLoadTimeout;
        }

        public long getImplicitWaitTimeout() {
            return implicitWaitTimeout;
        }

        public void setImplicitWaitTimeout(long implicitWaitTimeout) {
            this.implicitWaitTimeout = implicitWaitTimeout;
        }

        public long getScriptTimeout() {
            return scriptTimeout;
        }

        public void setScriptTimeout(long scriptTimeout) {
            this.scriptTimeout = scriptTimeout;
        }

        /**
         * 转换为ChromeConfig
         */
        public ChromeConfig toChromeConfig() {
            ChromeConfig config = new ChromeConfig();
            config.setExecutablePath(executablePath);
            config.setWindowSize(windowSize);
            config.setHeadless(headless);
            config.setUserDataDir(userDataDir);
            config.setDownloadDir(downloadDir);
            config.setPageLoadTimeout(pageLoadTimeout);
            config.setImplicitWaitTimeout(implicitWaitTimeout);
            config.setScriptTimeout(scriptTimeout);
            return config;
        }
    }

    /**
     * 闲鱼场景配置
     */
    public static class XianyuProperties {
        private boolean enabled = false;
        private String baseUrl = "https://www.goofish.com/";
        private String messageUrl = "https://www.goofish.com/im";
        private long loginTimeoutSeconds = 180;
        private long pollIntervalMillis = 1500;
        private java.util.List<String> requiredAuthCookies = new java.util.ArrayList<>(
                java.util.Arrays.asList("_m_h5_tk", "_m_h5_tk_enc", "cookie2"));
        private java.util.List<String> messageInputSelectors = new java.util.ArrayList<>(
                java.util.Arrays.asList("textarea", "div[contenteditable='true']", "div[role='textbox']"));
        private java.util.List<String> sendButtonSelectors = new java.util.ArrayList<>(
                java.util.Arrays.asList("button[type='submit']", "button.send-btn", ".send-btn", "button[class*='send']"));
        private boolean timelinePreferMtop = true;
        private String mtopAppKey = "34839810";
        private String mtopMessageSyncApi = "mtop.taobao.idlemessage.pc.message.sync";
        private String mtopApiVersion = "1.0";
        private String mtopJsv = "2.7.2";
        private int mtopTimeoutMillis = 20000;
        private int mtopStart = 0;
        private boolean mtopIncludeRequestMsg = true;
        private int mtopRequestType = 1;
        private boolean realtimeEnabled = true;
        private String websocketUrl = "wss://wss-goofish.dingtalk.com/";
        private String realtimeAppKey = "444e9908a51d1cb236a27862abc769c9";
        private String mtopLoginTokenApi = "mtop.taobao.idlemessage.pc.login.token";
        private int heartbeatIntervalSeconds = 15;
        private int websocketConnectTimeoutSeconds = 20;
        private int heartbeatTimeoutSeconds = 45;
        private boolean realtimeAutoReconnect = true;
        private int realtimeMaxReconnectAttempts = 0;
        private int realtimeReconnectBaseDelayMillis = 3000;
        private int realtimeReconnectMaxDelayMillis = 30000;
        private boolean realtimeTokenRefreshEnabled = true;
        private int realtimeTokenRefreshIntervalSeconds = 7200;
        private String realtimeImageUploadUrl =
                "https://stream-upload.goofish.com/api/upload.api?floderId=0&appkey=xy_chat&_input_charset=utf-8";
        private String realtimeUserAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getMessageUrl() {
            return messageUrl;
        }

        public void setMessageUrl(String messageUrl) {
            this.messageUrl = messageUrl;
        }

        public long getLoginTimeoutSeconds() {
            return loginTimeoutSeconds;
        }

        public void setLoginTimeoutSeconds(long loginTimeoutSeconds) {
            this.loginTimeoutSeconds = loginTimeoutSeconds;
        }

        public long getPollIntervalMillis() {
            return pollIntervalMillis;
        }

        public void setPollIntervalMillis(long pollIntervalMillis) {
            this.pollIntervalMillis = pollIntervalMillis;
        }

        public java.util.List<String> getRequiredAuthCookies() {
            return requiredAuthCookies;
        }

        public void setRequiredAuthCookies(java.util.List<String> requiredAuthCookies) {
            this.requiredAuthCookies = requiredAuthCookies;
        }

        public java.util.List<String> getMessageInputSelectors() {
            return messageInputSelectors;
        }

        public void setMessageInputSelectors(java.util.List<String> messageInputSelectors) {
            this.messageInputSelectors = messageInputSelectors;
        }

        public java.util.List<String> getSendButtonSelectors() {
            return sendButtonSelectors;
        }

        public void setSendButtonSelectors(java.util.List<String> sendButtonSelectors) {
            this.sendButtonSelectors = sendButtonSelectors;
        }

        public boolean isTimelinePreferMtop() {
            return timelinePreferMtop;
        }

        public void setTimelinePreferMtop(boolean timelinePreferMtop) {
            this.timelinePreferMtop = timelinePreferMtop;
        }

        public String getMtopAppKey() {
            return mtopAppKey;
        }

        public void setMtopAppKey(String mtopAppKey) {
            this.mtopAppKey = mtopAppKey;
        }

        public String getMtopMessageSyncApi() {
            return mtopMessageSyncApi;
        }

        public void setMtopMessageSyncApi(String mtopMessageSyncApi) {
            this.mtopMessageSyncApi = mtopMessageSyncApi;
        }

        public String getMtopApiVersion() {
            return mtopApiVersion;
        }

        public void setMtopApiVersion(String mtopApiVersion) {
            this.mtopApiVersion = mtopApiVersion;
        }

        public String getMtopJsv() {
            return mtopJsv;
        }

        public void setMtopJsv(String mtopJsv) {
            this.mtopJsv = mtopJsv;
        }

        public int getMtopTimeoutMillis() {
            return mtopTimeoutMillis;
        }

        public void setMtopTimeoutMillis(int mtopTimeoutMillis) {
            this.mtopTimeoutMillis = mtopTimeoutMillis;
        }

        public int getMtopStart() {
            return mtopStart;
        }

        public void setMtopStart(int mtopStart) {
            this.mtopStart = mtopStart;
        }

        public boolean isMtopIncludeRequestMsg() {
            return mtopIncludeRequestMsg;
        }

        public void setMtopIncludeRequestMsg(boolean mtopIncludeRequestMsg) {
            this.mtopIncludeRequestMsg = mtopIncludeRequestMsg;
        }

        public int getMtopRequestType() {
            return mtopRequestType;
        }

        public void setMtopRequestType(int mtopRequestType) {
            this.mtopRequestType = mtopRequestType;
        }

        public boolean isRealtimeEnabled() {
            return realtimeEnabled;
        }

        public void setRealtimeEnabled(boolean realtimeEnabled) {
            this.realtimeEnabled = realtimeEnabled;
        }

        public String getWebsocketUrl() {
            return websocketUrl;
        }

        public void setWebsocketUrl(String websocketUrl) {
            this.websocketUrl = websocketUrl;
        }

        public String getRealtimeAppKey() {
            return realtimeAppKey;
        }

        public void setRealtimeAppKey(String realtimeAppKey) {
            this.realtimeAppKey = realtimeAppKey;
        }

        public String getMtopLoginTokenApi() {
            return mtopLoginTokenApi;
        }

        public void setMtopLoginTokenApi(String mtopLoginTokenApi) {
            this.mtopLoginTokenApi = mtopLoginTokenApi;
        }

        public int getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }

        public int getWebsocketConnectTimeoutSeconds() {
            return websocketConnectTimeoutSeconds;
        }

        public void setWebsocketConnectTimeoutSeconds(int websocketConnectTimeoutSeconds) {
            this.websocketConnectTimeoutSeconds = websocketConnectTimeoutSeconds;
        }

        public int getHeartbeatTimeoutSeconds() {
            return heartbeatTimeoutSeconds;
        }

        public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
            this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        }

        public boolean isRealtimeAutoReconnect() {
            return realtimeAutoReconnect;
        }

        public void setRealtimeAutoReconnect(boolean realtimeAutoReconnect) {
            this.realtimeAutoReconnect = realtimeAutoReconnect;
        }

        public int getRealtimeMaxReconnectAttempts() {
            return realtimeMaxReconnectAttempts;
        }

        public void setRealtimeMaxReconnectAttempts(int realtimeMaxReconnectAttempts) {
            this.realtimeMaxReconnectAttempts = realtimeMaxReconnectAttempts;
        }

        public int getRealtimeReconnectBaseDelayMillis() {
            return realtimeReconnectBaseDelayMillis;
        }

        public void setRealtimeReconnectBaseDelayMillis(int realtimeReconnectBaseDelayMillis) {
            this.realtimeReconnectBaseDelayMillis = realtimeReconnectBaseDelayMillis;
        }

        public int getRealtimeReconnectMaxDelayMillis() {
            return realtimeReconnectMaxDelayMillis;
        }

        public void setRealtimeReconnectMaxDelayMillis(int realtimeReconnectMaxDelayMillis) {
            this.realtimeReconnectMaxDelayMillis = realtimeReconnectMaxDelayMillis;
        }

        public boolean isRealtimeTokenRefreshEnabled() {
            return realtimeTokenRefreshEnabled;
        }

        public void setRealtimeTokenRefreshEnabled(boolean realtimeTokenRefreshEnabled) {
            this.realtimeTokenRefreshEnabled = realtimeTokenRefreshEnabled;
        }

        public int getRealtimeTokenRefreshIntervalSeconds() {
            return realtimeTokenRefreshIntervalSeconds;
        }

        public void setRealtimeTokenRefreshIntervalSeconds(int realtimeTokenRefreshIntervalSeconds) {
            this.realtimeTokenRefreshIntervalSeconds = realtimeTokenRefreshIntervalSeconds;
        }

        public String getRealtimeImageUploadUrl() {
            return realtimeImageUploadUrl;
        }

        public void setRealtimeImageUploadUrl(String realtimeImageUploadUrl) {
            this.realtimeImageUploadUrl = realtimeImageUploadUrl;
        }

        public String getRealtimeUserAgent() {
            return realtimeUserAgent;
        }

        public void setRealtimeUserAgent(String realtimeUserAgent) {
            this.realtimeUserAgent = realtimeUserAgent;
        }

        public XianyuConfig toXianyuConfig() {
            XianyuConfig config = new XianyuConfig();
            config.setBaseUrl(baseUrl);
            config.setMessageUrl(messageUrl);
            config.setLoginTimeoutSeconds(loginTimeoutSeconds);
            config.setPollIntervalMillis(pollIntervalMillis);
            config.setRequiredAuthCookies(requiredAuthCookies);
            config.setMessageInputSelectors(messageInputSelectors);
            config.setSendButtonSelectors(sendButtonSelectors);
            config.setTimelinePreferMtop(timelinePreferMtop);
            config.setMtopAppKey(mtopAppKey);
            config.setMtopMessageSyncApi(mtopMessageSyncApi);
            config.setMtopApiVersion(mtopApiVersion);
            config.setMtopJsv(mtopJsv);
            config.setMtopTimeoutMillis(mtopTimeoutMillis);
            config.setMtopStart(mtopStart);
            config.setMtopIncludeRequestMsg(mtopIncludeRequestMsg);
            config.setMtopRequestType(mtopRequestType);
            config.setRealtimeEnabled(realtimeEnabled);
            config.setWebsocketUrl(websocketUrl);
            config.setRealtimeAppKey(realtimeAppKey);
            config.setMtopLoginTokenApi(mtopLoginTokenApi);
            config.setHeartbeatIntervalSeconds(heartbeatIntervalSeconds);
            config.setWebsocketConnectTimeoutSeconds(websocketConnectTimeoutSeconds);
            config.setHeartbeatTimeoutSeconds(heartbeatTimeoutSeconds);
            config.setRealtimeAutoReconnect(realtimeAutoReconnect);
            config.setRealtimeMaxReconnectAttempts(realtimeMaxReconnectAttempts);
            config.setRealtimeReconnectBaseDelayMillis(realtimeReconnectBaseDelayMillis);
            config.setRealtimeReconnectMaxDelayMillis(realtimeReconnectMaxDelayMillis);
            config.setRealtimeTokenRefreshEnabled(realtimeTokenRefreshEnabled);
            config.setRealtimeTokenRefreshIntervalSeconds(realtimeTokenRefreshIntervalSeconds);
            config.setRealtimeImageUploadUrl(realtimeImageUploadUrl);
            config.setRealtimeUserAgent(realtimeUserAgent);
            return config;
        }
    }
}
