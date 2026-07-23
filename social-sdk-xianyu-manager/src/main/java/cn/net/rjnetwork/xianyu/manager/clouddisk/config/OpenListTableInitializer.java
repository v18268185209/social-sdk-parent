package cn.net.rjnetwork.xianyu.manager.clouddisk.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OpenList 数据库表初始化——确保 openlist_instance 表存在。
 * 解决已有数据库缺失该表的问题，新部署自动创建。
 *
 * <p>支持三种方言：SQLite（{@code sqlite_master} + {@code INTEGER PRIMARY KEY AUTOINCREMENT}）、
 * MySQL（{@code information_schema.tables} + {@code BIGINT AUTO_INCREMENT}）、
 * PostgreSQL（{@code information_schema.tables} + {@code BIGSERIAL}）。
 * 方言通过 {@code spring.datasource.driver-class-name} 推断，或显式 {@code db.dialect} 属性覆盖。</p>
 */
@Component
public class OpenListTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(OpenListTableInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 显式方言覆盖（sqlite/mysql/postgres），未设则按 driver-class-name 推断 */
    @Value("${db.dialect:}")
    private String dialect;

    @Value("${spring.datasource.driver-class-name:}")
    private String driverClassName;

    @PostConstruct
    public void init() {
        String dialect = resolveDialect();
        try {
            if (!tableExists(dialect)) {
                jdbcTemplate.execute(createTableSql(dialect));
                log.info("[OpenList] 已自动创建 openlist_instance 表 (dialect={})", dialect);
            }
        } catch (Exception e) {
            // DatabaseInitializer 已在 schema 里建过 openlist_instance，此处兜底失败属正常
            log.debug("[OpenList] 初始化表失败（可能已被 schema 建好）: {}", e.getMessage());
        }
    }

    private String resolveDialect() {
        if (dialect != null && !dialect.isBlank()) {
            return dialect.toLowerCase();
        }
        if (driverClassName == null) return "sqlite";
        String d = driverClassName.toLowerCase();
        if (d.contains("mysql")) return "mysql";
        if (d.contains("postgresql") || d.contains("postgres")) return "postgres";
        return "sqlite";
    }

    private boolean tableExists(String dialect) {
        String checkSql;
        switch (dialect) {
            case "mysql":
            case "postgres":
                // ANSI 标准 information_schema，MySQL/PG 都支持
                checkSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = "
                        + schemaLiteral(dialect) + " AND table_name = 'openlist_instance'";
                break;
            default:
                checkSql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='openlist_instance'";
        }
        try {
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String schemaLiteral(String dialect) {
        // MySQL 默认 schema =库名，可走 DATABASE()；PG 默认 public
        if ("postgres".equals(dialect)) {
            return "'public'";
        }
        // mysql：用当前库名（DATABASE()）
        return "(SELECT DATABASE())";
    }

    private String createTableSql(String dialect) {
        switch (dialect) {
            case "mysql":
                return "CREATE TABLE IF NOT EXISTS openlist_instance (" +
                        "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                        "  port INTEGER NOT NULL DEFAULT 5244," +
                        "  url VARCHAR(256) DEFAULT 'http://127.0.0.1:5244'," +
                        "  data_dir VARCHAR(512)," +
                        "  initial_username VARCHAR(128)," +
                        "  initial_password VARCHAR(128)," +
                        "  install_path VARCHAR(512)," +
                        "  os_name VARCHAR(32)," +
                        "  arch VARCHAR(16)," +
                        "  installed INTEGER DEFAULT 0," +
                        "  running INTEGER DEFAULT 0," +
                        "  first_started_at TIMESTAMP NULL," +
                        "  last_started_at TIMESTAMP NULL," +
                        "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            case "postgres":
                return "CREATE TABLE IF NOT EXISTS openlist_instance (" +
                        "  id BIGSERIAL PRIMARY KEY," +
                        "  port INTEGER NOT NULL DEFAULT 5244," +
                        "  url VARCHAR(256) DEFAULT 'http://127.0.0.1:5244'," +
                        "  data_dir VARCHAR(512)," +
                        "  initial_username VARCHAR(128)," +
                        "  initial_password VARCHAR(128)," +
                        "  install_path VARCHAR(512)," +
                        "  os_name VARCHAR(32)," +
                        "  arch VARCHAR(16)," +
                        "  installed INTEGER DEFAULT 0," +
                        "  running INTEGER DEFAULT 0," +
                        "  first_started_at TIMESTAMP," +
                        "  last_started_at TIMESTAMP," +
                        "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
            default:
                return "CREATE TABLE IF NOT EXISTS openlist_instance (" +
                        "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "  port INTEGER NOT NULL DEFAULT 5244," +
                        "  url VARCHAR(256) DEFAULT 'http://127.0.0.1:5244'," +
                        "  data_dir VARCHAR(512)," +
                        "  initial_username VARCHAR(128)," +
                        "  initial_password VARCHAR(128)," +
                        "  install_path VARCHAR(512)," +
                        "  os_name VARCHAR(32)," +
                        "  arch VARCHAR(16)," +
                        "  installed INTEGER DEFAULT 0," +
                        "  running INTEGER DEFAULT 0," +
                        "  first_started_at DATETIME," +
                        "  last_started_at DATETIME," +
                        "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                        ")";
        }
    }
}
