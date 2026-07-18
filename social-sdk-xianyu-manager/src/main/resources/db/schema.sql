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
    goods_type VARCHAR(16) DEFAULT 'PHYSICAL', -- PHYSICAL / VIRTUAL
    deliver_type VARCHAR(16), -- CARD / ACCOUNT / LINK / FILE (虚拟商品用)
    deliver_content_template TEXT, -- 发货内容模板(虚拟商品用)
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

-- 关键词/自动回复规则表
CREATE TABLE IF NOT EXISTS xianyu_keyword_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    rule_name VARCHAR(128),
    reply_type VARCHAR(16) DEFAULT 'KEYWORD', -- KEYWORD, AI, AUTO
    keyword VARCHAR(256),
    match_type VARCHAR(16) DEFAULT 'CONTAINS',
    reply_text TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 自动回复全局配置表（按账号）
CREATE TABLE IF NOT EXISTS xianyu_auto_reply_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE,
    -- AI 配置
    ai_enabled BOOLEAN DEFAULT FALSE,
    ai_provider VARCHAR(32),
    ai_api_key VARCHAR(512),
    api_url VARCHAR(512),
    ai_model VARCHAR(64),
    ai_system_prompt TEXT,
    ai_temperature REAL DEFAULT 0.7,
    -- 兜底自动回复
    auto_reply_enabled BOOLEAN DEFAULT FALSE,
    welcome_message TEXT,
    fallback_reply TEXT,
    idle_timeout_minutes INTEGER DEFAULT 30,
    idle_reply TEXT,
    offline_reply_enabled BOOLEAN DEFAULT FALSE,
    offline_reply TEXT,
    -- 全局配置
    notify_on_new_message BOOLEAN DEFAULT TRUE,
    include_chat_history BOOLEAN DEFAULT TRUE,
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
    available_balance REAL DEFAULT 0,
    total_assets REAL DEFAULT 0,
    withdrawable_amount REAL DEFAULT 0,
    alipay_account VARCHAR(128),
    alipay_real_name VARCHAR(64),
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
    biz_type VARCHAR(32),
    amount REAL,
    balance_after REAL,
    description TEXT,
    status VARCHAR(16),
    trade_no VARCHAR(64),
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

-- ======================== AI 模块 ========================

