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

    /**
     * 无供应商或全部失败时，是否自动退化为直连模式（返回 ProxyInfo.isDirect()=true 的 lease）。
     * 默认 true，保证业务方永远不需为"无代理配置"做特殊编码。
     */
    private boolean directModeAutoFallback = true;

    /** 健康检查子配置 */
    private HealthCheck healthCheck = new HealthCheck();

    /** 供应商子配置（key = provider type name，_abuyun_, _kuaidaili_, _smartproxy_, _qg_） */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /** 青果网络 (qg.net) 专属配置 */
    private QgConfig qg = new QgConfig();

    /** 快代理 (kuaidaili.com) 专属配置 */
    private KuaidailiConfig kuaidaili = new KuaidailiConfig();

    /**
     * 青果网络 (qg.net) 专属配置。top-level <b>apiKey</b> 被隧道代理和短效代理共用。
     *
     * <p>yaml 结构：</p>
     * <pre>{@code
     * proxy:
     *   providers:
     *     qg:
     *       enabled: true
     *       apiKey: xxxxxx               # 产品唯一标识（隧道/短效共用）
     *       authKey: your_user            # 账密鉴权用户名（白名单模式可为空）
     *       authPwd: your_pass            # 账密鉴权密码
     *       tunnel:
     *         enabled: true
     *         host: tun-szbhry.qg.net
     *         port: 25889
     *         areaCode: "110100"
     *         keepAliveSec: 60
     *         protocol: http
     *       shortLived:
     *         enabled: false
     *         plan: extract_by_count
     *         areaCode: ""
     *         isp: 0
     *         distinct: true
     *         num: 1
     *         keepAliveSec: 60
     *         maxLatencyMs: 3000
     * }</pre>
     *
     * <p>参考 QG 文档：https://www.qg.net/doc/1851.html (按量提取) / https://www.qg.net/doc/1927.html (开发者指南)</p>
     */
    @Data
    public static class QgConfig {
        /** 是否启用 QG 代理（含隧道和短效，各自再用内部开关控制） */
        private boolean enabled = false;

        /** 产品唯一标识 key（隧道/短效共用，QG 控制台获取） */
        private String apiKey;

        /** 账密鉴权 — 用户名（白名单模式可不填） */
        private String authKey;

        /** 账密鉴权 — 密码 */
        private String authPwd;

        /** 隧道代理子配置 */
        private QgSubConfig tunnel = new QgSubConfig();

        /** 短效代理子配置 */
        private QgShortLivedSubConfig shortLived = new QgShortLivedSubConfig();
    }

    /**
     * 隧道代理子配置。
     * Note: 账密鉴权字段（authKey/authPwd）定义在顶层 {@link QgConfig}，自动装配时由 Spring 注入，
     * 不在子配置里重复存储。
     */
    @Data
    public static class QgSubConfig {
        private boolean enabled = false;
        private String host;
        private int port;
        private String areaCode;
        private int keepAliveSec;
        private String protocol = "http";
    }

    /** 短效代理子配置（按量提取模式） */
    @Data
    public static class QgShortLivedSubConfig {
        private boolean enabled = false;
        private String plan = "extract_by_count";
        private String areaCode;
        private int isp;
        private boolean distinct = true;
        private int num = 1;
        private int keepAliveSec = 60;
        private long maxLatencyMs = 3000L;

        public String resolveApiDomain() { return "https://share.proxy.qg.net"; }

        public String buildExtractUrl(String apiKey) {
            StringBuilder url = new StringBuilder(resolveApiDomain()).append("/get?key=").append(apiKey);
            if (areaCode != null && !areaCode.isBlank()) url.append("&area=").append(areaCode);
            if (isp > 0) url.append("&isp=").append(isp);
            url.append("&distinct=").append(distinct).append("&num=").append(num);
            if (keepAliveSec > 0) url.append("&keep_alive=").append(keepAliveSec);
            return url.toString();
        }
    }

    /**
     * 快代理 (kuaidaili.com) 专属配置。top-level <b>secretId/secretKey</b> 被隧道代理和私密代理共用。
     *
     * <p>yaml 结构：</p>
     * <pre>{@code
     * proxy:
     *   providers:
     *     kuaidaili:
     *       enabled: true
     *       secretId: your_secret_id       # 订单 SecretId
     *       secretKey: your_secret_key     # 订单 SecretKey（hmacsha1 模式必填）
     *       authType: token                # token 或 hmacsha1
     *       tunnel:
     *         enabled: true
     *         num: 1
     *         format: json
     *       private:
     *         enabled: false
     *         num: 1
     *         pt: 1
     *         distinct: true
     *         format: json
     *         keepAliveSec: 120
     * }</pre>
     *
     * <p>参考文档：https://help.kuaidaili.com/api/auth/ / https://help.kuaidaili.com/api/gettps/ / https://help.kuaidaili.com/api/getdps/</p>
     */
    @Data
    public static class KuaidailiConfig {
        /** 是否启用快代理（仅作标记，bean 注册看内部子开关） */
        private boolean enabled = false;

        /** 订单 SecretId（隧道/私密共用，快代理控制台获取） */
        private String secretId;

        /** 订单 SecretKey（hmacsha1 模式必填，token 模式可选） */
        private String secretKey;

        /** 鉴权方式：token（默认）或 hmacsha1 */
        private String authType = "token";

        /** 隧道代理子配置 */
        private KuaidailiTunnelSubConfig tunnel = new KuaidailiTunnelSubConfig();

        /** 私密代理子配置 */
        private KuaidailiPrivateSubConfig private_ = new KuaidailiPrivateSubConfig();
    }

    /**
     * 隧道代理子配置。
     */
    @Data
    public static class KuaidailiTunnelSubConfig {
        private boolean enabled = false;
        private int num = 1;
        private String format = "json";
    }

    /**
     * 私密代理子配置。
     */
    @Data
    public static class KuaidailiPrivateSubConfig {
        private boolean enabled = false;
        private int num = 1;
        /** IP 协议：1=http 2=socks5 */
        private int pt = 1;
        /** 去重提取 */
        private boolean distinct = true;
        private String format = "json";
        /** 地区编码 */
        private String areaCode;
        /** 运营商筛选：0=不筛选 1=电信 2=移动 3=联通 */
        private int isp;
        /** 可用时长（秒） */
        private int keepAliveSec = 120;
    }

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
