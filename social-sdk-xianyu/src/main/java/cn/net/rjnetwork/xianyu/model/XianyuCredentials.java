package cn.net.rjnetwork.xianyu.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 闲鱼认证参数
 */
public class XianyuCredentials implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录等待超时（秒）
     */
    private long loginTimeoutSeconds = 300;

    /**
     * 原始 Cookie 字符串（name=value; name2=value2...）
     */
    private String cookieHeader;

    /**
     * Cookie Map，优先级高于 cookieHeader
     */
    private Map<String, String> cookies = new HashMap<>();

    /**
     * 二维码登录会话ID（通过 XianyuApiFacade#createQrLoginSession 生成）
     */
    private String qrLoginSessionId;

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

    public String getQrLoginSessionId() {
        return qrLoginSessionId;
    }

    public void setQrLoginSessionId(String qrLoginSessionId) {
        this.qrLoginSessionId = qrLoginSessionId;
    }
}