-- AI 厂商表（OpenAI 兼容协议：api_base_url + api_key 即可接入）
CREATE TABLE IF NOT EXISTS ai_provider (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(64) NOT NULL UNIQUE,           -- 展示名（如 Agnes AI / OpenAI / DeepSeek）
    api_base_url VARCHAR(256) NOT NULL,          -- API 端点（如 https://apihub.agnes-ai.com/v1）
    api_key VARCHAR(512) NOT NULL,               -- API Key（明文存储，生产可加对称加密）
    provider_type VARCHAR(32) DEFAULT 'OPENAI_COMPATIBLE',
    enabled BOOLEAN DEFAULT TRUE,
    remark VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- AI 模型表
CREATE TABLE IF NOT EXISTS ai_model (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id INTEGER NOT NULL,
    model_name VARCHAR(128) NOT NULL,            -- 模型标识（如 agnes-2.0-flash）
    display_name VARCHAR(128),                   -- 展示名
    model_type VARCHAR(16) NOT NULL,             -- TEXT / IMAGE / VIDEO
    capabilities TEXT,                           -- JSON 能力标签（streaming / tools / thinking / image_input）
    default_temperature REAL DEFAULT 0.7,
    default_max_tokens INTEGER DEFAULT 1024,
    enabled BOOLEAN DEFAULT TRUE,
    remark VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (provider_id) REFERENCES ai_provider(id),
    UNIQUE (provider_id, model_name)
);

CREATE INDEX IF NOT EXISTS idx_ai_model_provider ON ai_model(provider_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_type ON ai_model(model_type);

-- ======================== 通知模块 ========================
-- 通知通道（邮件 SMTP / Webhook 机器人）。config_json 密文存储敏感配置。
CREATE TABLE IF NOT EXISTS notify_channel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type VARCHAR(16) NOT NULL,            -- EMAIL, WEBHOOK
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    config_json TEXT,                     -- AES 加密后的 JSON 配置
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 通知模板（按场景）。场景常量见 NotifyScenario。
CREATE TABLE IF NOT EXISTS notify_template (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scenario VARCHAR(64) NOT NULL UNIQUE,
    title_tpl TEXT,
    body_tpl TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 订阅规则：场景 -> 通道 + 接收范围
CREATE TABLE IF NOT EXISTS notify_subscription (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scenario VARCHAR(64) NOT NULL,
    channel_id INTEGER NOT NULL,
    recipient_scope VARCHAR(16) DEFAULT 'ALL',  -- ALL / CUSTOM
    recipients TEXT,                            -- CUSTOM 时的接收人（逗号分隔/JSON）
    account_scope VARCHAR(16) DEFAULT 'ALL',    -- ALL / CUSTOM
    account_ids TEXT,                           -- CUSTOM 时的账号 ID 列表（JSON 数组）
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 投递日志
CREATE TABLE IF NOT EXISTS notify_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scenario VARCHAR(64),
    channel_id INTEGER,
    channel_type VARCHAR(16),
    recipient VARCHAR(256),
    status VARCHAR(16),             -- SENT / FAILED
    payload TEXT,
    error TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME
);

-- 站内通知收件箱
CREATE TABLE IF NOT EXISTS notify_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,             -- 关联的闲鱼账号（可为空）
    scenario VARCHAR(64),
    title VARCHAR(256),
    content TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notify_sub_scenario ON notify_subscription(scenario);
CREATE INDEX IF NOT EXISTS idx_notify_log_scenario ON notify_log(scenario);
CREATE INDEX IF NOT EXISTS idx_notify_msg_read ON notify_message(is_read);
CREATE INDEX IF NOT EXISTS idx_notify_msg_created ON notify_message(created_at);

-- ======================== 虚拟商品 / 自动发货 ========================

-- 给 xianyu_order 加虚拟发货/自动收货字段
ALTER TABLE xianyu_order ADD COLUMN goods_type VARCHAR(16) DEFAULT 'PHYSICAL';
ALTER TABLE xianyu_order ADD COLUMN require_virtual_ship BOOLEAN DEFAULT FALSE;
ALTER TABLE xianyu_order ADD COLUMN virtual_shipped_at DATETIME;
ALTER TABLE xianyu_order ADD COLUMN auto_receipt_at DATETIME;
ALTER TABLE xianyu_order ADD COLUMN deliver_content TEXT;     -- 实际发货内容快照

-- 卡密池（Card / Account 类虚拟商品共用）
CREATE TABLE IF NOT EXISTS virtual_card_pool (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL,
    card_code VARCHAR(256) NOT NULL,
    card_password VARCHAR(256),
    status VARCHAR(16) DEFAULT 'AVAILABLE', -- AVAILABLE / USED / EXPIRED
    used_order_id INTEGER,
    used_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    UNIQUE(card_code),
    FOREIGN KEY (product_id) REFERENCES xianyu_product(id)
);

-- 自动发货任务（定时扫描执行）
CREATE TABLE IF NOT EXISTS virtual_ship_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL UNIQUE,
    product_id INTEGER NOT NULL,
    status VARCHAR(16) DEFAULT 'PENDING', -- PENDING / PROCESSING / SHIPPED / FAILED / SKIPPED
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (order_id) REFERENCES xianyu_order(id),
    FOREIGN KEY (product_id) REFERENCES xianyu_product(id)
);

-- 自动发货全局配置（每账号一条）
CREATE TABLE IF NOT EXISTS virtual_ship_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT TRUE,
    delay_seconds INTEGER DEFAULT 30,           -- 支付成功后延时发货(防风控)
    auto_confirm_days INTEGER DEFAULT 7,        -- N天后自动确认收货
    notify_after_ship BOOLEAN DEFAULT TRUE,     -- 发货后站内通知运营
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_virtual_card_pool_product ON virtual_card_pool(product_id);
CREATE INDEX IF NOT EXISTS idx_virtual_card_pool_status ON virtual_card_pool(status);
CREATE INDEX IF NOT EXISTS idx_virtual_ship_task_status ON virtual_ship_task(status);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_require_virtual_ship ON xianyu_order(require_virtual_ship);

-- ======================== AI 客服 ========================

-- 客服会话表（按账号 + 买家分组，一个买家在一个账号下一个会话）
CREATE TABLE IF NOT EXISTS ai_cs_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    buyer_nickname VARCHAR(64),
    product_id INTEGER,                          -- 关联商品（可为空表示闲聊）
    order_id INTEGER,                            -- 关联订单（可为空）
    status VARCHAR(16) DEFAULT 'ACTIVE',         -- ACTIVE / CLOSED / BLOCKED
    last_message_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    UNIQUE(account_id, buyer_id)
);

-- 客服消息表（完整记录买家消息 + AI/运营回复）
CREATE TABLE IF NOT EXISTS ai_cs_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    direction VARCHAR(16),                       -- INCOMING(买家) / OUTGOING(AI/运营)
    content TEXT,
    intent VARCHAR(32),                          -- 意图分类（议价/确认商品/物流/售后/闲聊）
    intent_confidence REAL,                      -- 意图识别置信度
    ai_generated BOOLEAN DEFAULT FALSE,          -- 是否 AI 生成
    sent_by VARCHAR(16),                         -- AUTO(全自动) / AI_ASSIST(AI建议运营一键发) / HUMAN(纯手动)
    raw_ai_response TEXT,                        -- AI 原始回复（运营可能修改过）
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES ai_cs_session(id)
);

