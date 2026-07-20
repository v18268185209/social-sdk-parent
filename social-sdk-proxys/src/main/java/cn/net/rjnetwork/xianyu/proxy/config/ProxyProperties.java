package cn.net.rjnetwork.xianyu.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 代理池全局配置。对应 application.yml 中 {@code proxy:} 前缀。
 *
 * <h3>application.yml 示例</h3>
 * <pre>{@code
 * proxy:
 *   enabled: true
 *   reuse-bound-ip: true
 *   max-binding-use-count: 100
 *   cool-down-recovery-minutes: 30
 *   lease-leak-threshold-minutes: 60
 *   health-check:
 *     enabled: true
 *     interval-minutes: 5
 *     max-latency-ms: 3000
 *     geo-check: true
 *   providers:
 *     abuyun:
 *       enabled: true
 *       username: your_user
 *       password: your_pass
 *       host: http-dyn.abuyun.com
 *       port: 9020
 *     kuaidaili:
 *       enabled: true
 *       orderId: your_order
 *       apiKey: your_key
 *       tunnelHost: tunnel.kuaidaili.com
 *       tunnelPort: 15818
 *     smartproxy:
 *       enabled: false
 *       username: your_user
 *       password: your_pass
 *       host: gate.smartproxy.com
 *       port: 7000
 *       city: Shanghai
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    /** 是否启用代理池 */
    private boolean enabled = true;

    /** 是否复用已绑定的 IP（推荐 true） */
    private boolean reuseBoundIp = true;

    /** 一条绑定的最大使用次数 */
    private int maxBindingUseCount = 100;

    /** 冷名单复原间隔（分钟） */
    private int coolDownRecoveryMinutes = 30;

    /** 泄露检测阈值（分钟） */
    private int leaseLeakThresholdMinutes = 60;

    /** 健康检查子配置 */
    private HealthCheck healthCheck = new HealthCheck();

    /** 供应商子配置（key = provider type name） */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * 健康检查配置
     */
    @Data
    public static class HealthCheck {
        private boolean enabled = true;
        private int intervalMinutes = 5;
        private long maxLatencyMs = 3000L;
        private boolean geoCheck = true;
    }

    /**
     * 单个供应商配置
     */
    @Data
    public static class ProviderConfig {
        private boolean enabled = true;
        private String username;
        private String password;
        private String host;
        private int port;
        private String city;
        // 快代理专用
        private String orderId;
        private String apiKey;
        private String tunnelHost;
        private int tunnelPort;
    }
}
