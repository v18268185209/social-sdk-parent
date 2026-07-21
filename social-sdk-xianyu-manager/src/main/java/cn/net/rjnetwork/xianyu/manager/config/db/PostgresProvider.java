package cn.net.rjnetwork.xianyu.manager.config.db;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * PostgreSQL 方言实现 — profile=postgres。
 * <p>连接池可并发（maxActive=20），SET 注入标准_conforming_strings + 时区。</p>
 */
@Component
@Profile("postgres")
public class PostgresProvider implements DatabaseProvider {

    private static final String[] INIT = {
            "SET standard_conforming_strings = ON",
            "SET timezone = 'Asia/Shanghai'",
            "SET client_encoding = 'UTF8'"
    };

    @Override public String dialect() { return "postgres"; }
    @Override public String schemaFile() { return "db/schema-postgres.sql"; }
    @Override public String[] connectionInitSqls() { return Arrays.copyOf(INIT, INIT.length); }
    @Override public int maxActive() { return 20; }
    @Override public String validationQuery() { return "SELECT 1"; }
    @Override public boolean supportsUpsert() { return true; }

    @PostConstruct
    void register() { DatabaseProviderHolder.INSTANCE = this; }
}
