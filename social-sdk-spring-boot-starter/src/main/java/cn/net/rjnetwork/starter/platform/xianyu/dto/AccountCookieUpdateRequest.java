package cn.net.rjnetwork.starter.platform.xianyu.dto;

import java.util.Map;

public class AccountCookieUpdateRequest {

    private String cookieHeader;
    private Map<String, String> cookies;
    private String remark;

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
