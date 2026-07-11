package com.socialsdk.core.model;

import com.socialsdk.core.constant.SocialPlatform;
import java.io.Serializable;
import java.time.Instant;

/**
 * 社交平台会话信息
 */
public class SocialSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private SocialPlatform platform;
    private String userId;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Instant expiresAt;
    private String scope;
    private String rawData;

    public SocialSession() {
    }

    public SocialSession(SocialPlatform platform, String userId, String accessToken) {
        this.platform = platform;
        this.userId = userId;
        this.accessToken = accessToken;
    }

    // Getters and Setters
    public SocialPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(SocialPlatform platform) {
        this.platform = platform;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return accessToken != null && !accessToken.isEmpty() && !isExpired();
    }

    @Override
    public String toString() {
        return "SocialSession{" +
                ", platform=" + platform +
                ", userId='" + userId + '\'' +
                ", accessToken='" + (accessToken != null ? "***" : null) + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
