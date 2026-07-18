package cn.net.rjnetwork.xianyu.manager.audit.aspect;

import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.audit.model.AuditLog;
import cn.net.rjnetwork.xianyu.manager.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
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
        String ip = request != null ? request.getRemoteAddr() : "unknown";

        AuditLog log = new AuditLog();
        String methodName = joinPoint.getSignature().toShortString();
        log.setAction(methodName);
        log.setDetail(extractArgs(joinPoint));
        log.setIpAddress(ip);
        log.setActionTime(LocalDateTime.now());

        if (audit != null && !audit.value().isEmpty()) {
            log.setAction(audit.value());
        }

        try {
            Object result = joinPoint.proceed();
            log.setResourceType("SUCCESS");
            return result;
        } catch (Throwable t) {
            log.setResourceType("FAILURE");
            throw t;
        } finally {
            auditService.save(log);
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String extractArgs(ProceedingJoinPoint joinPoint) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : joinPoint.getArgs()) {
            if (arg != null && sb.length() < 500) {
                sb.append(arg.toString()).append("; ");
            }
        }
        return sb.toString();
    }
}
