package cn.net.rjnetwork.starter.platform.common.web;

import cn.net.rjnetwork.starter.platform.common.exception.StarterApiException;
import cn.net.rjnetwork.starter.platform.common.model.StarterApiResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Starter 平台能力统一异常处理。
 */
@RestControllerAdvice(basePackages = "cn.net.rjnetwork.starter.platform")
public class StarterGlobalExceptionHandler {

    @ExceptionHandler(StarterApiException.class)
    public StarterApiResponse<?> handleStarterApiException(StarterApiException ex) {
        if (ex.getData() != null) {
            return StarterApiResponse.fail(ex.getCode(), safeMessage(ex), ex.getData());
        }
        return StarterApiResponse.fail(ex.getCode(), safeMessage(ex));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public StarterApiResponse<?> handleIllegalArgument(IllegalArgumentException ex) {
        return StarterApiResponse.fail("BAD_REQUEST", safeMessage(ex));
    }

    @ExceptionHandler(Exception.class)
    public StarterApiResponse<?> handleUnhandledException(Exception ex) {
        return StarterApiResponse.fail("INTERNAL_ERROR", safeMessage(ex));
    }

    private String safeMessage(Throwable ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return "Unexpected error";
        }
        return ex.getMessage();
    }
}
