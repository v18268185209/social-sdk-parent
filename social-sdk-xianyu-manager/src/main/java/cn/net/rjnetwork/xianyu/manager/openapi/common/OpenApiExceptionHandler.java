package cn.net.rjnetwork.xianyu.manager.openapi.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 对外 OpenAPI 统一异常处理（仅作用于 openapi 包，避免吞掉内部 API 异常）。
 */
@RestControllerAdvice(basePackages = "cn.net.rjnetwork.xianyu.manager.openapi")
public class OpenApiExceptionHandler {

    @ExceptionHandler(OpenApiException.class)
    public ResponseEntity<OpenApiResponse<Void>> handleOpenApi(OpenApiException e) {
        OpenApiErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.httpStatus)
                .body(OpenApiResponse.fail(ec.code, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<OpenApiResponse<Void>> handleIllegal(IllegalArgumentException e) {
        return ResponseEntity.status(OpenApiErrorCode.INVALID_PARAM.httpStatus)
                .body(OpenApiResponse.fail(OpenApiErrorCode.INVALID_PARAM.code, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenApiResponse<Void>> handleOther(Exception e) {
        return ResponseEntity.status(OpenApiErrorCode.INTERNAL.httpStatus)
                .body(OpenApiResponse.fail(OpenApiErrorCode.INTERNAL.code, OpenApiErrorCode.INTERNAL.defaultMessage));
    }
}
