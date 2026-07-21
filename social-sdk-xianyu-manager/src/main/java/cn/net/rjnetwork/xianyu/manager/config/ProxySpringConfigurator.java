package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyHealthChecker;
import cn.net.rjnetwork.xianyu.proxy.config.ProxyProperties;
import cn.net.rjnetwork.xianyu.proxy.core.DefaultProxyPoolManager;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyPoolManager;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * DB 融合装配器：SDK 装配完成后，读取 SQLite proxy_config 表，覆盖 YAML。
 *
 * <p>装配时序：after {@code ProxyAutoConfiguration}。</p>
 */
@Configuration
@AutoConfigureAfter(name = "cn.net.rjnetwork.xianyu.proxy.core.ProxyAutoConfiguration")
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxySpringConfigurator {

    private static final Logger log = LoggerFactory.getLogger(ProxySpringConfigurator.class);

    private final ProxyProperties properties;

    public ProxySpringConfigurator(ProxyProperties properties) {
        this.properties = properties;
    }

    /**
     * 提供 proxyTaskScheduler，并注入 poolManager。
     * 实际 reload 逻辑由 DatabaseInitializer / ProxyController 触发。
     */
    @Bean(name = "proxyTaskScheduler", destroyMethod = "shutdown")
    public TaskScheduler proxyTaskScheduler(DefaultProxyPoolManager poolManager) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("proxy-scheduler-");
        scheduler.setDaemon(true);
        scheduler.initialize();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "proxy-health-check");
            t.setDaemon(true);
            return t;
        });
        poolManager.setHealthCheckScheduler(executor);
        return scheduler;
    }
}