-- AI 知识库（商品 FAQ、通用话术，按账号隔离）
CREATE TABLE IF NOT EXISTS ai_cs_knowledge (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,                          -- NULL = 全局共享
    product_id INTEGER,                          -- NULL = 通用知识
    question VARCHAR(256) NOT NULL,             -- 问题关键词 / 触发词
    answer TEXT NOT NULL,                        -- 回复内容
    category VARCHAR(32),                        -- PRICE / SHIPPING / AFTERSALES / GENERAL / PRODUCT
    priority INTEGER DEFAULT 100,                -- 优先级（越小越优先）
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- AI 客服策略配置（按账号）
CREATE TABLE IF NOT EXISTS ai_cs_policy (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE,
    mode VARCHAR(16) DEFAULT 'ASSIST',           -- AUTO(全自动) / ASSIST(AI建议) / HYBRID(闲聊自动，议价辅助)
    auto_reply_enabled BOOLEAN DEFAULT FALSE,    -- 是否启用自动回复（AUTO 模式）
    -- 议价策略
    price_floor_pct REAL DEFAULT 0.80,           -- 底价比例（如 0.8 = 8 折是底价）
    price_step_pct REAL DEFAULT 0.05,            -- 每次降价幅度（如 0.05 = 5%）
    max_discount_steps INTEGER DEFAULT 3,        -- 最多降价几次
    -- 风控
    max_auto_replies_per_hour INTEGER DEFAULT 10, -- 单会话每小时最大自动回复数
    transfer_to_human_intents TEXT,               -- JSON: ["售后","投诉","退款"] → 这些意图转人工
    -- 话术风格
    tone VARCHAR(32) DEFAULT 'FRIENDLY',         -- FRIENDLY / PROFESSIONAL / CASUAL / HUMOROUS
    -- 时段
    enabled_from TIME,                           -- 自动回复生效时间（NULL 表示全天）
    enabled_to TIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

-- AI 客服统计表（按天汇总，用于运营查看效果）
CREATE TABLE IF NOT EXISTS ai_cs_daily_stats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    stat_date DATE NOT NULL,
    total_sessions INTEGER DEFAULT 0,
    total_messages INTEGER DEFAULT 0,
    ai_replies INTEGER DEFAULT 0,
    human_replies INTEGER DEFAULT 0,
    auto_replies INTEGER DEFAULT 0,
    intent_price_negotiation INTEGER DEFAULT 0,  -- 议价类会话数
    intent_product_inquiry INTEGER DEFAULT 0,    -- 商品咨询数
    intent_logistics INTEGER DEFAULT 0,           -- 物流查询数
    intent_aftersales INTEGER DEFAULT 0,         -- 售后数
    avg_response_seconds INTEGER DEFAULT 0,      -- 平均响应时长（秒）
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, stat_date),
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_ai_cs_session_account ON ai_cs_session(account_id);
CREATE INDEX IF NOT EXISTS idx_ai_cs_session_buyer ON ai_cs_session(account_id, buyer_id);
CREATE INDEX IF NOT EXISTS idx_ai_cs_message_session ON ai_cs_message(session_id);
CREATE INDEX IF NOT EXISTS idx_ai_cs_knowledge_account ON ai_cs_knowledge(account_id);
CREATE INDEX IF NOT EXISTS idx_ai_cs_daily_stats_account ON ai_cs_daily_stats(account_id);

-- ======================== AI 运营 ========================

-- AI 运营任务表（批量上品、多账号同步等）
CREATE TABLE IF NOT EXISTS ai_ops_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    task_type VARCHAR(32) NOT NULL,              - BATCH_CREATE / MULTI_ACCOUNT_SYNC / AUTO_REFRESH
    status VARCHAR(16) DEFAULT 'PENDING',         - PENDING / RUNNING / COMPLETED / FAILED
    payload TEXT,                                 - JSON 任务参数
    result_summary TEXT,                          - AI 生成摘要
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id)
);

-- AI 建议执行记录
CREATE TABLE IF NOT EXISTS ai_ops_suggestion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    suggestion_type VARCHAR(32),                  - PRICE_ADJUST / REFRESH_TIME / LISTING_OPTIMIZE
    product_id INTEGER,
    suggestion_content TEXT,                      - JSON AI 建议详情
    confidence REAL,
    adopted BOOLEAN,                              - 运营是否采纳
    adopted_at DATETIME,
    expected_impact TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    FOREIGN KEY (account_id) REFERENCES xianyu_account(id),
    FOREIGN KEY (product_id) REFERENCES xianyu_product(id)
);

-- 运营知识库
CREATE TABLE IF NOT EXISTS ai_ops_knowledge (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category VARCHAR(64),                         - 商品品类
    knowledge_type VARCHAR(32),                   - PRICING / DESCRIPTION_STYLE / POSTING_TIME / KEYWORD
    content TEXT,
    source VARCHAR(32),                           - AI_GENERATED / MANUAL / PLATFORM_RULE
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_ops_task_account ON ai_ops_task(account_id);
CREATE INDEX IF NOT EXISTS idx_ai_ops_suggestion_account ON ai_ops_suggestion(account_id);
