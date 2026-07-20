package cn.net.rjnetwork.xianyu.chrome.exception;

import cn.net.rjnetwork.xianyu.proxy.core.ProviderType;
import lombok.Getter;

/**
 * Chrome 容器异常。
 */
@Getter
public class ChromeException extends RuntimeException {

    /** 错误码 */
    private final String code;

    /** 相关账号 ID（可能为 null） */
    private final Long accountId;

    /** 是否可重试 */
    private final boolean retryable;

    public ChromeException(String code, String message) {
        this(code, message, null, null, false);
    }

    public ChromeException(String code, String message, Throwable cause) {
        this(code, message, cause, null, false);
    }

    public ChromeException(String code, String message, Long accountId, boolean retryable) {
        this(code, message, null, accountId, retryable);
    }

    public ChromeException(String code, String message, Throwable cause, Long accountId, boolean retryable) {
        super(message, cause);
        this.code = code;
        this.accountId = accountId;
        this.retryable = retryable;
    }

    // ===== 工厂方法 =====

    public static ChromeException noAvailablePort() {
        return new ChromeException("NO_PORT", "无可用 CDP 端口，端口范围已耗尽");
    }

    public static ChromeException launchFailed(Long accountId, String reason) {
        return new ChromeException("LAUNCH_FAILED",
                "Chrome 容器启动失败: " + reason, accountId, true);
    }

    public static ChromeException launchFailed(Long accountId, Throwable cause) {
        return new ChromeException("LAUNCH_FAILED",
                "Chrome 容器启动失败: " + cause.getMessage(), cause, accountId, true);
    }

    public static ChromeException cdpConnectFailed(Long accountId, String endpoint) {
        return new ChromeException("CDP_CONNECT_FAILED",
                "无法连接到 CDP 端点: " + endpoint, accountId, true);
    }

    public static ChromeException sessionNotFound(Long accountId) {
        return new ChromeException("SESSION_NOT_FOUND",
                "未找到账号对应的 Chrome 容器, accountId=" + accountId, accountId, false);
    }

    public static ChromeException maxCrashRecoveryReached(Long accountId, int attempts) {
        return new ChromeException("MAX_RECOVERY_REACHED",
                "容器重启次数超过上限(" + attempts + ")", accountId, false);
    }

    public static ChromeException proxyBindingFailed(Long accountId, String reason) {
        return new ChromeException("PROXY_BINDING_FAILED",
                "代理绑定失败: " + reason, accountId, true);
    }

    @Override
    public String toString() {
        return String.format("ChromeException{code='%s', accountId=%s, retryable=%s, message='%s'}",
                code, accountId, retryable, getMessage());
    }
}
