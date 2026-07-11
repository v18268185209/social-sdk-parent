package com.socialsdk.xianyu.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * 闲鱼二维码登录会话
 */
public class XianyuQrLoginSession implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_SCANNED = "scanned";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_VERIFICATION_REQUIRED = "verification_required";
    public static final String STATUS_NOT_FOUND = "not_found";
    public static final String STATUS_ERROR = "error";

    private String sessionId;
    private String status = STATUS_WAITING;
    private String qrContent;
    private String qrCodeDataUrl;
    private String verificationUrl;
    private String cookieHeader;
    private String unb;
    private Instant createdAt = Instant.now();
    private int expiresInSeconds = 300;
    private String message;

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

    public String getQrContent() {
        return qrContent;
    }

    public void setQrContent(String qrContent) {
        this.qrContent = qrContent;
    }

    public String getQrCodeDataUrl() {
        return qrCodeDataUrl;
    }

    public void setQrCodeDataUrl(String qrCodeDataUrl) {
        this.qrCodeDataUrl = qrCodeDataUrl;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }

    public void setVerificationUrl(String verificationUrl) {
        this.verificationUrl = verificationUrl;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public void setCookieHeader(String cookieHeader) {
        this.cookieHeader = cookieHeader;
    }

    public String getUnb() {
        return unb;
    }

    public void setUnb(String unb) {
        this.unb = unb;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
