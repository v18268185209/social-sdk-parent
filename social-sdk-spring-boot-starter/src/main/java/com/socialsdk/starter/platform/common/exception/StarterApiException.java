package com.socialsdk.starter.platform.common.exception;

/**
 * Starter 通用业务异常，携带统一错误码和可选 payload。
 */
public class StarterApiException extends RuntimeException {

    private final String code;
    private final Object data;

    public StarterApiException(String code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public StarterApiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = null;
    }

    public StarterApiException(String code, String message, Object data, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
