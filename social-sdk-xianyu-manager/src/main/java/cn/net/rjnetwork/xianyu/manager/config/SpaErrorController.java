package cn.net.rjnetwork.xianyu.manager.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPA 前端路由兜底（健壮版）。
 *
 * <p>Spring Boot 默认 static-path-pattern=/**，因此浏览器直接访问/刷新前端路由
 * （如 /dashboard、/accounts/123）时，请求会先被 ResourceHttpRequestHandler 接管，
 * 因找不到对应静态文件而返回 404 —— 它并不会抛出 NoHandlerFoundException，
 * 所以 @ExceptionHandler(NoHandlerFoundException) 在这类场景下不会触发。</p>
 *
 * <p>本控制器接管 /error：所有 404（包括缺失的静态资源与客户端路由）都会转发到这里。
 * 对于“无扩展名、且非 /api、/ws、/v3、/swagger-ui 开头”的请求，判定为 SPA 路由，
 * 转发回 index.html 由 Vue Router 接管；其余（真实接口/文档 404）原样返回 JSON 404。</p>
 *
 * <p>真实存在的静态资源（.js/.css/.png/.jpg 等）由 ResourceHttpRequestHandler 直接返回，
 * 根本不会进入 /error，因此不受影响。</p>
 */
@Controller
public class SpaErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttr != null ? Integer.parseInt(statusAttr.toString()) : 404;

        boolean isSpaRoute = status == HttpStatus.NOT_FOUND.value()
                && uri != null
                && !uri.startsWith("/api")
                && !uri.startsWith("/openapi")
                && !uri.startsWith("/ws")
                && !uri.startsWith("/v3")
                && !uri.startsWith("/swagger-ui")
                && !uri.contains(".");

        if (isSpaRoute) {
            // 交给前端路由接管；静态资源（含 /assets、wechat.jpg）均带扩展名，不会走到这里
            return "forward:/index.html";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", HttpStatus.valueOf(status).getReasonPhrase());
        body.put("path", uri);
        return ResponseEntity.status(status).body(body);
    }
}
