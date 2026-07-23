package cn.net.rjnetwork.xianyu.proxy.persistence.repository;

import cn.net.rjnetwork.xianyu.proxy.persistence.entity.ProxyAccountBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 实现的 {@link BindingRepository}。与应用共用同一个 PG 库，表名前缀 {@code proxy_}。
 *
 * <p>通过构造传入 {@link DataSource}，与应用库复用同一个连接池（HikariCP）。</p>
 *
 * <p>所有方法都 synchronous，调用方应自行切换线程池。</p>
 */
public class PostgresBindingRepository implements BindingRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresBindingRepository.class);

    private final DataSource dataSource;

    private static final String TABLE = "proxy_account_binding";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE
            + " (account_id, provider_type, host, port, username, password, exit_ip, city, bound_at, last_used_at, use_count, captcha_passed, deleted) "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0) "
            + " ON CONFLICT (account_id) DO UPDATE SET "
            + " provider_type=EXCLUDED.provider_type, host=EXCLUDED.host, port=EXCLUDED.port, "
            + " username=EXCLUDED.username, password=EXCLUDED.password, exit_ip=EXCLUDED.exit_ip, "
            + " city=EXCLUDED.city, last_used_at=EXCLUDED.last_used_at, use_count=EXCLUDED.use_count, "
            + " captcha_passed=EXCLUDED.captcha_passed, deleted=EXCLUDED.deleted";

    private static final String SELECT_BY_ACCOUNT = "SELECT * FROM " + TABLE + " WHERE account_id = ? AND deleted = 0";
    private static final String SELECT_ALL_ACTIVE = "SELECT * FROM " + TABLE + " WHERE deleted = 0 ORDER BY last_used_at DESC";
    private static final String SELECT_BY_EXIT_IP = "SELECT * FROM " + TABLE + " WHERE exit_ip = ? AND deleted = 0";
    private static final String DELETE_BY_ACCOUNT = "UPDATE " + TABLE + " SET deleted = 1 WHERE account_id = ?";
    private static final String DELETE_EXPIRED = "UPDATE " + TABLE + " SET deleted = 1 WHERE last_used_at < ? AND deleted = 0";
    private static final String COUNT_ACTIVE = "SELECT COUNT(*) FROM " + TABLE + " WHERE deleted = 0";

    public PostgresBindingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureSchema();
    }

    @Override
    public ProxyAccountBinding save(ProxyAccountBinding binding) {
        if (binding == null || binding.getAccountId() == null) {
            throw new IllegalArgumentException("binding.accountId must not be null");
        }

        try (Connection conn = dataSource.getConnection()) {
            upsert(conn, binding);
            return binding;
        } catch (SQLException e) {
            log.error("[BINDING-DB] save failed, accountId={}", binding.getAccountId(), e);
            throw new RuntimeException("保存绑定失败", e);
        }
    }

    @Override
    public Optional<ProxyAccountBinding> findByAccountId(Long accountId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ACCOUNT)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("[BINDING-DB] findByAccountId failed, accountId={}", accountId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ProxyAccountBinding> findAllActive() {
        List<ProxyAccountBinding> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("[BINDING-DB] findAllActive failed", e);
        }
        return result;
    }

    @Override
    public boolean deleteByAccountId(Long accountId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_ACCOUNT)) {
            ps.setLong(1, accountId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("[BINDING-DB] deleteByAccountId failed, accountId={}", accountId, e);
            return false;
        }
    }

    @Override
    public List<ProxyAccountBinding> findByExitIp(String exitIp) {
        List<ProxyAccountBinding> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_EXIT_IP)) {
            ps.setString(1, exitIp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("[BINDING-DB] findByExitIp failed, exitIp={}", exitIp, e);
        }
        return result;
    }

    @Override
    public int deleteExpired(LocalDateTime expireBefore) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_EXPIRED)) {
            ps.setString(1, expireBefore.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[BINDING-DB] deleteExpired failed", e);
            return 0;
        }
    }

    @Override
    public long countActive() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("[BINDING-DB] countActive failed", e);
        }
        return 0;
    }

    private void ensureSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "account_id BIGINT NOT NULL UNIQUE, "
                    + "provider_type VARCHAR(32) NOT NULL, "
                    + "host VARCHAR(256) NOT NULL, "
                    + "port INT NOT NULL, "
                    + "username VARCHAR(256), "
                    + "password VARCHAR(512), "
                    + "exit_ip VARCHAR(64), "
                    + "city VARCHAR(64), "
                    + "bound_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "last_used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "use_count INT DEFAULT 0, "
                    + "captcha_passed BOOLEAN DEFAULT FALSE, "
                    + "deleted INT DEFAULT 0"
                    + ")");
            createIndexIfMissing(conn, "idx_proxy_binding_account", "CREATE INDEX idx_proxy_binding_account ON " + TABLE + "(account_id)");
            createIndexIfMissing(conn, "idx_proxy_binding_exit_ip", "CREATE INDEX idx_proxy_binding_exit_ip ON " + TABLE + "(exit_ip)");
            createIndexIfMissing(conn, "idx_proxy_binding_deleted", "CREATE INDEX idx_proxy_binding_deleted ON " + TABLE + "(deleted)");
        } catch (SQLException e) {
            log.error("[BINDING-DB] ensureSchema failed", e);
            throw new RuntimeException("初始化代理绑定表失败", e);
        }
    }

    private void createIndexIfMissing(Connection conn, String indexName, String createSql) throws SQLException {
        // PostgreSQL 用 pg_indexes 查索引
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = ? AND indexname = ?")) {
            ps.setString(1, TABLE);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute(createSql);
        }
    }

    private void upsert(Connection conn, ProxyAccountBinding b) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setLong(1, b.getAccountId());
            ps.setString(2, b.getProviderType());
            ps.setString(3, b.getHost());
            ps.setInt(4, b.getPort());
            ps.setString(5, b.getUsername());
            ps.setString(6, b.getPassword());
            ps.setString(7, b.getExitIp());
            ps.setString(8, b.getCity());
            ps.setString(9, b.getBoundAt() != null ? b.getBoundAt().toString() : LocalDateTime.now().toString());
            ps.setString(10, b.getLastUsedAt() != null ? b.getLastUsedAt().toString() : LocalDateTime.now().toString());
            ps.setInt(11, b.getUseCount() != null ? b.getUseCount() : 0);
            ps.setBoolean(12, b.getCaptchaPassed() != null && b.getCaptchaPassed());
            ps.executeUpdate();
        }
    }

    private ProxyAccountBinding mapRow(ResultSet rs) throws SQLException {
        return ProxyAccountBinding.builder()
                .id(rs.getLong("id"))
                .accountId(rs.getLong("account_id"))
                .providerType(rs.getString("provider_type"))
                .host(rs.getString("host"))
                .port(rs.getInt("port"))
                .username(rs.getString("username"))
                .password(rs.getString("password"))
                .exitIp(rs.getString("exit_ip"))
                .city(rs.getString("city"))
                .boundAt(parseDateTime(rs.getString("bound_at")))
                .lastUsedAt(parseDateTime(rs.getString("last_used_at")))
                .useCount(rs.getInt("use_count"))
                .captchaPassed(rs.getBoolean("captcha_passed"))
                .deleted(rs.getInt("deleted"))
                .build();
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
