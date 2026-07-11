package com.socialsdk.xianyu.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 无头浏览器二维码登录会话
 */
public class XianyuHeadlessQrLoginSession implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_SCANNED = "scanned";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_NOT_FOUND = "not_found";

    private String sessionId;
    private String status = STATUS_WAITING;
    private String message;

    /**
     * QR 截图纯 base64 内容（不带 data:image 前缀）
     */
    private String qrCodeBase64;

    /**
     * QR dataUrl，前端可直接展示
     */
    private String qrCodeDataUrl;

    private String loginUrl;
    private String currentUrl;
    private String userAgent;
    private String cookieHeader;
    private String userId;
    private String tracknick;

    private Map<String, String> cookies = new LinkedHashMap<>();
    private Map<String, Map<String, String>> localStorageByOrigin = new LinkedHashMap<>();
    private Map<String, Map<String, String>> sessionStorageByOrigin = new LinkedHashMap<>();
    private Map<String, List<Map<String, Object>>> indexedDbByOrigin = new LinkedHashMap<>();
    private Map<String, List<String>> cacheStorageByOrigin = new LinkedHashMap<>();

    private Instant createdAt = Instant.now();
    private Instant completedAt;
    private int expiresInSeconds = 300;

    public boolean isExpired() {
        if (createdAt == null) {
            return true;
        }
        return Instant.now().isAfter(createdAt.plusSeconds(Math.max(1, expiresInSeconds)));
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getQrCodeBase64() {
        return qrCodeBase64;
    }

    public void setQrCodeBase64(String qrCodeBase64) {
        this.qrCodeBase64 = qrCodeBase64;
    }

    public String getQrCodeDataUrl() {
        return qrCodeDataUrl;
    }

    public void setQrCodeDataUrl(String qrCodeDataUrl) {
        this.qrCodeDataUrl = qrCodeDataUrl;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
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

    public String getCookieHeader() {
        return cookieHeader;
    }

    public void setCookieHeader(String cookieHeader) {
        this.cookieHeader = cookieHeader;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTracknick() {
        return tracknick;
    }

    public void setTracknick(String tracknick) {
        this.tracknick = tracknick;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public void setCookies(Map<String, String> cookies) {
        this.cookies = cookies;
    }

    public Map<String, Map<String, String>> getLocalStorageByOrigin() {
        return localStorageByOrigin;
    }

    public void setLocalStorageByOrigin(Map<String, Map<String, String>> localStorageByOrigin) {
        this.localStorageByOrigin = localStorageByOrigin;
    }

    public Map<String, Map<String, String>> getSessionStorageByOrigin() {
        return sessionStorageByOrigin;
    }

    public void setSessionStorageByOrigin(Map<String, Map<String, String>> sessionStorageByOrigin) {
        this.sessionStorageByOrigin = sessionStorageByOrigin;
    }

    public Map<String, List<Map<String, Object>>> getIndexedDbByOrigin() {
        return indexedDbByOrigin;
    }

    public void setIndexedDbByOrigin(Map<String, List<Map<String, Object>>> indexedDbByOrigin) {
        this.indexedDbByOrigin = indexedDbByOrigin;
    }

    public Map<String, List<String>> getCacheStorageByOrigin() {
        return cacheStorageByOrigin;
    }

    public void setCacheStorageByOrigin(Map<String, List<String>> cacheStorageByOrigin) {
        this.cacheStorageByOrigin = cacheStorageByOrigin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
