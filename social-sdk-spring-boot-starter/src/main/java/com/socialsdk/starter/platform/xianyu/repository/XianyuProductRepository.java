package com.socialsdk.starter.platform.xianyu.repository;

import com.socialsdk.starter.platform.xianyu.model.XianyuProductEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class XianyuProductRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<XianyuProductEntity> rowMapper = (rs, rowNum) -> {
        XianyuProductEntity entity = new XianyuProductEntity();
        entity.setId(rs.getLong("id"));
        entity.setAccountId(rs.getLong("account_id"));
        entity.setItemId(rs.getString("item_id"));
        entity.setTitle(rs.getString("title"));
        double price = rs.getDouble("price");
        entity.setPrice(rs.wasNull() ? null : price);
        int stock = rs.getInt("stock");
        entity.setStock(rs.wasNull() ? null : stock);
        entity.setStatus(rs.getString("status"));
        entity.setDetailUrl(rs.getString("detail_url"));
        entity.setDescription(rs.getString("description"));
        entity.setCreatedAt(SqliteTime.parse(rs.getString("created_at")));
        entity.setUpdatedAt(SqliteTime.parse(rs.getString("updated_at")));
        return entity;
    };

    public XianyuProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<XianyuProductEntity> findByAccountId(Long accountId) {
        if (accountId == null) {
            return jdbcTemplate.query("SELECT * FROM xianyu_products ORDER BY id DESC", rowMapper);
        }
        return jdbcTemplate.query(
                "SELECT * FROM xianyu_products WHERE account_id = ? ORDER BY id DESC",
                rowMapper,
                accountId);
    }

    public Optional<XianyuProductEntity> findById(long id) {
        try {
            XianyuProductEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM xianyu_products WHERE id = ?",
                    rowMapper,
                    id);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public long insert(XianyuProductEntity entity) {
        String now = SqliteTime.nowText();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO xianyu_products("
                            + "account_id, item_id, title, price, stock, status, detail_url, description, created_at, updated_at"
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, entity.getAccountId());
            ps.setString(2, entity.getItemId());
            ps.setString(3, entity.getTitle());
            if (entity.getPrice() == null) {
                ps.setObject(4, null);
            } else {
                ps.setDouble(4, entity.getPrice());
            }
            if (entity.getStock() == null) {
                ps.setObject(5, null);
            } else {
                ps.setInt(5, entity.getStock());
            }
            ps.setString(6, entity.getStatus());
            ps.setString(7, entity.getDetailUrl());
            ps.setString(8, entity.getDescription());
            ps.setString(9, now);
            ps.setString(10, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public void update(XianyuProductEntity entity) {
        jdbcTemplate.update(
                "UPDATE xianyu_products SET account_id=?, item_id=?, title=?, price=?, stock=?, status=?, "
                        + "detail_url=?, description=?, updated_at=? WHERE id=?",
                entity.getAccountId(),
                entity.getItemId(),
                entity.getTitle(),
                entity.getPrice(),
                entity.getStock(),
                entity.getStatus(),
                entity.getDetailUrl(),
                entity.getDescription(),
                SqliteTime.nowText(),
                entity.getId());
    }

    public boolean deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM xianyu_products WHERE id = ?", id) > 0;
    }
}
