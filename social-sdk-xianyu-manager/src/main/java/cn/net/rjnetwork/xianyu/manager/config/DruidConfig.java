^package cn.net.rjnetwork.xianyu.manager.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Druid 连接池 + SQLite PRAGMA 优化配置
 *
 * <p>SQLite 连接池最优配置要点：</p>
 * <ol>
 *   <li>WAL 模式 - 读写并发，必开</li>
 *   <li>busy_timeout - 等待锁释放，避免 lock 异常</li>
 *   <li>journal_mode=WAL + synchronous=NORMAL - 性能与安全平衡</li>
 *   <li>cache_size=-16000 - 缓存 16MB 热数据</li>
 *   <li>mmap_size=256MB - 内存映射读加速</li>
 *   <li>temp_store=MEMORY - 临时表在内存</li>
 *   <li>Foreign Keys=ON - 外键约束</li>
 *   <li>max-active=1 - SQLite 文件锁限制，单连接最优，依赖 busy_timeout 调度读写</li>
 * </ol>
 */
@Configuration
@ConditionalOnClass(DruidDataSource.class)
@ConditionalOnProperty(prefix = "spring.datasource", name = "type", havingValue = "com.alibaba.druid.pool.DruidDataSource")
public class DruidConfig {

    private static final Logger log = LoggerFactory.getLogger(DruidConfig.class);

    /**
     * 连接初始化 SQL — SQLite PRAGMA 优化
     *
     * <p>每条新连接建立后依次执行，注入 WAL/synchronous/cache_size/busy_timeout 等。</p>
     */
    private static final String[] SQLITE_PRAGMA = {
            "PRAGMA journal_mode=WAL",
            "PRAGMA synchronous=NORMAL",
            "PRAGMA busy_timeout=30000",
            "PRAGMA cache_size=-16000",
            "PRAGMA mmap_size=268435456",
            "PRAGMA temp_store=MEMORY",
            "PRAGMA foreign_keys=ON"
    };

    @Bean
    @Primary
    public DataSource druidDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) throws SQLException {

        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(url);
        ds.setDriverClassName(driverClassName);

        // ===== SQLite 单连接最优：busy_timeout 替代多连接竞争 =====
        ds.setInitialSize(1);
        ds.setMinIdle(1);
        ds.setMaxActive(1);
        ds.setMaxWait(30000);

        // ===== 心跳与空闲 =====
        ds.setTimeBetweenEvictionRunsMillis(60000);
        ds.setMinEvictableIdleTimeMillis(300000);
        ds.setMaxEvictableIdleTimeMillis(600000);
        ds.setValidationQuery("SELECT 1");
        ds.setTestWhileIdle(true);
        ds.setTestOnBorrow(false);
        ds.setTestOnReturn(false);
        ds.setKeepAlive(true);

        // ===== PRAGMA 注入 =====
        ds.setConnectionInitSqls(Arrays.asList(SQLITE_PRAGMA));
        ds.setConnectionProperties("busy_timeout=30000;journal_mode=WAL;synchronous=NORMAL");

        // ===== 防火墙（SQLite 版） =====
        ds.setFilters("stat,wall,slf4j");

        // ===== 监控 Servlet =====（通过 application.yml stat-view-servlet 配置即可，无需 Java API）
        // ds.setResetEnable(false); // DruidDataSource 无此方法，由 YAML 控制

        // ===== 连接泄漏检测 =====
        ds.setRemoveAbandoned(true);
        ds.setRemoveAbandonedTimeout(1800);
        ds.setLogAbandoned(false);

        // ===== PSCache（实际无效，但保留配置接口兼容） =====
        ds.setPoolPreparedStatements(true);
        ds.setMaxPoolPreparedStatementPerConnectionSize(20);

        log.info("DruidDataSource initialized with SQLite PRAGMA (WAL, busy_timeout=30s, cache=16MB, mmap=256MB)");
        return ds;
    }
}
