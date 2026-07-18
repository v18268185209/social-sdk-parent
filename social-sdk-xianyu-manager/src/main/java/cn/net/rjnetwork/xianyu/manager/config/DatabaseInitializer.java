package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据库初始化配置
 * 启动时自动执行 schema.sql 初始化数据库表结构
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final AuthService authService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public DatabaseInitializer(DataSource dataSource, AuthService authService) {
        this.dataSource = dataSource;
        this.authService = authService;
    }

    @PostConstruct
    public void init() {
        // 确保数据目录存在
        try {
            String dbPath = System.getProperty("user.dir") + "/data";
            File dbDir = new File(dbPath);
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            // 执行 schema 初始化
            ClassPathResource resource = new ClassPathResource("db/schema.sql");
            if (resource.exists()) {
                ScriptUtils.executeSqlScript(dataSource.getConnection(), resource);
                logger.info("Database schema initialized successfully");
            }
            ensureNotifyRetryColumns();
            ensureProductColumns();
            ensureAiColumns();
        } catch (Exception e) {
            logger.warn("Database initialization skipped (may already exist): {}", e.getMessage());
        }

        // 初始化默认管理员账户
        try {
            authService.initDefaultAdmin("admin", "admin123");
            logger.info("Default admin account initialized (username: admin, password: admin123)");
        } catch (Exception e) {
            logger.warn("Admin initialization skipped: {}", e.getMessage());
        }
    }

    /** 兼容旧库：schema 的 CREATE TABLE IF NOT EXISTS 不会给已存在的表补列，这里手动补齐 */
    private void ensureNotifyRetryColumns() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean hasCol = false;
            try (java.sql.PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(notify_retry)")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if ("vars_json".equalsIgnoreCase(rs.getString("name"))) {
                            hasCol = true;
                            break;
                        }
                    }
                }
            }
            if (!hasCol) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE notify_retry ADD COLUMN vars_json TEXT");
                    logger.info("Added column vars_json to notify_retry");
                }
            }
        } catch (Exception e) {
            logger.warn("ensureNotifyRetryColumns skipped: {}", e.getMessage());
        }
    }

    /** 兼容旧库：xianyu_product 早期 DDL 缺 image_url 列，这里手动补齐 */
    private void ensureProductColumns() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean hasImageUrl = false;
            try (java.sql.PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(xianyu_product)")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if ("image_url".equalsIgnoreCase(rs.getString("name"))) {
                            hasImageUrl = true;
                            break;
                        }
                    }
                }
            }
            if (!hasImageUrl) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE xianyu_product ADD COLUMN image_url VARCHAR(512)");
                    logger.info("Added column image_url to xianyu_product");
                }
            }
        } catch (Exception e) {
            logger.warn("ensureProductColumns skipped: {}", e.getMessage());
        }
    }

    /** 兼容旧库：补齐 AI 相关表缺失列（实体字段 ↔ DDL 列不匹配会导致 selectList 500） */
    private void ensureAiColumns() {
        // xianyu_auto_reply_config.ai_model_id（实体 aiModelId 映射目标）
        ensureColumn("xianyu_auto_reply_config", "ai_model_id", "BIGINT");
        // ai_ops_task / ai_ops_suggestion 缺 BaseEntity 的 updated_at
        ensureColumn("ai_ops_task", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_ops_suggestion", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean has = false;
            try (java.sql.PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (column.equalsIgnoreCase(rs.getString("name"))) {
                            has = true;
                            break;
                        }
                    }
                }
            }
            if (!has) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
                    logger.info("Added column {} to {}", column, table);
                }
            }
        } catch (Exception e) {
            logger.warn("ensureColumn {} on {} skipped: {}", column, table, e.getMessage());
        }
    }
}
