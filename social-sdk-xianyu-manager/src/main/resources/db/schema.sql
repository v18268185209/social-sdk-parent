-- 闲鱼多账号管理平台 - SQLite 数据库初始化脚本

-- 管理员用户表
CREATE TABLE IF NOT EXISTS admin_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    display_name VARCHAR(128),
    email VARCHAR(128),
    phone VARCHAR(32),
    role_level INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 闲鱼账号表
CREATE TABLE IF NOT EXISTS xianyu_account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_name VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    display_name VARCHAR(128),
    cookie_header TEXT,
    cookies_json TEXT,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    remark VARCHAR(256),
    last_error VARCHAR(512),
    last_login_at DATETIME,
    cookie_expires_at DATETIME,
    -- 个人信息（从闲鱼 API 获取）
    avatar VARCHAR(512),
    introduction TEXT,
    ip_location VARCHAR(64),
    followers INTEGER DEFAULT 0,
    following INTEGER DEFAULT 0,
    sold_count INTEGER DEFAULT 0,
    purchase_count INTEGER DEFAULT 0,
    collection_count INTEGER DEFAULT 0,
    on_sale_count INTEGER DEFAULT 0,
    shop_level VARCHAR(32),
    credit_score INTEGER DEFAULT 0,
    review_num INTEGER DEFAULT 0,
    profile_synced_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 商品表
CREATE TABLE IF NOT EXISTS xianyu_product (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    item_id VARCHAR(64),
    title VARCHAR(256) NOT NULL,
    price REAL,
    original_price REAL,
    stock INTEGER DEFAULT 0,
    status VARCHAR(16) DEFAULT 'DRAFT',
    category_id VARCHAR(64),
    images TEXT,
    description TEXT,
    videos TEXT, -- 视频 URL 的 JSON 数组
    detail_url VARCHAR(512),
    view_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 消息表
CREATE TABLE IF NOT EXISTS xianyu_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    sender_id VARCHAR(64),
    sender_name VARCHAR(128),
    content TEXT,
    msg_type VARCHAR(16) DEFAULT 'TEXT',
    direction VARCHAR(8) DEFAULT 'INCOMING',
    auto_reply BOOLEAN DEFAULT FALSE,
    message_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 订单表
CREATE TABLE IF NOT EXISTS xianyu_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    type VARCHAR(16) DEFAULT 'BOUGHT', -- SOLD, BOUGHT
    order_id VARCHAR(64),
    item_title VARCHAR(256),
    counterparty_name VARCHAR(128), -- 买方名字(bought)或卖方名字(sold)
    amount REAL,
    status VARCHAR(32) DEFAULT 'PENDING',
    tracking_no VARCHAR(64),
    order_time DATETIME, -- 订单创建时间(来自闲鱼 API)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 关键词规则表
CREATE TABLE IF NOT EXISTS xianyu_keyword_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    rule_name VARCHAR(128),
    keyword VARCHAR(256),
    match_type VARCHAR(16) DEFAULT 'CONTAINS',
    reply_text TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operator_id INTEGER,
    operator_name VARCHAR(128),
    action VARCHAR(256),
    resource_type VARCHAR(64),
    resource_id VARCHAR(64),
    detail TEXT,
    ip_address VARCHAR(64),
    action_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 钱包表
CREATE TABLE IF NOT EXISTS xianyu_wallet (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE,
    balance REAL DEFAULT 0,
    frozen_amount REAL DEFAULT 0,
    alipay_account VARCHAR(128),
    bank_card VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 钱包交易记录表
CREATE TABLE IF NOT EXISTS xianyu_wallet_transaction (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    transaction_id VARCHAR(64),
    type VARCHAR(16) DEFAULT 'EXPENSE',
    amount REAL,
    balance_after REAL,
    description TEXT,
    transaction_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 收藏关注表
CREATE TABLE IF NOT EXISTS xianyu_collect (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    target_type VARCHAR(16) DEFAULT 'ITEM',
    target_id VARCHAR(64),
    target_name VARCHAR(256),
    collected_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_xianyu_product_account ON xianyu_product(account_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_product_status ON xianyu_product(status);
CREATE INDEX IF NOT EXISTS idx_xianyu_message_session ON xianyu_message(account_id, session_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_account ON xianyu_order(account_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_keyword_rule_account ON xianyu_keyword_rule(account_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_xianyu_wallet_account ON xianyu_wallet(account_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_wallet_transaction_account ON xianyu_wallet_transaction(account_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_collect_account ON xianyu_collect(account_id);
