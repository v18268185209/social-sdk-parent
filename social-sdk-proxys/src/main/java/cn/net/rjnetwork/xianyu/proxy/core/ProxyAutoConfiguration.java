package cn.net.rjnetwork.xianyu.proxy.core;

import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyProperties;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyType;
import cn.net.rjnetwork.xianyu.proxy.health.DefaultHealthChecker;
import cn.net.rjnetwork.xianyu.proxy.provider.AbuyunProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.KuaidailiProvider;
import cn.net.rjnetwork.xianyu.proxy.provider.SmartproxyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 代理池 Spring Boot 自动装配。
 *
 * <h3>装配条件</h3>
 * <ul>
 *   <li>配置 {@code proxy.enabled=true} 时启用（默认 true）</li>
 *   <li>任一供应商配置了 enabled=true 即启用该供应商</li>
 * </ul>
 *
 * <h3>装配产物</h3>
 * <ul>
 *   <li>{@link ProxyPoolManager} — 单例，自动注册所有启用的供应商</li>
 *   <li>{@link ProxyHealthChecker} — 默认使用 {@link DefaultHealthChecker}，可覆盖</li>
 *   <li>{@link ScheduledExecutorService} — 用于健康检查 / 冷名单复原 / 泄露检测</li>
 *   <li>{@link TaskScheduler} — Spring TaskScheduler，代理池内部用</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ProxyProperties.class)
