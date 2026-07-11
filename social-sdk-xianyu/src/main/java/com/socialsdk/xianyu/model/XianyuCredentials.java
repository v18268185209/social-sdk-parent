package com.socialsdk.xianyu.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 闲鱼认证参数
 */
public class XianyuCredentials implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 认证前打开的页面
     */
    private String startUrl;

    /**
     * 登录等待超时（秒）
     */
    private long loginTimeoutSeconds = 180;

    /**
     * 原始 Cookie 字符串（name=value; name2=value2）
     */
    private String cookieHeader;

    /**
     * Cookie Map，优先级高于 cookieHeader
     */
    private Map<String, String> cookies = new HashMap<>();

    /**
     * 是否允许手动扫码登录
     */
    private boolean allowManualLogin = true;

    /**
     * 是否优先使用二维码页（浏览器手动扫码）
     */
    private boolean preferQrLogin = false;

    /**
     * 二维码登录会话ID（通过 XianyuProvider#createQrLoginSession 生成）
     */
    private String qrLoginSessionId;

    public String getStartUrl() {
        return startUrl;
    }

    public void setStartUrl(String startUrl) {
        this.startUrl = startUrl;
    }

    public long getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }

    public void setLoginTimeoutSeconds(long loginTimeoutSeconds) {
        this.loginTimeoutSeconds = loginTimeoutSeconds;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public void setCookieHeader(String cookieHeader) {
        this.cookieHeader = cookieHeader;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public void setCookies(Map<String, String> cookies) {
        this.cookies = cookies;
    }

    public boolean isAllowManualLogin() {
        return allowManualLogin;
    }

    public void setAllowManualLogin(boolean allowManualLogin) {
        this.allowManualLogin = allowManualLogin;
    }

    public boolean isPreferQrLogin() {
        return preferQrLogin;
    }

    public void setPreferQrLogin(boolean preferQrLogin) {
        this.preferQrLogin = preferQrLogin;
    }

    public String getQrLoginSessionId() {
        return qrLoginSessionId;
    }

    public void setQrLoginSessionId(String qrLoginSessionId) {
        this.qrLoginSessionId = qrLoginSessionId;
    }
}
