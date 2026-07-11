package cn.net.rjnetwork.starter.platform.xianyu.repository;

import cn.net.rjnetwork.starter.platform.xianyu.model.XianyuKeywordRuleEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class XianyuKeywordRuleRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<XianyuKeywordRuleEntity> rowMapper = (rs, rowNum) -> {
        XianyuKeywordRuleEntity entity = new XianyuKeywordRuleEntity();
        entity.setId(rs.getLong("id"));
        long accountId = rs.getLong("account_id");
        entity.setAccountId(rs.wasNull() ? null : accountId);
        entity.setRuleName(rs.getString("rule_name"));
        entity.setKeyword(rs.getString("keyword"));
        entity.setMatchType(rs.getString("match_type"));
        entity.setReplyText(rs.getString("reply_text"));
        entity.setEnabled(rs.getInt("enabled") == 1);
        entity.setPriority(rs.getInt("priority"));
        entity.setCreatedAt(SqliteTime.parse(rs.getString("created_at")));
        entity.setUpdatedAt(SqliteTime.parse(rs.getString("updated_at")));
        return entity;
    };

    public XianyuKeywordRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<XianyuKeywordRuleEntity> findByAccountId(Long accountId) {
        if (accountId == null) {
            return jdbcTemplate.query(
                    "SELECT * FROM xianyu_keyword_rules ORDER BY priority ASC, id DESC",
                    rowMapper);
        }
        return jdbcTemplate.query(
                "SELECT * FROM xianyu_keyword_rules WHERE account_id = ? OR account_id IS NULL "
                        + "ORDER BY priority ASC, id DESC",
                rowMapper,
                accountId);
    }

    public Optional<XianyuKeywordRuleEntity> findById(long id) {
        try {
            XianyuKeywordRuleEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM xianyu_keyword_rules WHERE id = ?",
                    rowMapper,
                    id);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public long insert(XianyuKeywordRuleEntity entity) {
        String now = SqliteTime.nowText();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO xianyu_keyword_rules("
                            + "account_id, rule_name, keyword, match_type, reply_text, enabled, priority, created_at, updated_at"
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (entity.getAccountId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, entity.getAccountId());
            }
            ps.setString(2, entity.getRuleName());
            ps.setString(3, entity.getKeyword());
            ps.setString(4, entity.getMatchType());
            ps.setString(5, entity.getReplyText());
            ps.setInt(6, entity.isEnabled() ? 1 : 0);
            ps.setInt(7, entity.getPriority());
            ps.setString(8, now);
            ps.setString(9, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public void update(XianyuKeywordRuleEntity entity) {
        jdbcTemplate.update(
                "UPDATE xianyu_keyword_rules SET account_id=?, rule_name=?, keyword=?, match_type=?, reply_text=?, "
                        + "enabled=?, priority=?, updated_at=? WHERE id=?",
                entity.getAccountId(),
                entity.getRuleName(),
                entity.getKeyword(),
                entity.getMatchType(),
                entity.getReplyText(),
                entity.isEnabled() ? 1 : 0,
                entity.getPriority(),
                SqliteTime.nowText(),
                entity.getId());
    }

    public boolean deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM xianyu_keyword_rules WHERE id = ?", id) > 0;
    }
}
