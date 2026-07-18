# 闲鱼多账号管理平台 — 需求规划与设计 (Lightweight Edition)

> 基于 `social-sdk-parent` 架构，新建 Spring Boot 子模块 `social-sdk-xianyu-manager`
> **轻量级架构**：SQLite3 + In-Memory Cache，单管理员多账号管理

---

## 一、项目定位

### 1.1 目标

构建一个**轻量级闲鱼多账号管理平台**，支持：
- **单管理员**登录管理
- **多闲鱼账号**集中管理（Cookie/扫码登录）
- 商品全生命周期管理（发布、编辑、上下架、库存、价格）
- 消息收发与自动回复
- 订单管理与自动发货
- 关键词规则引擎（自动匹配回复）
- 账号健康监控

### 1.2 技术栈

| 层级 | 技术选型 |
|------|---------|
| 后端框架 | Spring Boot 3.5 + Java 17 |
| 数据库 | **SQLite3** (Embedded) |
| ORM | **MyBatis-Plus** 3.5 (推荐) 或 Spring Data JDBC |
| HTTP 客户端 | 复用 `social-sdk-xianyu` 中的 `XianyuSdk` / `XianyuApiFacade` |
| 缓存 | **In-Memory** (Caffeine or ConcurrentHashMap) |
| 任务调度 | Spring Schedule |
| WebSocket | Spring WebSocket (单实例无需 Redis Pub/Sub) |
| 认证 | JWT (jsonwebtoken) |
| 前端 | Vue 3 + Element Plus (管理后台) |
| 部署 | 单 JAR 包运行 |

### 1.3 与现有模块的关系

```
social-sdk-parent
├── social-sdk-core          # 核心公共模块
├── social-sdk-xianyu        # 闲鱼 SDK（纯 HTTP/MTOP）
├── social-sdk-spring-boot-starter  # Starter 自动配置
└── social-sdk-xianyu-manager # ← 新建：闲鱼多账号管理平台（独立 Spring Boot 应用）
```

---

## 二、功能模块设计

### 2.1 模块总览

```
social-sdk-xianyu-manager
├── auth            # 认证授权模块 (单管理员)
├── account         # 账号管理模块
├── product         # 商品管理模块
├── message         # 消息管理模块
├── order           # 订单管理模块
├── rule            # 规则引擎模块
├── wallet          # 钱包/资产模块
├── collect         # 收藏关注模块
├── monitor         # 监控告警模块
├── audit           # 审计日志模块
└── system          # 系统管理模块
```

---

### 2.2 详细功能需求

#### 模块一：认证授权（auth）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 管理员登录 | 用户名/密码登录，JWT Token 鉴权 | P0 |
| 登录审计 | 记录登录 IP、时间 | P1 |

**API 端点：**
- `POST /api/auth/login` — 管理员登录
- `POST /api/auth/logout` — 退出登录
- `GET /api/auth/profile` — 获取当前用户信息

---

#### 模块二：账号管理（account）— 核心模块

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 账号列表 | 分页展示所有闲鱼账号，显示昵称、状态 | P0 |
| Cookie 登录 | 手动粘贴 Cookie 登录，自动解析 userId/nickname | P0 |
| 扫码登录 | 生成二维码，轮询等待扫码，成功后自动保存 Cookie | P0 |
| Cookie 更新 | 手动更新某账号的 Cookie | P1 |
| 账号状态管理 | 启用/禁用/冻结账号 | P0 |
| 账号健康检测 | 定时检测 Cookie 有效性，异常时标记 | P1 |

**数据库表：** `xianyu_account` (SQLite)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PRIMARY KEY | 自增主键 |
| account_name | VARCHAR(64) | 账号名称/备注名 |
| user_id | VARCHAR(64) | 闲鱼用户 ID |
| display_name | VARCHAR(128) | 昵称 |
| cookie_header | TEXT | Cookie 字符串（加密存储） |
| cookies_json | TEXT | Cookie JSON（加密存储） |
| status | VARCHAR(16) | ACTIVE / DISABLED / FROZEN / COOKIE_EXPIRED |
| remark | VARCHAR(256) | 备注 |
| last_error | VARCHAR(512) | 最近错误信息 |
| last_login_at | DATETIME | 最后登录时间 |
| cookie_expires_at | DATETIME | Cookie 预计过期时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**API 端点：**
- `GET /api/accounts` — 账号列表（分页）
- `GET /api/accounts/{id}` — 账号详情
- `POST /api/accounts/login` — Cookie 登录
- `POST /api/accounts/login/qrcode/generate` — 生成扫码二维码
- `POST /api/accounts/login/qrcode/poll` — 轮询扫码状态
- `PUT /api/accounts/{id}/cookies` — 更新 Cookie
- `PUT /api/accounts/{id}/status` — 更新状态
- `DELETE /api/accounts/{id}` — 删除账号
- `GET /api/accounts/{id}/profile` — 刷新账号资料

