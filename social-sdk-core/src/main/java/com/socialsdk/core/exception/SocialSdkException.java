package com.socialsdk.core.exception;

/**
 * SDK基础异常类
 */
public class SocialSdkException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public SocialSdkException(String message) {
        super(message);
        this.errorCode = "SDK_ERROR";
        this.errorMessage = message;
    }

    public SocialSdkException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }

    public SocialSdkException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "SocialSdkException{" +
                "errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
