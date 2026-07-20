package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;

/**
 * 代理健康检查器。定期检查代理的可用性、延迟、IP 属地。
 * 不通过的代理直接打入冷名单，不分配给业务方。
 */
public interface ProxyHealthChecker {

    /**
     * 检查单个代理是否健康。
     *
     * @param proxy 要检查的代理
     * @return 健康检查结果
     */
    HealthCheckResult check(ProxyInfo proxy);

    /**
     * 冷却时间（毫秒）。被标记为失败的代理，在冷却期内不会被再次分配。
     */
    default long cooldownMs() {
        return 5 * 60 * 1000L; // 默认 5 分钟
    }

    /**
     * 健康检查结果。
     */
    class HealthCheckResult {
        private final boolean healthy;
        private final String message;
        private final long latencyMs;
        private final String detectedIp;
        private final String detectedCity;

        public HealthCheckResult(boolean healthy, String message, long latencyMs) {
            this(healthy, message, latencyMs, null, null);
        }

        public HealthCheckResult(boolean healthy, String message, long latencyMs, String detectedIp, String detectedCity) {
            this.healthy = healthy;
            this.message = message;
            this.latencyMs = latencyMs;
            this.detectedIp = detectedIp;
            this.detectedCity = detectedCity;
        }

        public boolean isHealthy() { return healthy; }
        public String getMessage() { return message; }
        public long getLatencyMs() { return latencyMs; }
        public String getDetectedIp() { return detectedIp; }
        public String getDetectedCity() { return detectedCity; }

        public static HealthCheckResult healthy(long latencyMs, String detectedIp, String detectedCity) {
            return new HealthCheckResult(true, "OK", latencyMs, detectedIp, detectedCity);
        }

        public static HealthCheckResult unhealthy(String message) {
            return new HealthCheckResult(false, message, -1, null, null);
        }

        public static HealthCheckResult timeout() {
            return new HealthCheckResult(false, "TIMEOUT", -1, null, null);
        }
    }
}
