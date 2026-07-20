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

            // 执行 schema 初始化（逐行执行 + 容错：已存在的表/索引/列跳过，不让单行错回滚整个脚本）
            // 不用 Spring ScriptUtils.executeSqlScript（默认 FAIL_ON_ERROR + 整脚本当事务遇错即全回滚）
            ClassPathResource resource = new ClassPathResource("db/schema.sql");
            if (resource.exists()) {
                executeSchemaPerStatement();
                logger.info("Database schema initialized successfully");
            }
            ensureNotifyRetryColumns();
            ensureNotifyDigestConfigTable();
            ensureProductColumns();
            ensureAiColumns();
            ensureVirtualColumns();
            ensureOrderColumns();
            ensureMessageColumns();
            ensureOpenAppTable();
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
        ensureColumn("notify_retry", "vars_json", "TEXT");
    }

    /**
     * 兼容旧库：确保 notify_digest_config 表存在（早期 schema.sql 未包含此表，加上 CREATE IF NOT EXISTS
     * 后新建库已有，但旧库里没有，启动时查此表会报 no such table）。
     */
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

    /**
     * 逐行执行 schema.sql + 容错：已存在的表/索引/列跳过，不让单行错回滚整个脚本。
     * <p>不用 Spring ScriptUtils.executeSqlScript（默认 FAIL_ON_ERROR + 整脚本当事务遇错即全回滚，
     * 导致第 12 行 CREATE INDEX 中断后前 11 张表也回滚 DB 最终 0 表）。</p>
     *
     * <p>用正常 Druid 池连接（try-with-resources 归还），不要 {@code unwrap(Connection)} 拿原生
     * Connection —— unwrap 拿到的原生 Connection 脱离 Druid 代理管理，close 它并不会让池中
     * 对应的代理槽位归还，结果初始化阶段永久占住一个池连接，后续 ensureColumn 拿不到连接
     * 卡在 max-wait 上（日志里 `wait millis 30000, active 1, maxActive 1` 就是这个症状）。</p>
     */
    private void executeSchemaPerStatement() {
        try (java.sql.Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             BufferedReader br = new BufferedReader(new InputStreamReader(
                     new ClassPathResource("db/schema.sql").getInputStream(), StandardCharsets.UTF_8))) {

            // 拼多行语句（CREATE TABLE 多行直到 ; 结束），再逐句执行
            StringBuilder cur = new StringBuilder();
            List<String> stmts = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                // 剥离行内 -- 注释：Druid WallFilter 对 SQLite 默认 commentAllow=false，
                // 带 -- 注释的 CREATE TABLE 会被当 SQL 注入拦掉（"sql injection violation ...
                // comment not allow"），导致 open_app / ai_ops_knowledge / ai_cs_knowledge 等
                // 表建不出来，后续 CREATE INDEX 报 no such table。执行前先去掉行内注释即可。
                line = stripInlineComment(line);
                String stripped = line.trim();
                if (stripped.isEmpty()) continue;  // 跳空行 + 已被剥成空的整行注释
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
                    // 已存在 / 重复创建 / 不认的语法 → 跳过继续（CREATE IF NOT EXISTS 已容重，错也不当 fatal）
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

    /**
     * 剥离 SQL 行内的 -- 注释（保留单引号字符串字面量内的 --）。
     * schema.sql 的建表语句普遍带 -- 中文注释，Druid WallFilter(sqlite) 默认不允许注释会整条拒绝，
     * 因此执行前逐行去掉 -- 之后的内容。
     */
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

    /** 兼容旧库：xianyu_product 早期 DDL 缺 image_url 列，这里手动补齐 */
    private void ensureProductColumns() {
        ensureColumn("xianyu_product", "image_url", "VARCHAR(512)");
    }

    /** 兼容旧库：补齐 AI 相关表缺失列（实体字段 ↔ DDL 列不匹配会导致 selectList 500） */
    private void ensureAiColumns() {
        // xianyu_auto_reply_config.ai_model_id（实体 aiModelId 映射目标）
        ensureColumn("xianyu_auto_reply_config", "ai_model_id", "BIGINT");
        // ai_ops_task / ai_ops_suggestion 缺 BaseEntity 的 updated_at
        ensureColumn("ai_ops_task", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("ai_ops_suggestion", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    /** 兼容旧库：virtual_card_pool 缺 BaseEntity 的 updated_at */
    private void ensureVirtualColumns() {
        ensureColumn("virtual_card_pool", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureOrderColumns() {
        // 从 schema.sql 定义的完整 xianyu_order 表结构补齐可能缺失的列
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
        // xianyu_message 兼容旧库补齐可能缺失的列
        ensureColumn("xianyu_message", "msg_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "sender_id", "VARCHAR(64)");
        ensureColumn("xianyu_message", "sender_name", "VARCHAR(128)");
        ensureColumn("xianyu_message", "msg_type", "VARCHAR(16)");
        ensureColumn("xianyu_message", "direction", "VARCHAR(8)");
        ensureColumn("xianyu_message", "auto_reply", "BOOLEAN");
    }

    /**
     * 兼容旧库：确保 open_app 表存在（早期 schema.sql 未包含此表，加上 CREATE IF NOT EXISTS
     * 后新建库已有，但旧库里没有，启动时执行 CREATE INDEX idx_open_app_key 会报 no such table）。
     */
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

    private void ensureColumn(String table, String column, String ddl) {
        // 不用 PRAGMA table_info（Druid WallFilter 对 SQLite 不认 PRAGMA 会拦掉）。
        // 改为：先用 WallFilter 允许的 SELECT * FROM <表> LIMIT 0 + ResultSetMetaData 查列是否存在，
        // 存在则直接跳过（不再触发 ALTER → duplicate column 异常 → Druid 记 ERROR 日志）；
        // 不存在才执行 ALTER TABLE ADD COLUMN。
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (columnExists(conn, table, column)) {
                return;  // 列已存在，无需补
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
                logger.info("Added column {} to {}", column, table);
            }
        } catch (Exception e) {
            logger.debug("ensureColumn {} on {}: {}", column, table, e.getMessage());
        }
    }

    /**
     * 通过查 sqlite_master 拿到表的 CREATE 语句，按词边界匹配列名是否存在。
     * WallFilter 允许对 sqlite_master 的 SELECT（ensureOpenAppTable 同样用法，已验证），
     * 且不触发 ALTER → duplicate column 异常 → Druid 记 ERROR 日志。
     * 表不存在或查询失败时返回 false（让调用方后续 ALTER 自行处理）。
     */
    private boolean columnExists(java.sql.Connection conn, String table, String column) {
        try (java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            if (rs.next()) {
                String createSql = rs.getString(1);
                if (createSql == null) return false;
                // 词边界匹配，避免 goods_type 误匹配 type 这种子串情况
                return createSql.matches("(?i).*\\b" + java.util.regex.Pattern.quote(column) + "\\b.*");
            }
        } catch (Exception ignored) {
            // 表不存在或查询失败 → 当作列不存在
        }
        return false;
    }
}