@ConditionalOnProperty(prefix = "proxy", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProxyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ProxyAutoConfiguration.class);

    private final ProxyProperties properties;

    public ProxyAutoConfiguration(ProxyProperties properties) {
        this.properties = properties;
    }

    /**
     * 健康检查调度线程池。
     */
    @Bean(name = "proxyHealthCheckExecutor", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "proxy.health-check", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ScheduledExecutorService proxyHealthCheckExecutor() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "proxy-health-check");
            t.setDaemon(true);
            return t;
        });
        return executor;
    }

    /**
     * TaskScheduler — 代理池内部用于定时任务。
     */
    @Bean(name = "proxyTaskScheduler")
    public TaskScheduler proxyTaskScheduler(@Qualifier("proxyHealthCheckExecutor") ScheduledExecutorService executor) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("proxy-scheduler-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 默认健康检查器，可被业务方自定义的 bean 覆盖。
     */
    @Bean
    @ConditionalOnMissingBean(ProxyHealthChecker.class)
    public ProxyHealthChecker proxyHealthChecker() {
        DefaultHealthChecker checker = new DefaultHealthChecker(
                properties.getHealthCheck().getMaxLatencyMs(),
                properties.getHealthCheck().isGeoCheck());
        log.info("[PROXY-AUTOCONFIG] 创建 DefaultHealthChecker, maxLatencyMs={}, geoCheck={}",
                properties.getHealthCheck().getMaxLatencyMs(), properties.getHealthCheck().isGeoCheck());
        return checker;
    }

    /**
     * 代理池管理器实现。
     */
    @Bean
    @ConditionalOnMissingBean(ProxyPoolManager.class)
    public DefaultProxyPoolManager proxyPoolManager(ProxyHealthChecker healthChecker) {
        DefaultProxyPoolManager manager = new DefaultProxyPoolManager(healthChecker);
        manager.setReuseBoundIp(properties.isReuseBoundIp());
        manager.setMaxBindingUseCount(properties.getMaxBindingUseCount());
        manager.setCoolDownRecoveryMinutes(properties.getCoolDownRecoveryMinutes());
        manager.setLeaseLeakThresholdMinutes(properties.getLeaseLeakThresholdMinutes());
        log.info("[PROXY-AUTOCONFIG] 创建 DefaultProxyPoolManager");
        return manager;
    }

    /**
     * 业务方的公开接口，直接暴露为 ProxyPoolManager。
     */
    @Bean
    @ConditionalOnMissingBean(ProxyPoolManager.class)
    public ProxyPoolManager proxyPool(DefaultProxyPoolManager proxyPoolManager) {
        return proxyPoolManager;
    }

    /**
     * 阿布云供应商 bean（条件注册）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "proxy.providers.abuyun", name = "enabled", havingValue = "true")
    public AbuyunProvider abuyunProvider() {
        ProxyProperties.ProviderConfig cfg = properties.getProviders().get("abuyun");
        if (cfg == null) {
            throw new IllegalStateException("阿布云启用但未配置 proxy.providers.abuyun.*");
        }
        AbuyunProvider provider = new AbuyunProvider(cfg.getUsername(), cfg.getPassword(), cfg.getHost(), cfg.getPort());
        log.info("[PROXY-AUTOCONFIG] 注册阿布云供应商, host={}:{}", cfg.getHost(), cfg.getPort());
        return provider;
    }

    /**
     * 快代理供应商 bean（条件注册）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "proxy.providers.kuaidaili", name = "enabled", havingValue = "true")
    public KuaidailiProvider kuaidailiProvider() {
        ProxyProperties.ProviderConfig cfg = properties.getProviders().get("kuaidaili");
        if (cfg == null) {
            throw new IllegalStateException("快代理启用但未配置 proxy.providers.kuaidaili.*");
        }
        KuaidailiProvider provider = new KuaidailiProvider(
                cfg.getOrderId(), cfg.getApiKey(), cfg.getTunnelHost(), cfg.getTunnelPort());
        log.info("[PROXY-AUTOCONFIG] 注册快代理供应商, tunnelHost={}:{}", cfg.getTunnelHost(), cfg.getTunnelPort());
        return provider;
    }

    /**
     * Smartproxy 供应商 bean（条件注册）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "proxy.providers.smartproxy", name = "enabled", havingValue = "true")
    public SmartproxyProvider smartproxyProvider() {
        ProxyProperties.ProviderConfig cfg = properties.getProviders().get("smartproxy");
        if (cfg == null) {
            throw new IllegalStateException("Smartproxy 启用但未配置 proxy.providers.smartproxy.*");
        }
        SmartproxyProvider provider = new SmartproxyProvider(
                cfg.getUsername(), cfg.getPassword(), cfg.getHost(), cfg.getPort(), cfg.getCity());
        log.info("[PROXY-AUTOCONFIG] 注册 Smartproxy 供应商, host={}:{}", cfg.getHost(), cfg.getPort());
        return provider;
    }

    /**
     * 把所有启用的供应商注册到 ProxyPoolManager。
     */
    @Bean
    public ProxyPoolRegistrar proxyPoolRegistrar(
            DefaultProxyPoolManager poolManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AbuyunProvider abuyunProvider,
            @org.springframework.beans.factory.annotation.Autowired(required = false) KuaidailiProvider kuaidailiProvider,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SmartproxyProvider smartproxyProvider,
            @Qualifier("proxyTaskScheduler") TaskScheduler taskScheduler) {

        poolManager.setHealthCheckScheduler(
                Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "proxy-scheduler-worker");
                    t.setDaemon(true);
                    return t;
                }));

        if (abuyunProvider != null) {
            poolManager.registerProvider(ProviderType.ABUYUN, abuyunProvider);
        }
        if (kuaidailiProvider != null) {
            poolManager.registerProvider(ProviderType.KUAILAILI, kuaidailiProvider);
        }
        if (smartproxyProvider != null) {
            poolManager.registerProvider(ProviderType.SMARTPROXY, smartproxyProvider);
        }

        log.info("[PROXY-AUTOCONFIG] 供应商注册完成, providers={}", poolManager.listRegisteredProviders());
        return new ProxyPoolRegistrar(poolManager);
    }

    /**
     * 注册器（仅触发一次注册流程）
     */
    public static class ProxyPoolRegistrar {
        private final DefaultProxyPoolManager manager;

        public ProxyPoolRegistrar(DefaultProxyPoolManager manager) {
            this.manager = manager;
        }
    }
}
