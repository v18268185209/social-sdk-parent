-- 本地商品表（待上架闲鱼）
-- 发布成功后物理删除，不长期滞留
CREATE TABLE IF NOT EXISTS local_product (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT      NULL COMMENT '发布时关联的闲鱼账号',
    title         VARCHAR(255) NULL COMMENT '标题',
    price         DECIMAL(12,2) NULL COMMENT '售价（元）',
    original_price DECIMAL(12,2) NULL COMMENT '原价（元）',
    stock         INT          NULL COMMENT '库存',
    category_id   VARCHAR(64)  NULL COMMENT '闲鱼分类 ID',
    description   TEXT         NULL COMMENT '商品描述',
    images        TEXT         NULL COMMENT '图片 URL 列表（JSON）',
    videos        TEXT         NULL COMMENT '视频 URL 列表（JSON）',
    image_url     VARCHAR(512) NULL COMMENT '主图封面 URL',
    goods_type    VARCHAR(16)  NULL COMMENT 'PHYSICAL/VIRTUAL',
    deliver_type  VARCHAR(16)  NULL COMMENT 'CARD/ACCOUNT/LINK/FILE',
    deliver_content_template TEXT NULL COMMENT '虚拟发货模板',
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/PUBLISHING/FAILED',
    publish_error TEXT         NULL COMMENT '最近一次发布失败原因',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删',
    INDEX idx_local_product_account (account_id),
    INDEX idx_local_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '本地商品（待上架闲鱼）';
