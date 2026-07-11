package cn.net.rjnetwork.starter.platform.xianyu.model;

import java.time.Instant;

public class XianyuLoginSnapshotEntity {

    private Long id;
    private Long accountId;
    private String sessionId;
    private String loginMode;
    private String userId;
    private String cookieHeader;
    private String cookiesJson;
    private String localStorageJson;
    private String sessionStorageJson;
    private String indexedDbJson;
    private String cacheStorageJson;
    private String currentUrl;
    private String userAgent;
    private Instant capturedAt;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLoginMode() {
        return loginMode;
    }

    public void setLoginMode(String loginMode) {
        this.loginMode = loginMode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public void setCookieHeader(String cookieHeader) {
        this.cookieHeader = cookieHeader;
    }

    public String getCookiesJson() {
        return cookiesJson;
    }

    public void setCookiesJson(String cookiesJson) {
        this.cookiesJson = cookiesJson;
    }

    public String getLocalStorageJson() {
        return localStorageJson;
    }

    public void setLocalStorageJson(String localStorageJson) {
        this.localStorageJson = localStorageJson;
    }

    public String getSessionStorageJson() {
        return sessionStorageJson;
    }

    public void setSessionStorageJson(String sessionStorageJson) {
        this.sessionStorageJson = sessionStorageJson;
    }

    public String getIndexedDbJson() {
        return indexedDbJson;
    }

    public void setIndexedDbJson(String indexedDbJson) {
        this.indexedDbJson = indexedDbJson;
    }

    public String getCacheStorageJson() {
        return cacheStorageJson;
    }

    public void setCacheStorageJson(String cacheStorageJson) {
        this.cacheStorageJson = cacheStorageJson;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
