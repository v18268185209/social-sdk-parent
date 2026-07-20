package cn.net.rjnetwork.xianyu.manager.openapi.common;

import java.time.Instant;

/**
 * 对外 OpenAPI 统一响应信封。
 * 成功 code=OK；业务失败 code=OPEN_xxxx（见 OpenApiErrorCode）。
 */
public class OpenApiResponse<T> {

    private String code;

    private String message;

    private T data;

    private long timestamp = Instant.now().getEpochSecond();

    public OpenApiResponse() {}

    public static <T> OpenApiResponse<T> ok(T data) {
        OpenApiResponse<T> r = new OpenApiResponse<>();
        r.code = "OK";
        r.message = "Success";
        r.data = data;
        return r;
    }

    public static <T> OpenApiResponse<T> fail(String code, String message) {
        OpenApiResponse<T> r = new OpenApiResponse<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
