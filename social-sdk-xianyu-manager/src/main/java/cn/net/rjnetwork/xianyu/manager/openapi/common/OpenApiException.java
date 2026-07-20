package cn.net.rjnetwork.xianyu.manager.openapi.common;

/**
 * 对外 OpenAPI 业务异常。由控制器层抛出，被 OpenApiExceptionHandler 统一转换为 OpenApiResponse。
 * 拦截器层鉴权失败不走此异常（直接写响应），避免与 SPA 兜底冲突。
 */
public class OpenApiException extends RuntimeException {

    private final OpenApiErrorCode errorCode;

    public OpenApiException(OpenApiErrorCode errorCode) {
        super(errorCode.defaultMessage);
        this.errorCode = errorCode;
    }

    public OpenApiException(OpenApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OpenApiErrorCode getErrorCode() {
        return errorCode;
    }
}
