-- 代理池持久化 schema（与应用库同库，表名前缀 proxy_ 避免冲突）
-- 存放账号-IP 绑定、冷名单、代理审计日志

-- 闲鱼账号 ↔ 代理 IP 绑定表（业务主键：account_id）
CREATE TABLE IF NOT EXISTS proxy_account_binding (
    id              INTEGER PRIMARY KEY,
    account_id      INTEGER NOT NULL UNIQUE,
    provider_type   VARCHAR(32)     NOT NULL,
    host            VARCHAR(256)    NOT NULL,
    port            INTEGER         NOT NULL,
    username        VARCHAR(256),
    password        VARCHAR(512),
    exit_ip         VARCHAR(64),
    city            VARCHAR(64),
    bound_at        DATETIME        DEFAULT CURRENT_TIMESTAMP,
    last_used_at    DATETIME        DEFAULT CURRENT_TIMESTAMP,
    use_count       INTEGER         DEFAULT 0,
    captcha_passed  BOOLEAN         DEFAULT FALSE,
    deleted         INTEGER         DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_proxy_binding_account ON proxy_account_binding(account_id);
CREATE INDEX IF NOT EXISTS idx_proxy_binding_exit_ip ON proxy_account_binding(exit_ip);
CREATE INDEX IF NOT EXISTS idx_proxy_binding_deleted ON proxy_account_binding(deleted);

-- 代理 IP 冷名单表（IP 段 / IP 维度，供跨进程共享冷名单用）
CREATE TABLE IF NOT EXISTS proxy_cool_down (
    id                  INTEGER PRIMARY KEY,
    ip                  VARCHAR(64)     NOT NULL,
    provider_type       VARCHAR(32)     NOT NULL,
    consecutive_fail INTEGER         NOT NULL DEFAULT 0,
    reason              VARCHAR(512),
    cooled_down_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    recover_at          DATETIME,
    deleted             INTEGER         DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_proxy_cooldown_ip ON proxy_cool_down(ip, deleted);
CREATE INDEX IF NOT EXISTS idx_proxy_cooldown_recover ON proxy_cool_down(recover_at, deleted);

-- 代理操作审计日志（可选，用于排查 / 计费对账）
CREATE TABLE IF NOT EXISTS proxy_audit_log (
    id              INTEGER PRIMARY KEY,
    account_id      INTEGER,
    action          VARCHAR(32)     NOT NULL,   -- BIND / UNBIND / ACQUIRE / RELEASE / MARK_FAILURE / HEALTH_CHECK
    provider_type   VARCHAR(32),
    host            VARCHAR(256),
    port            INTEGER,
    exit_ip         VARCHAR(64),
    detail          VARCHAR(1024),
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_proxy_audit_account ON proxy_audit_log(account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_proxy_audit_action ON proxy_audit_log(action, created_at);
