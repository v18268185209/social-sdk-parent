package cn.net.rjnetwork.demo.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class CookieLoginRequest {

    private String cookieHeader;
    private Map<String, String> cookies = new LinkedHashMap<>();
    private Boolean allowManualLogin;
    private Long loginTimeoutSeconds;
    private String startUrl;

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

    public Boolean getAllowManualLogin() {
        return allowManualLogin;
    }

    public void setAllowManualLogin(Boolean allowManualLogin) {
        this.allowManualLogin = allowManualLogin;
    }

    public Long getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }

    public void setLoginTimeoutSeconds(Long loginTimeoutSeconds) {
        this.loginTimeoutSeconds = loginTimeoutSeconds;
    }

    public String getStartUrl() {
        return startUrl;
    }

    public void setStartUrl(String startUrl) {
        this.startUrl = startUrl;
    }
}
