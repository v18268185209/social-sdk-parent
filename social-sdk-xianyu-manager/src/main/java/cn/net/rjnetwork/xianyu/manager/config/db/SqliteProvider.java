package cn.net.rjnetwork.xianyu.manager.config.db;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * SQLite 方言实现 — 默认 profile。
 * <p>连接池单连接最优（SQLite 文件锁限制），PRAGMA 注入 WAL/synchronous/cache_size/busy_timeout。</p>
 */
@Component
public class SqliteProvider implements DatabaseProvider {

    private static final String[] PRAGMA = {
            "PRAGMA journal_mode=WAL",
            "PRAGMA synchronous=NORMAL",
            "PRAGMA busy_timeout=30000",
            "PRAGMA cache_size=-16000",
            "PRAGMA mmap_size=268435456",
            "PRAGMA temp_store=MEMORY",
            "PRAGMA foreign_keys=ON"
    };

    @Override public String dialect() { return "sqlite"; }
    @Override public String schemaFile() { return "db/schema-sqlite.sql"; }
    @Override public String[] connectionInitSqls() { return Arrays.copyOf(PRAGMA, PRAGMA.length); }
    @Override public int maxActive() { return 1; }
    @Override public String validationQuery() { return "SELECT 1"; }
    @Override public boolean supportsUpsert() { return true; }

    @PostConstruct
    void register() { DatabaseProviderHolder.INSTANCE = this; }
}
