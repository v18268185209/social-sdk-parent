package com.socialsdk.core.exception;

/**
 * 认证异常
 */
public class SocialAuthenticationException extends SocialSdkException {

    private static final String AUTH_ERROR = "AUTH_ERROR";

    public SocialAuthenticationException(String message) {
        super(AUTH_ERROR, message);
    }

    public SocialAuthenticationException(String message, Throwable cause) {
        super(AUTH_ERROR, message, cause);
    }
}
