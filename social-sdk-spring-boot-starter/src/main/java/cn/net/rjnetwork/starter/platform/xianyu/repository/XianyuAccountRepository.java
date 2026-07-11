package cn.net.rjnetwork.starter.platform.xianyu.repository;

import cn.net.rjnetwork.starter.platform.xianyu.model.XianyuAccountEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class XianyuAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<XianyuAccountEntity> rowMapper = (rs, rowNum) -> {
        XianyuAccountEntity entity = new XianyuAccountEntity();
        entity.setId(rs.getLong("id"));
        entity.setPlatform(rs.getString("platform"));
        entity.setAccountName(rs.getString("account_name"));
        entity.setUserId(rs.getString("user_id"));
        entity.setDisplayName(rs.getString("display_name"));
        entity.setCookieHeader(rs.getString("cookie_header"));
        entity.setCookiesJson(rs.getString("cookies_json"));
        entity.setSessionRawData(rs.getString("session_raw_data"));
        entity.setStatus(rs.getString("status"));
        entity.setRemark(rs.getString("remark"));
        entity.setLastError(rs.getString("last_error"));
        entity.setLastLoginAt(SqliteTime.parse(rs.getString("last_login_at")));
        entity.setCreatedAt(SqliteTime.parse(rs.getString("created_at")));
        entity.setUpdatedAt(SqliteTime.parse(rs.getString("updated_at")));
        return entity;
    };

    public XianyuAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<XianyuAccountEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM xianyu_accounts ORDER BY updated_at DESC, id DESC",
                rowMapper);
    }

    public Optional<XianyuAccountEntity> findById(long id) {
        try {
            XianyuAccountEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM xianyu_accounts WHERE id = ?",
                    rowMapper,
                    id);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<XianyuAccountEntity> findFirstByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            XianyuAccountEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM xianyu_accounts WHERE user_id = ? ORDER BY updated_at DESC, id DESC LIMIT 1",
                    rowMapper,
                    userId.trim());
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<XianyuAccountEntity> findFirstByAccountName(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            return Optional.empty();
        }
        try {
            XianyuAccountEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM xianyu_accounts WHERE account_name = ? ORDER BY updated_at DESC, id DESC LIMIT 1",
                    rowMapper,
                    accountName.trim());
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public long insert(XianyuAccountEntity entity) {
        String now = SqliteTime.nowText();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO xianyu_accounts("
                            + "platform, account_name, user_id, display_name, cookie_header, cookies_json, "
                            + "session_raw_data, status, remark, last_error, last_login_at, created_at, updated_at"
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entity.getPlatform());
            ps.setString(2, entity.getAccountName());
            ps.setString(3, entity.getUserId());
            ps.setString(4, entity.getDisplayName());
            ps.setString(5, entity.getCookieHeader());
            ps.setString(6, entity.getCookiesJson());
            ps.setString(7, entity.getSessionRawData());
            ps.setString(8, entity.getStatus());
            ps.setString(9, entity.getRemark());
            ps.setString(10, entity.getLastError());
            ps.setString(11, entity.getLastLoginAt() == null ? null : entity.getLastLoginAt().toString());
            ps.setString(12, now);
            ps.setString(13, now);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public void update(XianyuAccountEntity entity) {
        String now = SqliteTime.nowText();
        jdbcTemplate.update(
                "UPDATE xianyu_accounts SET "
                        + "account_name=?, user_id=?, display_name=?, cookie_header=?, cookies_json=?, "
                        + "session_raw_data=?, status=?, remark=?, last_error=?, last_login_at=?, updated_at=? "
                        + "WHERE id=?",
                entity.getAccountName(),
                entity.getUserId(),
                entity.getDisplayName(),
                entity.getCookieHeader(),
                entity.getCookiesJson(),
                entity.getSessionRawData(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getLastError(),
                entity.getLastLoginAt() == null ? null : entity.getLastLoginAt().toString(),
                now,
                entity.getId());
    }

    public boolean deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM xianyu_accounts WHERE id = ?", id) > 0;
    }
}
