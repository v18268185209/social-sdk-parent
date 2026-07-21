package cn.net.rjnetwork.xianyu.manager.proxy.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.proxy.core.ProxyProviderFactory;
import cn.net.rjnetwork.xianyu.manager.proxy.model.ProxyConfig;
import cn.net.rjnetwork.xianyu.manager.proxy.service.ProxyConfigService;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyProperties;
import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import cn.net.rjnetwork.xianyu.proxy.core.DefaultProxyPoolManager;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProxyConfigService configService;
    private final DefaultProxyPoolManager poolManager;
    private final ProxyProperties properties;

    @Autowired
    public ProxyController(ProxyConfigService configService,
                           @Autowired(required = false) DefaultProxyPoolManager poolManager,
                           ProxyProperties properties) {
        this.configService = configService;
        this.poolManager = poolManager;
        this.properties = properties;
    }

    @PostConstruct
    public void seedIfEmpty() {
        try {
            List<ProxyConfig> all = configService.listAll();
            if (!all.isEmpty()) return;
            ObjectNode global = MAPPER.createObjectNode();
            global.put("reuseBoundIp", properties.isReuseBoundIp());
            global.put("maxBindingUseCount", properties.getMaxBindingUseCount());
            global.put("directModeAutoFallback", properties.isDirectModeAutoFallback());
            ProxyConfig gc = new ProxyConfig();
            gc.setProviderType("global");
            gc.setConfigJson(global.toString());
            gc.setEnabled(1);
            gc.setSortOrder(0);
            configService.save(gc);

            properties.getProviders().forEach((name, cfg) -> {
                if (!cfg.isEnabled()) return;
                try {
                    ObjectNode node = MAPPER.createObjectNode();
                    node.put("username", cfg.getUsername() != null ? cfg.getUsername() : "");
                    node.put("password", cfg.getPassword() != null ? cfg.getPassword() : "");
                    node.put("host", cfg.getHost() != null ? cfg.getHost() : "");
                    node.put("port", cfg.getPort());
                    node.put("city", cfg.getCity() != null ? cfg.getCity() : "");
                    ProxyConfig pc = new ProxyConfig();
                    pc.setProviderType(name);
                    pc.setConfigJson(node.toString());
                    pc.setEnabled(1);
                    pc.setSortOrder(10);
                    configService.save(pc);
                } catch (Exception e) {
                    log.warn("[PROXY] seed provider {} 失败: {}", name, e.getMessage());
                }
            });
            log.info("[PROXY] proxy_config 表已用 YAML 初始化 seed");
        } catch (Exception e) {
            log.warn("[PROXY] seedIfEmpty 跳过: {}", e.getMessage());
        }
    }

    @GetMapping("/config")
    public ApiResponse<List<Map<String, Object>>> listConfig() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProxyConfig cfg : configService.listAll()) {
            try {
                Map<String, Object> configMap = ProxyProviderFactory.parseConfigMap(cfg.getConfigJson());
                configMap.remove("password");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", cfg.getId());
                row.put("providerType", cfg.getProviderType());
                row.put("enabled", cfg.getEnabled() != null && cfg.getEnabled() == 1);
                row.put("sortOrder", cfg.getSortOrder());
                row.put("config", configMap);
                result.add(row);
            } catch (Exception e) {
                log.warn("[PROXY] 解析 {} 配置失败: {}", cfg.getProviderType(), e.getMessage());
            }
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/config")
    public ApiResponse<ProxyConfig> saveConfig(@RequestBody ObjectNode req) {
        String providerType = req.path("providerType").asText();
        if (providerType.isBlank()) return ApiResponse.fail("providerType 必填");
        JsonNode cfgNode = req.path("config");
        if (cfgNode.isMissingNode()) return ApiResponse.fail("config JSON 必填");
        boolean enabled = req.path("enabled").asBoolean(true);
        int sortOrder = req.path("sortOrder").asInt(0);

        try {
            ProxyConfig cfg = configService.findByType(providerType);
            if (cfg == null) {
                cfg = new ProxyConfig();
                cfg.setProviderType(providerType);
            }
            cfg.setConfigJson(cfgNode.toString());
            cfg.setEnabled(enabled ? 1 : 0);
            cfg.setSortOrder(sortOrder);
            configService.save(cfg);
            return ApiResponse.ok(cfg);
        } catch (Exception e) {
            return ApiResponse.fail("保存失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/config/{providerType}")
    public ApiResponse<Void> deleteConfig(@PathVariable String providerType) {
        int n = configService.delete(providerType);
        return n > 0 ? ApiResponse.ok(null) : ApiResponse.fail("记录不存在: " + providerType);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (poolManager == null) {
            result.put("available", false);
            return ApiResponse.ok(result);
        }
        DefaultProxyPoolManager.PoolMetrics m = poolManager.getMetrics();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("registeredProviders", m.registeredProviders);
        metrics.put("activeLeases", m.activeLeaseCount);
        metrics.put("bindings", m.bindingCount);
        metrics.put("coolingDown", m.coolingDownCount);
        metrics.put("totalAcquire", m.totalAcquire);
        metrics.put("successRate", String.format("%.1f%%", m.successRate));
        result.put("metrics", metrics);
        result.put("balances", poolManager.queryAllBalances());
        result.put("providers", poolManager.listRegisteredProviders());
        return ApiResponse.ok(result);
    }

    @PostMapping("/reload")
    public ApiResponse<Void> reload() {
        if (poolManager == null) return ApiResponse.fail("池管理器未就绪");
        try {
            List<Map.Entry<ProviderType, ProxyProvider>> providers = buildProvidersFromDb();
            poolManager.reload(properties, providers);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("[PROXY] reload 失败: {}", e.getMessage(), e);
            return ApiResponse.fail("reload 失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/bindings/{accountId}")
    public ApiResponse<Void> unbind(@PathVariable Long accountId) {
        if (poolManager == null) return ApiResponse.fail("池管理器未就绪");
        poolManager.unbind(accountId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/health-check")
    public ApiResponse<Void> healthCheck() {
        if (poolManager == null) return ApiResponse.fail("池管理器未就绪");
        poolManager.runHealthCheck();
        return ApiResponse.ok(null);
    }

    private List<Map.Entry<ProviderType, ProxyProvider>> buildProvidersFromDb() {
        List<Map.Entry<ProviderType, ProxyProvider>> result = new ArrayList<>();
        List<ProxyConfig> list = configService.listEnabled();
        for (ProxyConfig cfg : list) {
            ProviderType pType = resolveProviderType(cfg.getProviderType());
            if (pType == null) continue;
            try {
                ProxyProvider provider = ProxyProviderFactory.build(cfg.getProviderType(), cfg.getConfigJson());
                if (provider != null) {
                    result.add(new AbstractMap.SimpleEntry<>(pType, provider));
                }
            } catch (Exception e) {
                log.warn("[PROXY] reload 时构建 {} 失败: {}", cfg.getProviderType(), e.getMessage());
            }
        }
        // sortOrder 已在 ProxyConfigService.listEnabled() 中按 sort_order asc 排列
        return result;
    }

    private ProviderType resolveProviderType(String name) {
        return switch (name) {
            case "abuyun" -> ProviderType.ABUYUN;
            case "smartproxy" -> ProviderType.SMARTPROXY;
            case "qg_tunnel", "qg_short_lived" -> ProviderType.QG;
            case "kuaidaili_tunnel" -> ProviderType.KUAILAILI_TUNNEL;
            case "kuaidaili_private" -> ProviderType.KUAILAILI_PRIVATE;
            default -> null;
        };
    }
}
