package com.socialsdk.starter.platform.common.model;

import java.time.Instant;

/**
 * Starter统一API响应结构。
 */
public class StarterApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private Instant timestamp = Instant.now();

    public static <T> StarterApiResponse<T> ok(T data) {
        StarterApiResponse<T> response = new StarterApiResponse<>();
        response.success = true;
        response.code = "OK";
        response.message = "OK";
        response.data = data;
        return response;
    }

    public static <T> StarterApiResponse<T> fail(String code, String message) {
        StarterApiResponse<T> response = new StarterApiResponse<>();
        response.success = false;
        response.code = code;
        response.message = message;
        return response;
    }

    public static <T> StarterApiResponse<T> fail(String code, String message, T data) {
        StarterApiResponse<T> response = fail(code, message);
        response.data = data;
        return response;
    }

    public static <T> StarterApiResponse<T> fail(String message) {
        return fail("ERROR", message);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
