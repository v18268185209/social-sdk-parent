package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库初始化配置
 * 启动时自动执行 schema.sql 初始化数据库表结构
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final AuthService authService;
    private final cn.net.rjnetwork.xianyu.manager.config.db.DatabaseProvider databaseProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public DatabaseInitializer(DataSource dataSource, AuthService authService,
                               cn.net.rjnetwork.xianyu.manager.config.db.DatabaseProvider databaseProvider) {
        this.dataSource = dataSource;
        this.authService = authService;
        this.databaseProvider = databaseProvider;
    }

    @PostConstruct
    public void init() {
        try {
            String dbPath = System.getProperty("user.dir") + "/data";
            File dbDir = new File(dbPath);
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            ClassPathResource resource = new ClassPathResource(
                    databaseProvider != null ? databaseProvider.schemaFile() : "db/schema-sqlite.sql");
            if (resource.exists()) {
                executeSchemaPerStatement();
                logger.info("Database schema initialized successfully (dialect={})",
                        databaseProvider != null ? databaseProvider.dialect() : "sqlite(fallback)");
            } else {
                // 兜底：profile 指定的 schema 文件不存在时退回默认 schema.sql（向后兼容）
                logger.warn("Schema file not found, fallback to db/schema.sql");
                new ClassPathResource("db/schema.sql");
                executeSchemaPerStatement();
            }
            ensureNotifyRetryColumns();
            ensureNotifyDigestConfigTable();
            ensureProductColumns();
            ensureAiColumns();
            ensureVirtualColumns();
            ensureOrderColumns();
            ensureMessageColumns();
            ensureOpenAppTable();
            ensureImColumns();
            // ===== 新增模块的列补齐 =====
            ensureMarketColumns();
            ensureMonitorColumns();
            ensureBuyerProfileColumns();
            ensureCircuitBreakerColumns();
            ensureAiCsSessionStateColumns();
        } catch (Exception e) {
            logger.warn("Database initialization skipped (may already exist): {}", e.getMessage());
        }

        try {
            authService.initDefaultAdmin("admin", "admin123");
            logger.info("Default admin account initialized (username: admin, password: admin123)");
        } catch (Exception e) {
            logger.warn("Admin initialization skipped: {}", e.getMessage());
        }
    }

    private void ensureNotifyRetryColumns() {
        ensureColumn("notify_retry", "vars_json", "TEXT");
    }

    private void ensureNotifyDigestConfigTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean hasTable = false;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='notify_digest_config'")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    hasTable = rs.next();
                }
            }
            if (!hasTable) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute("CREATE TABLE notify_digest_config ("
                            + "id INTEGER PRIMARY KEY, "
                            + "enabled BOOLEAN DEFAULT FALSE, "
                            + "channel_id INTEGER, "
                            + "recipients TEXT, "
                            + "hour INTEGER DEFAULT 9, "
                            + "minute INTEGER DEFAULT 0, "
                            + "scenarios TEXT, "
                            + "include_in_app BOOLEAN DEFAULT TRUE, "
                            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                            + ")");
                    ensureColumn("notify_digest_config", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
                    logger.info("Created missing table notify_digest_config");
                }
            }
        } catch (Exception e) {
            logger.warn("ensureNotifyDigestConfigTable skipped: {}", e.getMessage());
        }
    }

    private void executeSchemaPerStatement() {
        String schemaFile = databaseProvider != null ? databaseProvider.schemaFile() : "db/schema-sqlite.sql";
        try (java.sql.Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             BufferedReader br = new BufferedReader(new InputStreamReader(
                     new ClassPathResource(schemaFile).getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder cur = new StringBuilder();
            List<String> stmts = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                line = stripInlineComment(line);
                String stripped = line.trim();
                if (stripped.isEmpty()) continue;
                cur.append(line).append('\n');
                if (stripped.endsWith(";")) {
                    String s = cur.toString().trim();
                    if (!s.isEmpty()) stmts.add(s);
                    cur.setLength(0);
                }
            }
            if (cur.length() > 0) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) stmts.add(s);
            }

            int ok = 0, skip = 0;
            for (String sql : stmts) {
                try {
                    st.execute(sql);
                    ok++;
                } catch (Exception ex) {
                    skip++;
                    String msg = ex.getMessage();
                    if (msg != null && msg.length() > 200) msg = msg.substring(0, 200);
                    logger.debug("schema stmt skipped (may already exist): {}", msg);
                }
            }
            logger.info("Schema executed: {} statements ok, {} skipped", ok, skip);
        } catch (Exception e) {
            logger.warn("executeSchemaPerStatement failed: {}", e.getMessage());
        }
    }

    private String stripInlineComment(String line) {
        boolean inSingle = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') {
                inSingle = !inSingle;
            } else if (c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-' && !inSingle) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private void ensureProductColumns() {
        ensureColumn("xianyu_product", "image_url", "VARCHAR(512)");
    }

    private void ensureAiColumns() {
        ensureColumn("xianyu_auto_reply_config", "ai_model_id", "BIGINT");
        ensureColumn("ai_ops_task", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_ops_suggestion", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureVirtualColumns() {
        ensureColumn("virtual_card_pool", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureOrderColumns() {
        ensureColumn("xianyu_order", "type", "VARCHAR(16) DEFAULT 'BOUGHT'");
        ensureColumn("xianyu_order", "trade_status_enum", "VARCHAR(32)");
        ensureColumn("xianyu_order", "is_seller", "TINYINT(1)");
        ensureColumn("xianyu_order", "goods_type", "VARCHAR(16) DEFAULT 'PHYSICAL'");
        ensureColumn("xianyu_order", "require_virtual_ship", "INTEGER DEFAULT 0");
        ensureColumn("xianyu_order", "virtual_shipped_at", "DATETIME");
        ensureColumn("xianyu_order", "auto_receipt_at", "DATETIME");
        ensureColumn("xianyu_order", "deliver_content", "TEXT");
    }

    private void ensureMessageColumns() {
        ensureColumn("xianyu_message", "msg_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "sender_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "sender_name", "VARCHAR(128)");
        ensureColumn("xianyu_message", "sender_avatar", "VARCHAR(512)");
        ensureColumn("xianyu_message", "msg_type", "VARCHAR(16)");
        ensureColumn("xianyu_message", "direction", "VARCHAR(8)");
        ensureColumn("xianyu_message", "auto_reply", "BOOLEAN");
    }

    private void ensureImColumns() {
        ensureColumn("xianyu_account", "im_cookie_header", "TEXT");
        ensureColumn("xianyu_account", "im_device_id", "VARCHAR(128)");
        ensureColumn("xianyu_account", "im_access_token", "TEXT");
        ensureColumn("xianyu_account", "im_token_expires_at", "DATETIME");
    }

    private void ensureOpenAppTable() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean hasTable = false;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='open_app'")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    hasTable = rs.next();
                }
            }
            if (!hasTable) {
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute("CREATE TABLE open_app ("
                            + "id INTEGER PRIMARY KEY, "
                            + "app_name VARCHAR(128) NOT NULL, "
                            + "app_key VARCHAR(64) NOT NULL UNIQUE, "
                            + "app_secret_enc VARCHAR(512), "
                            + "status VARCHAR(16) DEFAULT 'ENABLED', "
                            + "bound_account_ids TEXT, "
                            + "rate_limit_per_minute INTEGER DEFAULT 60, "
                            + "expire_at DATETIME, "
                            + "last_used_at DATETIME, "
                            + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                            + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                            + "deleted INTEGER DEFAULT 0"
                            + ")");
                    st.execute("CREATE INDEX idx_open_app_key ON open_app(app_key)");
                    logger.info("Created missing table open_app and index idx_open_app_key");
                }
            }
        } catch (Exception e) {
            logger.warn("ensureOpenAppTable skipped: {}", e.getMessage());
        }
    }

    // ======================== 新增模块迁移 ========================

    private void ensureMarketColumns() {
        // market_snapshot 表补齐
        ensureColumn("market_snapshot", "raw_data", "TEXT");
        ensureColumn("market_snapshot", "total_results", "INTEGER DEFAULT 0");
        // price_history 表补齐
        ensureColumn("price_history", "currency", "VARCHAR(8) DEFAULT 'CNY'");
        ensureColumn("price_history", "item_condition", "VARCHAR(32)");
        // market_daily_stat 表补齐
        ensureColumn("market_daily_stat", "p25_price", "REAL");
        ensureColumn("market_daily_stat", "p75_price", "REAL");
        ensureColumn("market_daily_stat", "volume", "INTEGER DEFAULT 0");
        ensureColumn("market_daily_stat", "sampled_count", "INTEGER DEFAULT 0");
    }

    private void ensureMonitorColumns() {
        // monitor_task 表补齐
        ensureColumn("monitor_task", "ai_prompt", "TEXT");
        ensureColumn("monitor_task", "ai_model_id", "BIGINT");
        ensureColumn("monitor_task", "cron_expression", "VARCHAR(64)");
        ensureColumn("monitor_task", "interval_minutes", "INTEGER DEFAULT 30");
        ensureColumn("monitor_task", "circuit_open", "INTEGER DEFAULT 0");
        ensureColumn("monitor_task", "circuit_open_until", "DATETIME");
        // monitor_result 表补齐
        ensureColumn("monitor_result", "matched_keywords", "TEXT");
        ensureColumn("monitor_result", "ai_score", "REAL");
        ensureColumn("monitor_result", "ai_reason", "TEXT");
    }

    private void ensureBuyerProfileColumns() {
        // buyer_profile 表补齐
        ensureColumn("buyer_profile", "credibility_score", "REAL DEFAULT 50");
        ensureColumn("buyer_profile", "tags", "TEXT");
        ensureColumn("buyer_profile", "notes", "TEXT");
        ensureColumn("buyer_profile", "total_spent", "REAL DEFAULT 0");
        // ai_cs_session_state 表补齐
        ensureColumn("ai_cs_session_state", "lowest_offer", "REAL");
        ensureColumn("ai_cs_session_state", "current_offer", "REAL");
    }

    private void ensureCircuitBreakerColumns() {
        // circuit_breaker 表补齐
        ensureColumn("circuit_breaker", "half_open_max_success", "INTEGER DEFAULT 3");
        ensureColumn("circuit_breaker", "cooldown_seconds", "INTEGER DEFAULT 300");
        ensureColumn("circuit_breaker", "threshold_count", "INTEGER DEFAULT 5");
        ensureColumn("circuit_breaker", "last_failure_message", "TEXT");
    }

    private void ensureAiCsSessionStateColumns() {
        // ai_cs_session 表补齐
        ensureColumn("ai_cs_session", "product_id", "INTEGER");
        ensureColumn("ai_cs_session", "order_id", "INTEGER");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (columnExists(conn, table, column)) {
                return;
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
                logger.info("Added column {} to {}", column, table);
            }
        } catch (Exception e) {
            logger.debug("ensureColumn {} on {}: {}", column, table, e.getMessage());
        }
    }

    private boolean columnExists(java.sql.Connection conn, String table, String column) {
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            if (rs.next()) {
                String createSql = rs.getString(1);
                if (createSql == null) return false;
                return java.util.regex.Pattern.compile(
                        "\\b" + java.util.regex.Pattern.quote(column) + "\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(createSql).find();
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
