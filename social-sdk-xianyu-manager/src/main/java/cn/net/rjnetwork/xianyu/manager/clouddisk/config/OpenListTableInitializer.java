package cn.net.rjnetwork.xianyu.manager.clouddisk.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OpenList 数据库表初始化——确保 openlist_instance 表存在。
 * 解决已有数据库缺失该表的问题，新部署自动创建。
 */
@Component
public class OpenListTableInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            // 检查表是否存在
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='openlist_instance'",
                Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE openlist_instance (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  port INTEGER NOT NULL DEFAULT 5244," +
                    "  url VARCHAR(256) DEFAULT 'http://127.0.0.1:5244'," +
                    "  data_dir VARCHAR(512)," +
                    "  initial_username VARCHAR(128)," +
                    "  initial_password VARCHAR(128)," +
                    "  install_path VARCHAR(512)," +
                    "  os_name VARCHAR(32)," +
                    "  arch VARCHAR(16)," +
                    "  installed INTEGER DEFAULT 0," +
                    "  running INTEGER DEFAULT 0," +
                    "  first_started_at DATETIME," +
                    "  last_started_at DATETIME," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );
                System.out.println("[OpenList] 已自动创建 openlist_instance 表");
            }
        } catch (Exception e) {
            System.err.println("[OpenList] 初始化表失败: " + e.getMessage());
        }
    }
}
