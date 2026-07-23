package cn.net.rjnetwork.xianyu.proxy.persistence;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.config.ProviderType;
import cn.net.rjnetwork.xianyu.proxy.core.DefaultProxyPoolManager;
import cn.net.rjnetwork.xianyu.proxy.persistence.entity.ProxyAccountBinding;
import cn.net.rjnetwork.xianyu.proxy.persistence.repository.BindingRepository;
import cn.net.rjnetwork.xianyu.proxy.persistence.repository.MySqlBindingRepository;
import cn.net.rjnetwork.xianyu.proxy.persistence.repository.PostgresBindingRepository;
import cn.net.rjnetwork.xianyu.proxy.persistence.repository.SqliteBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;

/**
 * 绑定持久化自动装配。
 *
 * <p>装配条件：</p>
 * <ul>
 *   <li>{@code proxy.enabled=true}</li>
 *   <li>classpath 存在 {@link DataSource}（即应用已配 SQLite / MySQL / PostgreSQL 数据源）</li>
 * </ul>
 *
 * <p>根据 Spring Profile 选择对应数据库实现：</p>
 * <ul>
 *   <li>{@code sqlite}（默认）→ {@link SqliteBindingRepository}</li>
 *   <li>{@code mysql} → {@link MySqlBindingRepository}</li>
 *   <li>{@code postgres} → {@link PostgresBindingRepository}</li>
 * </ul>
 *
 * <p>业务方可自定义 {@link BindingRepository} Bean 覆盖默认行为（{@link ConditionalOnMissingBean}）。</p>
 *
 * <p>启动时自动从 DB 加载所有有效绑定到内存，保证应用重启后绑定关系不丢。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "proxy", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(DataSource.class)
public class BindingPersistenceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BindingPersistenceAutoConfiguration.class);

    /**
     * SQLite 实现（默认 / {@code sqlite} profile）。
     */
    @Bean
    @ConditionalOnMissingBean(BindingRepository.class)
    @Profile({"sqlite", "default"})
    public BindingRepository sqliteBindingRepository(DataSource dataSource) {
        log.info("[PROXY-AUTOCONFIG] 创建 SqliteBindingRepository (profile=sqlite/default)");
        return new SqliteBindingRepository(dataSource);
    }

    /**
     * MySQL 实现（{@code mysql} profile）。
     */
    @Bean
    @ConditionalOnMissingBean(BindingRepository.class)
    @Profile("mysql")
    public BindingRepository mysqlBindingRepository(DataSource dataSource) {
        log.info("[PROXY-AUTOCONFIG] 创建 MySqlBindingRepository (profile=mysql)");
        return new MySqlBindingRepository(dataSource);
    }

    /**
     * PostgreSQL 实现（{@code postgres} profile）。
     */
    @Bean
    @ConditionalOnMissingBean(BindingRepository.class)
    @Profile("postgres")
    public BindingRepository postgresBindingRepository(DataSource dataSource) {
        log.info("[PROXY-AUTOCONFIG] 创建 PostgresBindingRepository (profile=postgres)");
        return new PostgresBindingRepository(dataSource);
    }

    /**
     * 启动时从 DB 加载所有有效绑定到 ProxyPoolManager 内存。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadBindingsOnStartup(ApplicationReadyEvent event) {
        DefaultProxyPoolManager pool = event.getApplicationContext().getBean(DefaultProxyPoolManager.class);
        BindingRepository repo = event.getApplicationContext().getBean(BindingRepository.class);

        if (pool == null || repo == null) return;

        try {
            var bindings = repo.findAllActive();
            for (ProxyAccountBinding b : bindings) {
                ProxyInfo proxy = ProxyInfo.builder()
                        .providerType(ProviderType.fromString(b.getProviderType()))
                        .host(b.getHost())
                        .port(b.getPort())
                        .username(b.getUsername())
                        .password(b.getPassword())
                        .exitIp(b.getExitIp())
                        .city(b.getCity())
                        .boundAccountId(b.getAccountId())
                        .captchaPassed(b.getCaptchaPassed() != null && b.getCaptchaPassed())
                        .build();
                pool.registerBinding(b.getAccountId(), proxy);
            }
            log.info("[PROXY-AUTOCONFIG] 启动加载绑定完成, 共 {} 条", bindings.size());
        } catch (Exception e) {
            log.warn("[PROXY-AUTOCONFIG] 启动加载绑定失败: {}", e.getMessage());
        }
    }
}
