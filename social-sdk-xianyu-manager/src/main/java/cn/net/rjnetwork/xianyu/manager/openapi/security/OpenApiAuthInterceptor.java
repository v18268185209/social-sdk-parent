package cn.net.rjnetwork.xianyu.manager.openapi.security;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对外 OpenAPI 鉴权拦截器（仅作用于 /openapi/v1/**，oauth/token 与 docs 由注册处排除）。
 * 流程：Bearer 令牌 -> 解析应用 -> 写入 OpenApiContext -> 限流校验。
 * 鉴权/限流失败直接写 OpenApiResponse JSON 并返回 false（不走异常解析，避免与 SPA 兜底冲突）。
 */
@Component
public class OpenApiAuthInterceptor implements HandlerInterceptor {

    private final OpenAppService openAppService;
    private final ObjectMapper objectMapper;

    /** 限流桶：appKey -> [当前分钟epoch, 计数] */
    private final Map<String, long[]> buckets = new ConcurrentHashMap<>();

    public OpenApiAuthInterceptor(OpenAppService openAppService, ObjectMapper objectMapper) {
        this.openAppService = openAppService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(response, OpenApiErrorCode.UNAUTHORIZED, "缺少 Bearer 令牌");
            return false;
        }
        String token = auth.substring(7).trim();
        if (token.isEmpty()) {
            writeError(response, OpenApiErrorCode.UNAUTHORIZED, "令牌为空");
            return false;
        }

        OpenApp app;
        try {
            app = openAppService.resolveToken(token);
        } catch (OpenApiException e) {
            writeError(response, e.getErrorCode(), e.getMessage());
            return false;
        }

        OpenApiContext.setOpenApp(app);

        if (!allow(app.getAppKey(), app.getRateLimitPerMinute() != null ? app.getRateLimitPerMinute() : 60)) {
            writeError(response, OpenApiErrorCode.RATE_LIMIT, OpenApiErrorCode.RATE_LIMIT.defaultMessage);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OpenApiContext.clear();
    }

    private boolean allow(String appKey, int limit) {
        if (limit <= 0) {
            return true;
        }
        long nowMin = System.currentTimeMillis() / 60_000;
        long[] bucket = buckets.computeIfAbsent(appKey, k -> new long[]{nowMin, 0});
        synchronized (bucket) {
            if (bucket[0] != nowMin) {
                bucket[0] = nowMin;
                bucket[1] = 0;
            }
            if (bucket[1] >= limit) {
                return false;
            }
            bucket[1]++;
            return true;
        }
    }

    private void writeError(HttpServletResponse response, OpenApiErrorCode ec, String message) {
        try {
            response.setStatus(ec.httpStatus.value());
            response.setContentType("application/json;charset=UTF-8");
            OpenApiResponse<Void> body = OpenApiResponse.fail(ec.code, message);
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception ignored) {
            try {
                response.sendError(HttpStatus.UNAUTHORIZED.value());
            } catch (Exception ignore) {
                // ignore
            }
        }
    }
}