---

#### 模块三：商品管理（product）— 核心模块

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 商品列表 | 分页展示，按账号筛选，支持关键词搜索 | P0 |
| 商品详情 | 查看商品完整信息 | P0 |
| 创建商品 | 通过 API 创建商品 | P0 |
| 编辑商品 | 修改商品信息 | P0 |
| 上下架 | 单个/批量上下架 | P0 |
| 库存管理 | 更新库存数量 | P1 |
| 价格管理 | 更新售价/原价 | P1 |
| 草稿管理 | 保存/加载/删除草稿 | P2 |
| 图片上传 | 上传图片到闲鱼 CDN | P1 |

**数据库表：** `xianyu_product` (SQLite)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PRIMARY KEY | 自增主键 |
| account_id | INTEGER | 关联账号 |
| item_id | VARCHAR(64) | 闲鱼商品 ID |
| title | VARCHAR(256) | 商品标题 |
| price | REAL | 售价 |
| original_price | REAL | 原价 |
| stock | INTEGER | 库存 |
| status | VARCHAR(16) | ON_SALE / OFF_SALE / DRAFT |
| category_id | VARCHAR(64) | 分类 ID |
| images | TEXT | 图片 URL 列表（JSON） |
| description | TEXT | 商品描述 |
| detail_url | VARCHAR(512) | 商品详情页 URL |
| view_count | INTEGER | 浏览量 |
| favorite_count | INTEGER | 收藏量 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**API 端点：**
- `GET /api/products` — 商品列表（分页，按 accountId/keyword/status 筛选）
- `GET /api/products/{id}` — 商品详情
- `POST /api/products` — 创建商品
- `PUT /api/products/{id}` — 编辑商品
- `DELETE /api/products/{id}` — 删除商品
- `POST /api/products/{id}/shelf-on` — 上架
- `POST /api/products/{id}/shelf-off` — 下架
- `PUT /api/products/{id}/price` — 修改价格
- `PUT /api/products/{id}/stock` — 修改库存
- `POST /api/products/batch/shelf` — 批量上下架
- `POST /api/products/upload-image` — 上传图片

---

#### 模块四：消息管理（message）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 会话列表 | 获取所有聊天会话 | P0 |
| 消息历史 | 查看某个会话的历史消息 | P0 |
| 发送消息 | 向指定会话/用户发送文本消息 | P0 |
| 实时消息推送 | WebSocket 推送新消息 | P1 |
| 自动回复 | 基于关键词规则的自动回复 | P0 |

**数据库表：** `xianyu_message` (SQLite)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PRIMARY KEY | 自增主键 |
| account_id | INTEGER | 关联账号 |
| session_id | VARCHAR(64) | 会话 ID |
| sender_id | VARCHAR(64) | 发送者 ID |
| sender_name | VARCHAR(128) | 发送者昵称 |
| content | TEXT | 消息内容 |
| msg_type | VARCHAR(16) | TEXT / IMAGE / SYSTEM |
| direction | VARCHAR(8) | INCOMING / OUTGOING |
| is_auto_reply | BOOLEAN | 是否自动回复 |
| created_at | DATETIME | 消息时间 |

**API 端点：**
- `GET /api/messages/sessions` — 会话列表
- `GET /api/messages/sessions/{sessionId}` — 会话详情（消息列表）
- `POST /api/messages/send` — 发送消息
- `WS /ws/messages` — WebSocket 实时消息推送

---

#### 模块五：订单管理（order）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 订单列表 | 我买到的 / 我卖出的 分页查询 | P0 |
| 订单详情 | 查看订单完整信息 | P0 |
| 自动发货 | 输入物流单号，一键发货 | P1 |

**数据库表：** `xianyu_order` (SQLite)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PRIMARY KEY | 自增主键 |
| account_id | INTEGER | 关联账号 |
| order_id | VARCHAR(64) | 闲鱼订单 ID |
| item_title | VARCHAR(256) | 商品标题 |
| buyer_name | VARCHAR(128) | 买家昵称 |
| amount | REAL | 订单金额 |
| status | VARCHAR(32) | PENDING / PAID / SHIPPED / COMPLETED / REFUNDING |
| tracking_no | VARCHAR(64) | 物流单号 |
| created_at | DATETIME | 下单时间 |
| updated_at | DATETIME | 更新时间 |

