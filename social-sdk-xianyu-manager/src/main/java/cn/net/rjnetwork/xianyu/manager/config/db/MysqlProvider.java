package cn.net.rjnetwork.xianyu.manager.config.db;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * MySQL8 方言实现 — profile=mysql。
 * <p>连接池可并发（maxActive=20），SET 注入 utf8mb4 + 严格 SQL 模式 + 时区。</p>
 */
@Component
@Profile("mysql")
public class MysqlProvider implements DatabaseProvider {

    private static final String[] INIT = {
            "SET NAMES utf8mb4",
            "SET sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION'",
            "SET time_zone='+08:00'"
    };

    @Override public String dialect() { return "mysql"; }
    @Override public String schemaFile() { return "db/schema-mysql.sql"; }
    @Override public String[] connectionInitSqls() { return Arrays.copyOf(INIT, INIT.length); }
    @Override public int maxActive() { return 20; }
    @Override public String validationQuery() { return "SELECT 1"; }
    @Override public boolean supportsUpsert() { return true; }

    @PostConstruct
    void register() { DatabaseProviderHolder.INSTANCE = this; }
}
