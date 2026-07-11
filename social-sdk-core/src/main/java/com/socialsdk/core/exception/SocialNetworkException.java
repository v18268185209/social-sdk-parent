package com.socialsdk.core.exception;

/**
 * 网络异常
 */
public class SocialNetworkException extends SocialSdkException {

    private static final String NETWORK_ERROR = "NETWORK_ERROR";

    public SocialNetworkException(String message) {
        super(NETWORK_ERROR, message);
    }

    public SocialNetworkException(String message, Throwable cause) {
        super(NETWORK_ERROR, message, cause);
    }
}