**API 端点：**
- `GET /api/orders` — 订单列表（tab: bought/sold，分页）
- `GET /api/orders/{orderId}` — 订单详情
- `POST /api/orders/{orderId}/delivery` — 发货

---

#### 模块六：规则引擎（rule）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 关键词规则 | 配置关键词匹配规则（包含/精确/前缀） | P0 |
| 自动回复 | 匹配后自动回复指定内容 | P0 |
| 规则优先级 | 多条规则按优先级排序匹配 | P1 |
| 规则启用/禁用 | 动态开关规则 | P0 |
| 规则测试 | 输入文本测试是否命中规则 | P1 |

**数据库表：** `xianyu_keyword_rule` (SQLite)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER PRIMARY KEY | 自增主键 |
| account_id | INTEGER | 关联账号（NULL = 全局规则） |
| rule_name | VARCHAR(128) | 规则名称 |
| keyword | VARCHAR(256) | 关键词 |
| match_type | VARCHAR(16) | CONTAINS / EXACT / STARTS_WITH |
| reply_text | TEXT | 回复内容 |
| enabled | BOOLEAN | 是否启用 |
| priority | INTEGER | 优先级（数字越小越优先） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**API 端点：**
- `GET /api/rules` — 规则列表
- `POST /api/rules` — 创建规则
- `PUT /api/rules/{id}` — 编辑规则
- `DELETE /api/rules/{id}` — 删除规则
- `POST /api/rules/test` — 测试规则匹配

---

#### 模块七：其他模块（Wallet, Collect, Monitor, Audit）

- **钱包/资产**：余额查询、账单列表（P1）
- **收藏关注**：收藏列表、关注列表（P1）
- **监控告警**：账号健康监控、API 频率监控（P1）
- **审计日志**：记录所有管理操作（P1）

---

## 三、非功能性需求

### 3.1 性能要求

| 指标 | 目标值 |
|------|--------|
| 账号并发数 | 支持 50+ 账号同时在线（SQLite 读写锁限制） |
| API 响应时间 | 95% < 500ms |
| 消息处理延迟 | < 3s |

### 3.2 安全要求

| 要求 | 措施 |
|------|------|
| Cookie 存储 | AES-256 加密存储 |
| 传输加密 | HTTPS + JWT Token |
| 权限控制 | RBAC 细粒度权限（单管理员可配置角色） |
| 操作审计 | 全量操作日志 |

### 3.3 可用性要求

| 要求 | 指标 |
|------|------|
| 服务可用性 | 99.9% |
| 数据备份 | 每日自动备份 SQLite 文件 |

---

## 四、目录结构设计

