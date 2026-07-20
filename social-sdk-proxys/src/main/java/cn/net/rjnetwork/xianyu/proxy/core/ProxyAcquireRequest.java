package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyType;
import cn.net.rjnetwork.xianyu.proxy.config.SessionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理分配请求。业务方通过此对象声明需要什么类型的代理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyAcquireRequest {

    /** 需要代理的闲鱼账号 ID */
    private Long accountId;

    /** 期望城市（如 "上海"），null 表示不限 */
    private String preferredCity;

    /** 会话类型 */
    private SessionType sessionType;

    /** 期望代理类型 */
    private ProxyType proxyType;

    /** 粘性会话持续时间（分钟），用于计算 expireAt */
    private int stickyDurationMinutes;

    /** 是否需要已通过滑块验证的 IP（复用 IP） */
    private boolean requireCaptchaPassed;

    /** 业务方自定义过滤条件 */
    private java.util.function.Predicate<ProxyInfo> filter;

    public static ProxyAcquireRequest defaultRequest(Long accountId) {
        return ProxyAcquireRequest.builder()
                .accountId(accountId)
                .sessionType(SessionType.STICKY)
                .proxyType(ProxyType.RESIDENTIAL)
                .stickyDurationMinutes(30)
                .requireCaptchaPassed(false)
                .build();
    }
}
