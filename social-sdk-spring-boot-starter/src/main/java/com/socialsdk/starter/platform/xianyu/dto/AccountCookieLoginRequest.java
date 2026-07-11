package com.socialsdk.starter.platform.xianyu.dto;

import java.util.Map;

public class AccountCookieLoginRequest {

    private String accountName;
    private String cookieHeader;
    private Map<String, String> cookies;
    private Boolean allowManualLogin;
    private Long loginTimeoutSeconds;
    private String startUrl;
    private String remark;

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
