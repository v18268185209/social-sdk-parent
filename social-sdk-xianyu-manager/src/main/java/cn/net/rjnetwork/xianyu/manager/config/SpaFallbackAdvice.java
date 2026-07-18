package cn.net.rjnetwork.xianyu.manager.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * SPA 前端路由兜底。
 * 配合 application.yml 的 spring.mvc.throw-exception-if-no-handler-found=true：
 * 浏览器直接访问/刷新前端路由（如 /dashboard）时服务端没有对应资源，会抛出
 * NoHandlerFoundException，这里统一转发回 index.html，交给 Vue Router 接管。
 * 真实存在的静态资源（.js/.css/.png 等）由 ResourceHttpRequestHandler 正常返回，不会走到这里；
 * 以 /api、/ws、/v3、/swagger-ui 开头的请求则透传 404（保持接口与文档语义）。
 */
@ControllerAdvice
public class SpaFallbackAdvice {

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api")
                || uri.startsWith("/ws")
                || uri.startsWith("/v3")
                || uri.startsWith("/swagger-ui")) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
