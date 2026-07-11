package com.socialsdk.xianyu.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 闲鱼平台配置
 */
public class XianyuConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 闲鱼站点首页
     */
    private String baseUrl = "https://www.goofish.com/";

    /**
     * 默认消息页面
     */
    private String messageUrl = "https://www.goofish.com/im";

    /**
     * 登录等待超时（秒）
     */
    private long loginTimeoutSeconds = 180;

    /**
     * 登录轮询间隔（毫秒）
     */
    private long pollIntervalMillis = 1500;

    /**
     * 登录态关键 Cookie 名称
     */
    private List<String> requiredAuthCookies = new ArrayList<>(
            Arrays.asList("_m_h5_tk", "_m_h5_tk_enc", "cookie2"));

    /**
     * 消息输入框候选 CSS 选择器
     */
    private List<String> messageInputSelectors = new ArrayList<>(
            Arrays.asList(
                    "textarea",
                    "div[contenteditable='true']",
                    "div[role='textbox']"));

    /**
     * 发送按钮候选 CSS 选择器
     */
    private List<String> sendButtonSelectors = new ArrayList<>(
            Arrays.asList(
                    "button[type='submit']",
                    "button.send-btn",
                    ".send-btn",
                    "button[class*='send']"));

    /**
     * 时间线查询是否优先使用 MTOP 接口
     */
    private boolean timelinePreferMtop = true;

    /**
     * MTOP appKey
     */
    private String mtopAppKey = "34839810";

    /**
     * MTOP 消息同步 API
     */
    private String mtopMessageSyncApi = "mtop.taobao.idlemessage.pc.message.sync";

    /**
     * MTOP API 版本
     */
    private String mtopApiVersion = "1.0";

    /**
     * MTOP jsv 参数
     */
    private String mtopJsv = "2.7.2";

    /**
     * MTOP 调用超时（毫秒）
     */
    private int mtopTimeoutMillis = 20000;

    /**
     * MTOP 默认起始位置
     */
    private int mtopStart = 0;

    /**
     * MTOP includeRequestMsg 参数
     */
    private boolean mtopIncludeRequestMsg = true;

    /**
     * MTOP 请求 type 参数
     */
    private int mtopRequestType = 1;

    /**
     * 实时消息是否启用
     */
    private boolean realtimeEnabled = true;

    /**
     * WebSocket 地址
     */
    private String websocketUrl = "wss://wss-goofish.dingtalk.com/";

    /**
     * IM 注册 app-key
     */
    private String realtimeAppKey = "444e9908a51d1cb236a27862abc769c9";

    /**
     * 获取实时 token 的 API 名称
     */
    private String mtopLoginTokenApi = "mtop.taobao.idlemessage.pc.login.token";

    /**
     * WebSocket 心跳间隔（秒）
     */
    private int heartbeatIntervalSeconds = 15;

    /**
     * WebSocket 连接超时（秒）
     */
    private int websocketConnectTimeoutSeconds = 20;

    /**
     * 心跳响应超时时间（秒）
     */
    private int heartbeatTimeoutSeconds = 45;

    /**
     * 是否启用实时连接自动重连
     */
    private boolean realtimeAutoReconnect = true;

    /**
     * 最大重连次数（<=0 表示无限重试）
     */
    private int realtimeMaxReconnectAttempts = 0;

    /**
     * 重连基础等待时间（毫秒）
     */
    private int realtimeReconnectBaseDelayMillis = 3000;

    /**
     * 重连最大等待时间（毫秒）
     */
    private int realtimeReconnectMaxDelayMillis = 30000;

    /**
     * 是否启用实时 token 定时刷新
     */
    private boolean realtimeTokenRefreshEnabled = true;

    /**
     * 实时 token 刷新周期（秒）
     */
    private int realtimeTokenRefreshIntervalSeconds = 7200;

    /**
     * 闲鱼图片上传接口
     */
    private String realtimeImageUploadUrl =
            "https://stream-upload.goofish.com/api/upload.api?floderId=0&appkey=xy_chat&_input_charset=utf-8";

    /**
     * 实时连接 UA
     */
    private String realtimeUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

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

    public List<String> getRequiredAuthCookies() {
        return requiredAuthCookies;
    }

    public void setRequiredAuthCookies(List<String> requiredAuthCookies) {
        this.requiredAuthCookies = requiredAuthCookies;
    }

    public List<String> getMessageInputSelectors() {
        return messageInputSelectors;
    }

    public void setMessageInputSelectors(List<String> messageInputSelectors) {
        this.messageInputSelectors = messageInputSelectors;
    }

    public List<String> getSendButtonSelectors() {
        return sendButtonSelectors;
    }

    public void setSendButtonSelectors(List<String> sendButtonSelectors) {
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
}