```
social-sdk-xianyu-manager/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/cn/net/rjnetwork/xianyu/manager/
│   │   │   ├── ManagerApplication.java          # 启动类
│   │   │   ├── config/                           # 配置类
│   │   │   │   ├── SecurityConfig.java           # 安全配置 (JWT)
│   │   │   │   ├── MybatisPlusConfig.java        # MyBatis-Plus 配置
│   │   │   │   ├── WebSocketConfig.java          # WebSocket 配置
│   │   │   │   ├── SchedulerConfig.java          # 定时任务配置
│   │   │   │   └── CacheConfig.java              # 内存缓存配置
│   │   │   ├── common/                           # 公共模块
│   │   │   │   ├── ApiResponse.java              # 统一响应
│   │   │   │   ├── BaseException.java            # 异常基类
│   │   │   │   ├── PageRequest.java              # 分页请求
│   │   │   │   └── PageResponse.java             # 分页响应
│   │   │   ├── auth/                             # 认证授权
│   │   │   │   ├── controller/AuthController.java
│   │   │   │   ├── service/AuthService.java
│   │   │   │   ├── model/AdminUser.java
│   │   │   │   └── security/JwtUtils.java
│   │   │   ├── account/                          # 账号管理
│   │   │   │   ├── controller/AccountController.java
│   │   │   │   ├── service/AccountService.java
│   │   │   │   ├── mapper/AccountMapper.java
│   │   │   │   ├── model/XianyuAccount.java
│   │   │   │   ├── dto/                          # 请求/响应 DTO
│   │   │   │   └── task/AccountHealthTask.java   # 健康检查定时任务
│   │   │   ├── product/                          # 商品管理
│   │   │   │   ├── controller/ProductController.java
│   │   │   │   ├── service/ProductService.java
│   │   │   │   ├── mapper/ProductMapper.java
│   │   │   │   ├── model/XianyuProduct.java
│   │   │   │   └── dto/
│   │   │   ├── message/                          # 消息管理
│   │   │   │   ├── controller/MessageController.java
│   │   │   │   ├── service/MessageService.java
│   │   │   │   ├── mapper/MessageMapper.java
│   │   │   │   ├── model/XianyuMessage.java
│   │   │   │   ├── dto/
│   │   │   │   └── websocket/MessageWebSocketHandler.java
│   │   │   ├── order/                            # 订单管理
│   │   │   │   ├── controller/OrderController.java
│   │   │   │   ├── service/OrderService.java
│   │   │   │   ├── mapper/OrderMapper.java
│   │   │   │   ├── model/XianyuOrder.java
│   │   │   │   └── dto/
│   │   │   ├── rule/                             # 规则引擎
│   │   │   │   ├── controller/RuleController.java
│   │   │   │   ├── service/RuleService.java
│   │   │   │   ├── mapper/RuleMapper.java
│   │   │   │   ├── model/XianyuKeywordRule.java
│   │   │   │   ├── engine/KeywordRuleEngine.java  # 规则匹配引擎
│   │   │   │   └── dto/
│   │   │   ├── wallet/                           # 钱包/资产
│   │   │   │   ├── controller/WalletController.java
│   │   │   │   ├── service/WalletService.java
│   │   │   │   └── dto/
│   │   │   ├── collect/                          # 收藏关注
│   │   │   │   ├── controller/CollectController.java
│   │   │   │   ├── service/CollectService.java
│   │   │   │   └── dto/
│   │   │   ├── monitor/                          # 监控告警
│   │   │   │   ├── controller/MonitorController.java
│   │   │   │   ├── service/MonitorService.java
│   │   │   │   └── task/
│   │   │   ├── audit/                            # 审计日志
│   │   │   │   ├── controller/AuditController.java
│   │   │   │   ├── service/AuditService.java
│   │   │   │   ├── mapper/AuditLogMapper.java
│   │   │   │   ├── model/AuditLog.java
│   │   │   │   └── aspect/AuditLogAspect.java    # AOP 切面
│   │   │   └── system/                           # 系统管理
│   │   │       ├── controller/SystemController.java
│   │   │       ├── service/SystemService.java
│   │   │       └── model/
│   │   ├── resources/
│   │   │   ├── application.yml                   # 主配置文件 (SQLite Path)
│   │   │   ├── application-dev.yml               # 开发环境
│   │   │   ├── application-prod.yml              # 生产环境
│   │   │   └── mapper/                           # MyBatis XML 映射
│   │   └── frontend/                             # 前端资源（Vue 打包产物）
│   └── test/
│       └── java/cn/net/rjnetwork/xianyu/manager/
└── docker/
    ├── Dockerfile
    └── docker-compose.yml
```

---

## 五、数据库 ER 设计

### 5.1 核心表关系

```
admin_user ──1:N──> audit_log
xianyu_account ──1:N──> xianyu_product
xianyu_account ──1:N──> xianyu_message
xianyu_account ──1:N──> xianyu_order
xianyu_account ──1:N──> xianyu_keyword_rule
```

### 5.2 全部表清单

| 序号 | 表名 | 说明 |
|------|------|------|
| 1 | `admin_user` | 管理员用户 |
| 2 | `admin_role` | 角色 |
| 3 | `admin_user_role` | 用户角色关联 |
| 4 | `admin_role_permission` | 角色权限关联 |
| 5 | `xianyu_account` | 闲鱼账号 |
| 6 | `xianyu_product` | 商品 |
| 7 | `xianyu_message` | 消息 |
| 8 | `xianyu_order` | 订单 |
| 9 | `xianyu_keyword_rule` | 关键词规则 |
| 10 | `xianyu_collect` | 收藏记录 |
| 11 | `xianyu_follow` | 关注记录 |
| 12 | `audit_log` | 审计日志 |
| 13 | `sys_config` | 系统配置 |
| 14 | `sys_dict` | 系统字典 |

---

## 六、前后端交互设计

### 6.1 前端架构

```
前端项目: social-sdk-xianyu-manager-web
├── src/
│   ├── api/                    # Axios 请求封装
│   ├── views/                  # 页面组件
│   │   ├── login/              # 登录页
│   │   ├── dashboard/          # 仪表盘
│   │   ├── accounts/           # 账号管理
│   │   ├── products/           # 商品管理
│   │   ├── messages/           # 消息管理
│   │   ├── orders/             # 订单管理
│   │   ├── rules/              # 规则管理
│   │   ├── collect/            # 收藏管理
│   │   ├── wallet/             # 钱包管理
│   │   ├── monitor/            # 监控面板
│   │   ├── audit/              # 审计日志
│   │   └── system/             # 系统设置
│   ├── components/             # 公共组件
│   ├── layouts/                # 布局组件
│   ├── store/                  # Pinia 状态管理
│   ├── router/                 # 路由配置
│   └── utils/                  # 工具函数
├── package.json
└── vite.config.js
```

