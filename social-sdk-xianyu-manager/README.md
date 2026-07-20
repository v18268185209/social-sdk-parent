^# 闲鱼多账号管理平台 (Xianyu Manager)

轻量级闲鱼多账号管理平台，基于 SQLite3 + In-Memory Cache，单管理员多账号管理。

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 3.5 + Java 17 |
| 数据库 | SQLite3 (Embedded) |
| ORM | MyBatis-Plus 3.5 |
| HTTP 客户端 | social-sdk-xianyu |
| 缓存 | Caffeine (In-Memory) |
| 认证 | JWT |
| 任务调度 | Spring Schedule |

## 快速开始

### 1. 编译整个项目

```bash
cd E:\codes\social-sdk-parent
mvn clean install -DskipTests
```

### 2. 运行管理平台

```bash
cd social-sdk-xianyu-manager
mvn spring-boot:run
```

### 3. 访问

- 管理后台 API: `http://localhost:8080`
- 默认管理员: `admin` / `admin123`

## API 端点

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `POST /api/auth/login` | 管理员登录 |
| 认证 | `GET /api/auth/profile` | 获取当前用户信息 |
| 账号 | `GET /api/accounts` | 账号列表 |
| 账号 | `POST /api/accounts/login` | Cookie 登录 |
| 账号 | `PUT /api/accounts/{id}/status` | 更新状态 |
| 商品 | `GET /api/products` | 商品列表（分页） |
| 商品 | `POST /api/products` | 创建商品 |
| 商品 | `PUT /api/products/{id}` | 编辑商品 |
| 商品 | `POST /api/products/{id}/shelf-on` | 上架 |
| 商品 | `POST /api/products/{id}/shelf-off` | 下架 |
| 消息 | `GET /api/messages/sessions` | 会话列表 |
| 消息 | `GET /api/messages/history` | 消息历史 |
| 消息 | `POST /api/messages/send` | 发送消息 |
| 订单 | `GET /api/orders` | 订单列表 |
| 订单 | `POST /api/orders/{id}/delivery` | 发货 |
| 规则 | `GET /api/rules` | 规则列表 |
| 规则 | `POST /api/rules` | 创建规则 |
| 规则 | `POST /api/rules/test` | 测试规则匹配 |
| 规则 | `POST /api/rules/auto-reply` | 自动回复 |
| 审计 | `GET /api/audit/logs` | 审计日志 |

## 目录结构

```
social-sdk-xianyu-manager/
├── pom.xml
├── src/main/java/cn/net/rjnetwork/xianyu/manager/
│   ├── XianyuManagerApplication.java
│   ├── config/                    # 配置类
│   ├── common/                    # 通用组件
│   ├── auth/                      # 认证授权
│   ├── account/                   # 账号管理
│   ├── product/                   # 商品管理
│   ├── message/                   # 消息管理
│   ├── order/                     # 订单管理
│   ├── rule/                      # 规则引擎
│   ├── audit/                     # 审计日志
│   └── wallet/collect/monitor/    # 待扩展
├── src/main/resources/
│   ├── application.yml
│   └── db/schema.sql              # 数据库初始化脚本
└── src/test/
```

## 数据库

SQLite3 数据库文件位于运行目录下的 `data/xianyu-manager.db`。

首次启动会自动执行 `db/schema.sql` 初始化表结构。

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `XIANYU_JWT_SECRET` | JWT 签名密钥 | `defaultSecretKeyForDevelopmentOnlyChangeInProduction` |
| `XIANYU_CRYPTO_SECRET` | Cookie 加密密钥 | `defaultCryptoSecretKeyForDevelopmentOnly` |
