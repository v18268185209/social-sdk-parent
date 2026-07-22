package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.config.db.DatabaseProvider;
import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Druid 连接池配置 — 三选一适配 SQLite/MySQL8/PostgreSQL。
 * <p>连接初始化 SQL / maxActive / 验证查询由当前 {@link DatabaseProvider} 提供，
 * profile 决定方言（默认 sqlite，可选 mysql / postgres）。</p>
 */
@Configuration
@ConditionalOnClass(DruidDataSource.class)
@ConditionalOnProperty(prefix = "spring.datasource", name = "type", havingValue = "com.alibaba.druid.pool.DruidDataSource")
public class DruidConfig {

    private static final Logger log = LoggerFactory.getLogger(DruidConfig.class);

    @Autowired(required = false)
    private DatabaseProvider databaseProvider;

    @Bean
    @Primary
    public DataSource druidDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.driver-class-name}") String driverClassName,
            @Value("${spring.datasource.username:#{null}}") String username,
            @Value("${spring.datasource.password:#{null}}") String password) throws SQLException {

        ensureSqliteDatabaseDirectory(url);

        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(url);
        ds.setDriverClassName(driverClassName);
        if (username != null && !username.isBlank()) ds.setUsername(username);
        if (password != null && !password.isBlank()) ds.setPassword(password);

        // ===== 池大小：SQLite 单连接最优；MySQL/PG 可并发 =====
        int maxActive = databaseProvider != null ? databaseProvider.maxActive() : 1;
        ds.setInitialSize(1);
        ds.setMinIdle(1);
        ds.setMaxActive(maxActive);
        ds.setMaxWait(30000);

        // ===== 心跳与空闲 =====
        ds.setTimeBetweenEvictionRunsMillis(60000);
        ds.setMinEvictableIdleTimeMillis(300000);
        ds.setMaxEvictableIdleTimeMillis(600000);
        String validationQuery = databaseProvider != null ? databaseProvider.validationQuery() : "SELECT 1";
        ds.setValidationQuery(validationQuery);
        ds.setTestWhileIdle(true);
        ds.setTestOnBorrow(false);
        ds.setTestOnReturn(false);
        ds.setKeepAlive(true);

        // ===== 连接初始化 SQL（SQLite PRAGMA / MySQL/PG SET） =====
        if (databaseProvider != null) {
            String[] initSqls = databaseProvider.connectionInitSqls();
            if (initSqls != null && initSqls.length > 0) {
                ds.setConnectionInitSqls(Arrays.asList(initSqls));
            }
        }

        // ===== 过滤器 =====
        // SQLite 本地文件库不启用 wall，避免拦截 AUTOINCREMENT 等 SQLite 方言语法。
        String dialect = databaseProvider != null ? databaseProvider.dialect() : "sqlite";
        ds.setFilters("sqlite".equalsIgnoreCase(dialect) ? "stat,slf4j" : "stat,wall,slf4j");

        // ===== 连接泄漏检测 =====
        // SQLite 单连接启动初始化阶段容易被 removeAbandoned 误判，保持关闭。
        ds.setRemoveAbandoned(!"sqlite".equalsIgnoreCase(dialect));
        ds.setRemoveAbandonedTimeout(1800);
        ds.setLogAbandoned(false);

        // ===== PSCache =====
        ds.setPoolPreparedStatements(true);
        ds.setMaxPoolPreparedStatementPerConnectionSize(20);

        log.info("DruidDataSource initialized dialect={}, maxActive={}, url={}", dialect, maxActive, url);
        return ds;
    }

    private void ensureSqliteDatabaseDirectory(String url) {
        if (url == null || !url.startsWith("jdbc:sqlite:")) {
            return;
        }
        String path = url.substring("jdbc:sqlite:".length());
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (path.isBlank() || ":memory:".equals(path) || path.startsWith("file:")) {
            return;
        }
        File dbFile = new File(path);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("Failed to create SQLite database directory: {}", parent.getAbsolutePath());
        }
    }
}

