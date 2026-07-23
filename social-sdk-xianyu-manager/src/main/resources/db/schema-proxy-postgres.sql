-- 代理池 schema（PostgreSQL 方言） — 含 proxy_config + 绑定/冷名单/审计表
-- 由 DatabaseInitializer.executeProxySchema() 在 postgres profile 下加载
-- 与 SQLite schema-proxy.sql + proxy-bindings.sql 对齐，但用 PG 16 兼容语法

-- ============================================================================
-- proxy_config：代理池配置表（服务端落库）
-- ============================================================================
CREATE TABLE IF NOT EXISTS proxy_config (
    id BIGSERIAL PRIMARY KEY,
    provider_type VARCHAR(32) NOT NULL,              -- 'global' / 'abuyun' / 'qg_tunnel' / 'qg_short_lived' / 'kuaidaili_tunnel' / 'kuaidaili_private' / 'smartproxy'
    config_json TEXT NOT NULL,                       -- JSON 序列化的该供应商配置
    enabled INTEGER DEFAULT 1,                       -- 0=禁用 1=启用
    sort_order INTEGER DEFAULT 0,                    -- 数字越小优先级越高
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_type)                           -- 每个供应商类型只允许一条配置
);

-- ============================================================================
-- proxy_account_binding：闲鱼账号 ↔ 代理 IP 绑定表（业务主键：account_id）
-- ============================================================================
CREATE TABLE IF NOT EXISTS proxy_account_binding (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL UNIQUE,
    provider_type   VARCHAR(32) NOT NULL,
    host            VARCHAR(256) NOT NULL,
    port            INTEGER NOT NULL,
    username        VARCHAR(256),
    password        VARCHAR(512),
    exit_ip         VARCHAR(64),
    city            VARCHAR(64),
    bound_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    use_count       INTEGER DEFAULT 0,
    captcha_passed  BOOLEAN DEFAULT FALSE,
    deleted         INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_proxy_binding_account ON proxy_account_binding(account_id);
CREATE INDEX IF NOT EXISTS idx_proxy_binding_exit_ip ON proxy_account_binding(exit_ip);
CREATE INDEX IF NOT EXISTS idx_proxy_binding_deleted ON proxy_account_binding(deleted);

-- ============================================================================
-- proxy_cool_down：代理 IP 冷名单表（IP 段 / IP 维度，跨进程共享）
-- ============================================================================
CREATE TABLE IF NOT EXISTS proxy_cool_down (
    id                  BIGSERIAL PRIMARY KEY,
    ip                  VARCHAR(64) NOT NULL,
    provider_type       VARCHAR(32) NOT NULL,
    consecutive_fail    INTEGER NOT NULL DEFAULT 0,
    reason              VARCHAR(512),
    cooled_down_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    recover_at          TIMESTAMP,
    deleted             INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_proxy_cooldown_ip ON proxy_cool_down(ip, deleted);
CREATE INDEX IF NOT EXISTS idx_proxy_cooldown_recover ON proxy_cool_down(recover_at, deleted);

-- ============================================================================
-- proxy_audit_log：代理操作审计日志（可选，用于排查 / 计费对账）
-- ============================================================================
CREATE TABLE IF NOT EXISTS proxy_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT,
    action          VARCHAR(32) NOT NULL,   -- BIND / UNBIND / ACQUIRE / RELEASE / MARK_FAILURE / HEALTH_CHECK
    provider_type   VARCHAR(32),
    host            VARCHAR(256),
    port            INTEGER,
    exit_ip         VARCHAR(64),
    detail          VARCHAR(1024),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_proxy_audit_account ON proxy_audit_log(account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_proxy_audit_action ON proxy_audit_log(action, created_at);
