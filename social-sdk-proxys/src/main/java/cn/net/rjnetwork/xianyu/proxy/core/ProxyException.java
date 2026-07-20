package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;

/**
 * 代理池异常。所有代理供应商调用失败时统一抛出此异常，
 * 上层业务根据 {@link #isRetryable()} 决定是否重试或切换供应商。
 */
public class ProxyException extends RuntimeException {

    /** 错误码 */
    private final String errorCode;

    /** 触发异常的供应商 */
    private final ProviderType providerType;

    /** 是否可重试（true = 业务方可重试；false = 必须换供应商或降级） */
    private final boolean retryable;

    public ProxyException(String message) {
        this(message, null, null, true);
    }

    public ProxyException(String message, Throwable cause) {
        this(message, cause, null, true);
    }

    public ProxyException(String errorCode, String message) {
        this(message, null, null, true);
    }

    public ProxyException(String message, Throwable cause, ProviderType providerType, boolean retryable) {
        super(message, cause);
        this.errorCode = "PROXY_ERROR";
        this.providerType = providerType;
        this.retryable = retryable;
    }

    public ProxyException(String errorCode, String message, Throwable cause, ProviderType providerType, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.providerType = providerType;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** 预定义：余额不足（不可重试，要让业务方去充值） */
    public static ProxyException insufficientBalance(ProviderType type) {
        return new ProxyException("INSUFFICIENT_BALANCE",
                "代理供应商 [" + type.getDisplayName() + "] 余额不足，请充值",
                null, type, false);
    }

    /** 预定义：IP 被目标站点拉黑（不可重试） */
    public static ProxyException ipBanned(ProviderType type, String exitIp) {
        return new ProxyException("IP_BANNED",
                "代理 IP " + exitIp + " 已被目标站点拉黑",
                null, type, false);
    }

    /** 预定义：连接超时（可重试） */
    public static ProxyException timeout(ProviderType type, Throwable cause) {
        return new ProxyException("TIMEOUT",
                "连接代理供应商 [" + type.getDisplayName() + "] 超时",
                cause, type, true);
    }

    /** 预定义：无可用的代理（不可重试） */
    public static ProxyException noAvailableProxy() {
        return new ProxyException("NO_AVAILABLE_PROXY",
                "当前代理池无可用的代理，请检查配置或充值",
                null, null, false);
    }

    /** 预定义：账号-IP 绑定冲突（不可重试） */
    public static ProxyException bindingConflict(Long accountId, Long boundAccountId) {
        return new ProxyException("BINDING_CONFLICT",
                "账号 " + accountId + " 尝试绑定的代理已被账号 " + boundAccountId + " 占用",
                null, null, false);
    }
}
