package cn.net.rjnetwork.xianyu.manager.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyProperties;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.AbuyunProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.SmartproxyProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.kuaidaili.KuaidailiAuth;
import cn.net.rjnetwork.xianyu.proxy.provider.kuaidaili.KuaidailiPrivateProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.kuaidaili.KuaidailiTunnelProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.qg.QgShortLivedProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.qg.QgTunnelProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 SQLite proxy_config 表的 JSON 配置行构造 Provider 实例。
 *
 * <p>工厂封装了「哪种 ProviderType 走哪条构造函数」的分支逻辑，由 controller 在 reload 时调用。</p>
 */
public final class ProxyProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(ProxyProviderFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProxyProviderFactory() {}

    /**
     * 从配置 JSON 构建一个 Provider 实例。
     *
     * @param providerType  供应商类型
     * @param configJson    JSON 字符串
     * @return 构造好的 Provider，失败返回 null
     */
    public static ProxyProvider build(String providerType, String configJson) {
        try {
            JsonNode root = MAPPER.readTree(configJson);
            return switch (providerType) {
                case "abuyun" -> buildAbuyun(root);
                case "smartproxy" -> buildSmartproxy(root);
                case "qg_tunnel" -> buildQgTunnel(root);
                case "qg_short_lived" -> buildQgShortLived(root);
                case "kuaidaili_tunnel" -> buildKuaidailiTunnel(root);
                case "kuaidaili_private" -> buildKuaidailiPrivate(root);
                default -> {
                    log.warn("[PROXY-FACTORY] 未知 providerType={}", providerType);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("[PROXY-FACTORY] 构建 {} 失败: {}", providerType, e.getMessage());
            return null;
        }
    }

    /** 将整行 proxy_config 反序列化为 Map（用于 UI 读取时返回脱敏版本） */
    public static Map<String, Object> parseConfigMap(String configJson) throws Exception {
        return MAPPER.readValue(configJson, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    /** 序列化 Map → JSON（UI 保存时） */
    public static String toJson(Map<String, Object> configMap) throws Exception {
        return MAPPER.writeValueAsString(configMap);
    }

    private static ProxyProvider buildAbuyun(JsonNode root) {
        String username = root.path("username").asText();
        String password = root.path("password").asText();
        String host = root.path("host").asText("http-dyn.abuyun.com");
        int port = root.path("port").asInt(9020);
        return new AbuyunProvider(username, password, host, port);
    }

    private static ProxyProvider buildSmartproxy(JsonNode root) {
        String username = root.path("username").asText();
        String password = root.path("password").asText();
        String host = root.path("host").asText("gate.smartproxy.com");
        int port = root.path("port").asInt(7000);
        String city = root.path("city").asText("");
        return new SmartproxyProvider(username, password, host, port, city);
    }

    private static ProxyProvider buildQgTunnel(JsonNode root) {
        ProxyProperties.QgConfig cfg = new ProxyProperties.QgConfig();
        cfg.setApiKey(root.path("apiKey").asText());
        cfg.setAuthKey(root.path("authKey").asText());
        cfg.setAuthPwd(root.path("authPwd").asText());
        ProxyProperties.QgSubConfig tunnel = new ProxyProperties.QgSubConfig();
        tunnel.setHost(root.path("host").asText(""));
        tunnel.setPort(root.path("port").asInt(0));
        tunnel.setAreaCode(root.path("areaCode").asText(""));
        tunnel.setKeepAliveSec(root.path("keepAliveSec").asInt(60));
        tunnel.setProtocol(root.path("protocol").asText("http"));
        cfg.setTunnel(tunnel);
        return new QgTunnelProvider(cfg);
    }

    private static ProxyProvider buildQgShortLived(JsonNode root) {
        String apiKey = root.path("apiKey").asText();
        ProxyProperties.QgShortLivedSubConfig cfg = new ProxyProperties.QgShortLivedSubConfig();
        cfg.setPlan(root.path("plan").asText("extract_by_count"));
        cfg.setAreaCode(root.path("areaCode").asText(""));
        cfg.setIsp(root.path("isp").asInt(0));
        cfg.setDistinct(root.path("distinct").asBoolean(true));
        cfg.setNum(root.path("num").asInt(1));
        cfg.setKeepAliveSec(root.path("keepAliveSec").asInt(60));
        cfg.setMaxLatencyMs(root.path("maxLatencyMs").asLong(3000));
        return new QgShortLivedProvider(apiKey, cfg);
    }

    private static ProxyProvider buildKuaidailiTunnel(JsonNode root) {
        KuaidailiAuth auth = buildKuaidailiAuth(root);
        ProxyProperties.KuaidailiTunnelSubConfig cfg = new ProxyProperties.KuaidailiTunnelSubConfig();
        cfg.setNum(root.path("num").asInt(1));
        cfg.setFormat(root.path("format").asText("json"));
        return new KuaidailiTunnelProvider(auth, cfg);
    }

    private static ProxyProvider buildKuaidailiPrivate(JsonNode root) {
        KuaidailiAuth auth = buildKuaidailiAuth(root);
        ProxyProperties.KuaidailiPrivateSubConfig cfg = new ProxyProperties.KuaidailiPrivateSubConfig();
        cfg.setNum(root.path("num").asInt(1));
        cfg.setPt(root.path("pt").asInt(1));
        cfg.setDistinct(root.path("distinct").asBoolean(true));
        cfg.setFormat(root.path("format").asText("json"));
        cfg.setAreaCode(root.path("areaCode").asText(""));
        cfg.setIsp(root.path("isp").asInt(0));
        cfg.setKeepAliveSec(root.path("keepAliveSec").asInt(120));
        return new KuaidailiPrivateProvider(auth, cfg);
    }

    private static KuaidailiAuth buildKuaidailiAuth(JsonNode root) {
        String secretId = root.path("secretId").asText();
        String secretKey = root.path("secretKey").asText();
        String authType = root.path("authType").asText("token");
        return new KuaidailiAuth(secretId, secretKey, authType);
    }
}
