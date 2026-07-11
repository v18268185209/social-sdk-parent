package cn.net.rjnetwork.core.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * 内容发布结果
 */
public class PostResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String postId;
    private String platformPostId;
    private String shareUrl;
    private Instant publishedAt;
    private String errorCode;
    private String errorMessage;
    private String rawResponse;

    public PostResult() {
    }

    public static PostResult success(String postId) {
        PostResult result = new PostResult();
        result.setSuccess(true);
        result.setPostId(postId);
        return result;
    }

    public static PostResult fail(String errorCode, String errorMessage) {
        PostResult result = new PostResult();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getPlatformPostId() {
        return platformPostId;
    }

    public void setPlatformPostId(String platformPostId) {
        this.platformPostId = platformPostId;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    @Override
    public String toString() {
        return "PostResult{" +
                "success=" + success +
                ", postId='" + postId + '\'' +
                ", platformPostId='" + platformPostId + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
