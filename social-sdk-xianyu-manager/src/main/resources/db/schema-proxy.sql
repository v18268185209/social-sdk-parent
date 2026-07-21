-- 代理池配置表 — 服务端落库
-- 一条记录 = 一个供应商配置 或 全局配置(global)
-- 为什么不存单条 JSON？ 因为 UI 要逐供应商编辑、启停、查余额，行级存储更直观

CREATE TABLE IF NOT EXISTS proxy_config (
    id INTEGER PRIMARY KEY,
    provider_type VARCHAR(32) NOT NULL,              -- 'global' / 'abuyun' / 'qg_tunnel' / 'qg_short_lived' / 'kuaidaili_tunnel' / 'kuaidaili_private' / 'smartproxy'
    config_json TEXT NOT NULL,                       -- JSON 序列化的该供应商配置
    enabled INTEGER DEFAULT 1,                       -- 0=禁用 1=启用
    sort_order INTEGER DEFAULT 0,                    -- 数字越小优先级越高
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 唯一约束：每个供应商类型只允许一条配置
CREATE UNIQUE INDEX IF NOT EXISTS idx_proxy_config_type ON proxy_config(provider_type);
