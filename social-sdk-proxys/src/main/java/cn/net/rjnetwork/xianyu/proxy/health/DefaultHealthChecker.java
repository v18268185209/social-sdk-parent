package cn.net.rjnetwork.xianyu.proxy.health;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyHealthChecker;
import cn.net.rjnetwork.xianyu.proxy.util.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认健康检查器。通过 httpbin.org/ip 和 ipinfo.io 组合检测代理质量。
 *
 * <h3>检查项</h3>
 * <ul>
 *   <li><b>连通性</b>：TCP connect + HTTP GET 是否成功</li>
 *   <li><b>延迟</b>：HTTP 请求耗时</li>
 *   <li><b>出口 IP</b>：httpbin.org/ip 返回的真实出口 IP</li>
 *   <li><b>IP 属地</b>：ipinfo.io 返回的城市 / 运营商（判断 IP 是否在黑名单段）</li>
 * </ul>
 *
 * <h3>使用</h3>
 * <pre>{@code
 * DefaultHealthChecker checker = new DefaultHealthChecker();
 * HealthCheckResult result = checker.check(proxy);
 * if (result.isHealthy()) { ... }
 * }</pre>
 *
 * <p>每个代理供应商可实现自己的健康检查器（例如阿布云有专门的检测 API），覆盖默认实现。</p>
 */
public class DefaultHealthChecker implements ProxyHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultHealthChecker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 检测连通性的 URL（返回请求者 IP） */
    private static final String IP_CHECK_URL = "http://httpbin.org/ip";

    /** 检测 IP 属地的 URL */
    private static final String GEO_CHECK_URL = "http://ipinfo.io/%s/json";

    /** IP 正则，从 ipinfo.io 响应中提取 */
    private static final Pattern IP_PATTERN = Pattern.compile("\\\"ip\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    /** 最大允许延迟（ms），超过判定为不健康 */
    private final long maxLatencyMs;

    /** 是否启用 IP 属地检查（开启会多一次 HTTP 请求） */
    private final boolean geoCheckEnabled;

    /** URL 命中黑名单的 IP 段（正则表达式列表），命中即判定为数据中心 IP */
    private final java.util.List<String> blacklistPatterns = new java.util.ArrayList<>();

    public DefaultHealthChecker() {
        this(3000L, true);
    }

    public DefaultHealthChecker(long maxLatencyMs, boolean geoCheckEnabled) {
        this.maxLatencyMs = maxLatencyMs;
        this.geoCheckEnabled = geoCheckEnabled;
        // 默认黑名单：知名云厂商
        this.blacklistPatterns.add("^36\\.");
        this.blacklistPatterns.add("^47\\.");
        this.blacklistPatterns.add("^43\\.");
        this.blacklistPatterns.add("^52\\.");
        this.blacklistPatterns.add("^54\\.");
        this.blacklistPatterns.add("^140\\.");
        this.blacklistPatterns.add("^161\\.");
        this.blacklistPatterns.add("^192\\.30\\.");
        this.blacklistPatterns.add("^198\\.18\\.");
    }

    @Override
    public HealthCheckResult check(ProxyInfo proxy) {
        if (proxy == null) {
            return HealthCheckResult.unhealthy("proxy is null");
        }

        long startMs = System.currentTimeMillis();
        String detectedIp;
        try {
            // Step 1：连通性 + 出口 IP
            String resp = HttpUtils.get(IP_CHECK_URL, null, proxy);
            long latencyMs = System.currentTimeMillis() - startMs;
            detectedIp = detectExitIp(resp);
            if (detectedIp == null) {
                return HealthCheckResult.unhealthy("无法解析出口 IP");
            }

            // Step 2：延迟检查
            if (latencyMs > maxLatencyMs) {
                return HealthCheckResult.unhealthy("延迟过高: " + latencyMs + "ms");
            }

            // Step 3：黑名单 IP 段检查
            if (isBlacklisted(detectedIp)) {
                return HealthCheckResult.unhealthy("出口 IP " + detectedIp + " 在黑名单段，疑似数据中心 IP");
            }

            // Step 4：IP 属地检查
            String city = null;
            if (geoCheckEnabled) {
                city = fetchCity(detectedIp, proxy);
            }

            // 更新 proxy 信息
            proxy.setExitIp(detectedIp);
            proxy.setCity(city);

            return HealthCheckResult.healthy(latencyMs, detectedIp, city);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.warn("[HEALTH] check failed, proxy={}, elapsed={}ms, err={}", proxy.getHost(), elapsed, e.getMessage());
            return HealthCheckResult.unhealthy("检查异常: " + e.getMessage());
        }
    }

    @Override
    public long cooldownMs() {
        // 连续失败 3 次打入冷却，冷却 10 分钟
        return 10 * 60 * 1000L;
    }

    /**
     * 解析出口 IP。
     */
    private String detectExitIp(String response) {
        try {
            JsonNode node = MAPPER.readTree(response);
            if (node.has("origin")) {
                return node.get("origin").asText();
            }
        } catch (Exception ignored) {
        }
        // 回退到正则
        Matcher m = IP_PATTERN.matcher(response);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 从 ipinfo.io 获取 IP 所在城市。
     */
    private String fetchCity(String ip, ProxyInfo proxy) {
        try {
            String url = String.format(GEO_CHECK_URL, ip);
            String resp = HttpUtils.get(url, null, proxy);
            JsonNode node = MAPPER.readTree(resp);
            if (node.has("city")) {
                String city = node.get("city").asText();
                String region = node.has("region") ? node.get("region").asText() : "";
                String country = node.has("country") ? node.get("country").asText() : "";
                return city + (region.isBlank() ? "" : ", " + region) + (country.isBlank() ? "" : ", " + country);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 检查 IP 是否在黑名单段。
     */
    private boolean isBlacklisted(String ip) {
        for (String pattern : blacklistPatterns) {
            if (ip.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加黑名单 IP 正则。
     */
    public DefaultHealthChecker addBlacklistPattern(String regex) {
        blacklistPatterns.add(regex);
        return this;
    }

    /**
     * 一次性全量检查静态入口（用于 Java SPI / 单测）。
     */
    public static DefaultHealthChecker createDefault() {
        return new DefaultHealthChecker();
    }
}
