package cn.net.rjnetwork.xianyu.manager.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器服务 — 按账号+服务维度管理熔断状态
 */
@Service
public class CircuitBreakerService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final JdbcTemplate jdbc;
    private final Map<String, CircuitBreaker> cache = new ConcurrentHashMap<>();

    private static final RowMapper<CircuitBreaker> MAPPER = (rs, rowNum) -> {
        CircuitBreaker cb = new CircuitBreaker();
        cb.setId(rs.getLong("id"));
        long acctId = rs.getLong("account_id");
        cb.setAccountId(rs.wasNull() ? null : acctId);
        cb.setServiceName(rs.getString("service_name"));
        cb.setState(rs.getString("state"));
        cb.setFailureCount(rs.getInt("failure_count"));
        cb.setSuccessCount(rs.getInt("success_count"));
        cb.setLastFailureAt(rs.getObject("last_failure_at", LocalDateTime.class));
        cb.setLastFailureMessage(rs.getString("last_failure_message"));
        cb.setLastSuccessAt(rs.getObject("last_success_at", LocalDateTime.class));
        cb.setOpenedAt(rs.getObject("opened_at", LocalDateTime.class));
        cb.setCooldownUntil(rs.getObject("cooldown_until", LocalDateTime.class));
        cb.setThresholdCount(rs.getInt("threshold_count"));
        cb.setCooldownSeconds(rs.getInt("cooldown_seconds"));
        cb.setHalfOpenMaxSuccess(rs.getInt("half_open_max_success"));
        cb.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        cb.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return cb;
    };

    public CircuitBreakerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 检查是否允许执行（熔断器未开闸）
     */
    public boolean allowRequest(Long accountId, String serviceName) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        if (cb.isClosed()) return true;
        if (cb.isOpen()) {
            if (!cb.isInCooldown()) {
                // 冷却结束，进入半开
                cb.setState("HALF_OPEN");
                cb.setSuccessCount(0);
                persist(cb);
                return true;
            }
            return false;
        }
        // HALF_OPEN 允许探测
        return true;
    }

    /**
     * 记录成功
     */
    public void recordSuccess(Long accountId, String serviceName) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        cb.recordSuccess();
        persist(cb);
    }

    /**
     * 记录失败
     */
    public void recordFailure(Long accountId, String serviceName, String message) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        cb.recordFailure(message);
        persist(cb);
        if (cb.isOpen()) {
            logger.warn("Circuit breaker OPENED for account={} service={} after {} failures",
                    accountId, serviceName, cb.getFailureCount());
        }
    }

    /**
     * 重置熔断器
     */
    public void reset(Long accountId, String serviceName) {
        CircuitBreaker cb = getOrCreate(accountId, serviceName);
        cb.reset();
        persist(cb);
    }

    public List<CircuitBreaker> listAll() {
        return jdbc.query("SELECT * FROM circuit_breaker ORDER BY id", MAPPER);
    }

    public CircuitBreaker get(Long accountId, String serviceName) {
        return getOrCreate(accountId, serviceName);
    }

    private CircuitBreaker getOrCreate(Long accountId, String serviceName) {
        String key = (accountId == null ? "GLOBAL" : accountId) + ":" + serviceName;
        return cache.computeIfAbsent(key, k -> {
            List<CircuitBreaker> list;
            if (accountId == null) {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id IS NULL AND service_name = ?",
                        MAPPER, serviceName);
            } else {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id = ? AND service_name = ?",
                        MAPPER, accountId, serviceName);
            }
            if (!list.isEmpty()) return list.get(0);
            // 创建新记录
            CircuitBreaker cb = new CircuitBreaker();
            cb.setAccountId(accountId);
            cb.setServiceName(serviceName);
            cb.setState("CLOSED");
            cb.setThresholdCount(5);
            cb.setCooldownSeconds(300);
            cb.setHalfOpenMaxSuccess(3);
            persist(cb);
            return cb;
        });
    }

    private void persist(CircuitBreaker cb) {
        cb.setUpdatedAt(LocalDateTime.now());
        if (cb.getId() == null) {
            String sql = "INSERT INTO circuit_breaker (account_id, service_name, state, failure_count, success_count, " +
                    "last_failure_at, last_failure_message, last_success_at, opened_at, cooldown_until, " +
                    "threshold_count, cooldown_seconds, half_open_max_success, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbc.update(sql, cb.getAccountId(), cb.getServiceName(), cb.getState(),
                    cb.getFailureCount(), cb.getSuccessCount(),
                    cb.getLastFailureAt(), cb.getLastFailureMessage(), cb.getLastSuccessAt(),
                    cb.getOpenedAt(), cb.getCooldownUntil(),
                    cb.getThresholdCount(), cb.getCooldownSeconds(), cb.getHalfOpenMaxSuccess(),
                    LocalDateTime.now(), cb.getUpdatedAt());
            // 重新查询获取 id
            List<CircuitBreaker> list;
            if (cb.getAccountId() == null) {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id IS NULL AND service_name = ? ORDER BY id DESC LIMIT 1",
                        MAPPER, cb.getServiceName());
            } else {
                list = jdbc.query("SELECT * FROM circuit_breaker WHERE account_id = ? AND service_name = ? ORDER BY id DESC LIMIT 1",
                        MAPPER, cb.getAccountId(), cb.getServiceName());
            }
            if (!list.isEmpty()) cb.setId(list.get(0).getId());
        } else {
            String sql = "UPDATE circuit_breaker SET state=?, failure_count=?, success_count=?, " +
                    "last_failure_at=?, last_failure_message=?, last_success_at=?, opened_at=?, cooldown_until=?, " +
                    "threshold_count=?, cooldown_seconds=?, half_open_max_success=?, updated_at=? WHERE id=?";
            jdbc.update(sql, cb.getState(), cb.getFailureCount(), cb.getSuccessCount(),
                    cb.getLastFailureAt(), cb.getLastFailureMessage(), cb.getLastSuccessAt(),
                    cb.getOpenedAt(), cb.getCooldownUntil(),
                    cb.getThresholdCount(), cb.getCooldownSeconds(), cb.getHalfOpenMaxSuccess(),
                    cb.getUpdatedAt(), cb.getId());
        }
        // 记录事件
        String evtSql = "INSERT INTO circuit_breaker_event (breaker_id, event_type, message, created_at) VALUES (?, ?, ?, ?)";
        jdbc.update(evtSql, cb.getId(), "STATE_CHANGE",
                "state=" + cb.getState() + " failures=" + cb.getFailureCount(), LocalDateTime.now());
    }
}