### 6.2 前后端集成方案

- **方案 A（推荐）：** 前端打包为静态文件，由 Spring Boot 的 `ResourceHandler` 提供
- **方案 B：** 前后端分离，前端独立部署 Nginx，通过 CORS 跨域访问后端 API

---

## 七、定时任务设计

| 任务 | 频率 | 说明 |
|------|------|------|
| 账号健康检测 | 每 5 分钟 | 检测所有账号 Cookie 有效性 |
| 过期账号清理 | 每天 02:00 | 标记 Cookie 过期账号 |
| 消息自动回复 | 实时（WebSocket 触发） | 收到新消息时匹配规则并自动回复 |
| 审计日志清理 | 每月 1 日 | 清理超过 90 天的审计日志 |
| 数据统计聚合 | 每天 00:00 | 汇总昨日商品/消息/订单数据 |

---

## 八、Phase 分期规划

### Phase 1 — MVP（核心功能）

- [x] 账号管理（登录、Cookie 管理、状态管理）
- [x] 商品管理（列表、创建、编辑、上下架）
- [x] 消息管理（会话列表、发送消息、关键词自动回复）
- [x] 规则引擎（关键词规则 CRUD）
- [x] 认证授权（JWT + RBAC）
- [x] 基础审计日志

### Phase 2 — 增强功能

- [ ] 订单管理
- [ ] 钱包/资产
- [ ] 收藏关注
- [ ] WebSocket 实时消息推送
- [ ] 账号健康监控
- [ ] 批量操作

### Phase 3 — 高级功能

- [ ] 数据统计仪表盘
- [ ] 风控告警
- [ ] 操作频率监控
- [ ] 前端可视化大屏
- [ ] 多语言支持

---

## 九、关键设计决策

### 9.1 为什么选择 SQLite3？

| 维度 | SQLite | MySQL |
|------|--------|-------|
| 部署复杂度 | 极低（单文件） | 中（需安装服务） |
| 并发写入 | 有限（文件锁） | 好 |
| 运维复杂度 | 低 | 中 |
| 扩展性 | 受限 | 可扩展 |

**结论：** 轻量级管理平台，单管理员使用，SQLite3 足够满足需求，且部署和维护成本极低。

### 9.2 为什么选择 In-Memory Cache？

| 维度 | In-Memory | Redis |
|------|-----------|-------|
| 部署复杂度 | 无 | 中（需安装服务） |
| 性能 | 极高 | 高 |
| 共享性 | 单实例有效 | 多实例共享 |

**结论：** 单实例部署，In-Memory 缓存性能最佳，且无需额外依赖。

### 9.3 Cookie 加密存储

所有 Cookie 数据在数据库中采用 **AES-256-GCM** 加密存储，解密时使用 `javax.crypto.Cipher`，密钥通过环境变量 `XIANYU_MANAGER_COOKIE_ENCRYPT_KEY` 注入。

### 9.4 SDK 复用策略

管理平台通过 Maven 依赖直接引用 `social-sdk-xianyu`：

```xml
<dependency>
    <groupId>cn.net.rjnetwork</groupId>
    <artifactId>social-sdk-xianyu</artifactId>
    <version>${project.version}</version>
</dependency>
```

所有闲鱼 API 调用均通过 `XianyuSdk` 门面类发起，确保 SDK 升级时无需修改业务代码。

---

## 十、待确认事项

| 编号 | 问题 | 建议方案 |
|------|------|----------|
| Q1 | 是否需要支持多平台（不只是闲鱼）？ | 预留 `platform` 字段，架构上支持扩展 |
| Q2 | 前端是否需要独立仓库？ | 建议放在 `social-sdk-xianyu-manager-web` 独立仓库，或通过 git submodule 引用 |
| Q3 | 是否需要微服务拆分？ | Phase 1 单体即可，后期可按模块拆分为独立服务 |
| Q4 | 消息自动回复的并发控制？ | 使用内存锁，同一账号同一时间只允许一个回复任务 |
| Q5 | 是否需要文件上传功能？ | 商品图片走闲鱼 CDN，无需本地文件存储 |
