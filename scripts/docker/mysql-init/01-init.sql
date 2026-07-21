-- ============================================================================
-- MySQL 初始化脚本
-- 容器首次启动时自动执行（仅当 /var/lib/mysql 为空时）
-- ============================================================================

-- 确保数据库使用 utf8mb4
ALTER DATABASE xianyu_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建应用用户（如果 docker-compose 中 MYSQL_USER 未自动创建）
CREATE USER IF NOT EXISTS 'xianyu'@'%' IDENTIFIED WITH mysql_native_password BY 'xianyu123456';
GRANT ALL PRIVILEGES ON xianyu_manager.* TO 'xianyu'@'%';
FLUSH PRIVILEGES;

-- 注意：应用启动后 MyBatis-Plus 会自动创建表结构（通过 schema.sql 或 code-first）
-- 此脚本仅用于初始化数据库和用户权限
