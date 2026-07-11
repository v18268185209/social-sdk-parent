package com.socialsdk.starter.platform.xianyu.repository;

import com.socialsdk.starter.platform.xianyu.model.XianyuLoginSnapshotEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

public class XianyuLoginSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<XianyuLoginSnapshotEntity> rowMapper = (rs, rowNum) -> {
        XianyuLoginSnapshotEntity entity = new XianyuLoginSnapshotEntity();
        entity.setId(rs.getLong("id"));
        entity.setAccountId(rs.getLong("account_id"));
        entity.setSessionId(rs.getString("session_id"));
        entity.setLoginMode(rs.getString("login_mode"));
        entity.setUserId(rs.getString("user_id"));
        entity.setCookieHeader(rs.getString("cookie_header"));
        entity.setCookiesJson(rs.getString("cookies_json"));
        entity.setLocalStorageJson(rs.getString("local_storage_json"));
        entity.setSessionStorageJson(rs.getString("session_storage_json"));
        entity.setIndexedDbJson(rs.getString("indexeddb_json"));
        entity.setCacheStorageJson(rs.getString("cache_storage_json"));
        entity.setCurrentUrl(rs.getString("current_url"));
        entity.setUserAgent(rs.getString("user_agent"));
        entity.setCapturedAt(SqliteTime.parse(rs.getString("captured_at")));
        entity.setCreatedAt(SqliteTime.parse(rs.getString("created_at")));
        return entity;
    };

    public XianyuLoginSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(XianyuLoginSnapshotEntity entity) {
        String now = SqliteTime.nowText();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO xianyu_login_snapshots("
                            + "account_id, session_id, login_mode, user_id, cookie_header, cookies_json, "
                            + "local_storage_json, session_storage_json, indexeddb_json, cache_storage_json, "
                            + "current_url, user_agent, captured_at, created_at"
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, entity.getAccountId());
            ps.setString(2, entity.getSessionId());
            ps.setString(3, entity.getLoginMode());
            ps.setString(4, entity.getUserId());
            ps.setString(5, entity.getCookieHeader());
            ps.setString(6, entity.getCookiesJson());
            ps.setString(7, entity.getLocalStorageJson());
            ps.setString(8, entity.getSessionStorageJson());
            ps.setString(9, entity.getIndexedDbJson());
            ps.setString(10, entity.getCacheStorageJson());
            ps.setString(11, entity.getCurrentUrl());
            ps.setString(12, entity.getUserAgent());
            ps.setString(13, entity.getCapturedAt() == null ? now : entity.getCapturedAt().toString());
            ps.setString(14, now);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public Optional<XianyuLoginSnapshotEntity> findById(long id) {
        try {
            XianyuLoginSnapshotEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM xianyu_login_snapshots WHERE id = ?",
                    rowMapper,
                    id);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
