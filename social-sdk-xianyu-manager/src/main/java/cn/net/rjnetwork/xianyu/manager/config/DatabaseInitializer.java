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
            ensureProductColumns();
            ensureAiColumns();
            ensureOrderColumns();
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
                String stripped = line.trim();
                if (stripped.isEmpty() || stripped.startsWith("--")) continue;  // 跳空行 + 注释
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

    private void ensureOrderColumns() {
        ensureColumn("xianyu_order", "trade_status_enum", "VARCHAR(32)");
        ensureColumn("xianyu_order", "is_seller", "TINYINT(1)");
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
