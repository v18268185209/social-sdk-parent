package cn.net.rjnetwork.xianyu.manager.audit.aspect;

import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import cn.net.rjnetwork.xianyu.manager.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
public class AuditLogAspect {

    private final AuditService auditService;

    public AuditLogAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        HttpServletRequest request = getRequest();
        String ip = request != null ? getClientIp(request) : "unknown";

        // 从 SecurityContext 获取当前操作人
        String operatorName = "anonymous";
        Long operatorId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User userDetails) {
            operatorName = userDetails.getUsername();
        }

        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);

        String methodName = joinPoint.getSignature().toShortString();
        if (audit != null && !audit.value().isEmpty()) {
            log.setAction(audit.value());
        } else {
            log.setAction(methodName);
        }
        log.setDetail(truncate(extractArgs(joinPoint), 1000));
        log.setIpAddress(ip);
        log.setActionTime(LocalDateTime.now());

        try {
            Object result = joinPoint.proceed();
            log.setResourceType("SUCCESS");
            return result;
        } catch (Throwable t) {
            log.setResourceType("FAILURE");
            log.setDetail(log.getDetail() + " [ERROR: " + truncate(t.getMessage(), 200) + "]");
            throw t;
        } finally {
            try {
                auditService.save(log);
            } catch (Exception e) {
                // 审计日志记录失败不应影响业务
            }
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String extractArgs(ProceedingJoinPoint joinPoint) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : joinPoint.getArgs()) {
            if (arg != null) {
                sb.append(arg.getClass().getSimpleName()).append(": ").append(arg).append("; ");
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
