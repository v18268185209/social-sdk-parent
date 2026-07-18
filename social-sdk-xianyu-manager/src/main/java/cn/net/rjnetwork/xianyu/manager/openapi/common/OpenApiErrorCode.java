package cn.net.rjnetwork.xianyu.manager.openapi.common;

import org.springframework.http.HttpStatus;

/**
 * 对外 OpenAPI 错误码。code 以 OPEN_ 前缀，便于与内部错误区分。
 */
public enum OpenApiErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "OPEN_UNAUTHORIZED", "未提供访问令牌"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "OPEN_INVALID_TOKEN", "访问令牌无效或已过期"),
    APP_DISABLED(HttpStatus.FORBIDDEN, "OPEN_APP_DISABLED", "应用已被禁用"),
    APP_EXPIRED(HttpStatus.FORBIDDEN, "OPEN_APP_EXPIRED", "应用凭证已过期"),
    RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "OPEN_RATE_LIMIT", "请求过于频繁，请稍后重试"),
    ACCOUNT_FORBIDDEN(HttpStatus.FORBIDDEN, "OPEN_ACCOUNT_FORBIDDEN", "无权访问该账号"),
    INVALID_PARAM(HttpStatus.BAD_REQUEST, "OPEN_INVALID_PARAM", "请求参数错误"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "OPEN_NOT_FOUND", "资源不存在"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "OPEN_INTERNAL", "服务内部错误");

    public final HttpStatus httpStatus;
    public final String code;
    public final String defaultMessage;

    OpenApiErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
