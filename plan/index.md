# 闲鱼多账号管理平台 — 快速参考索引

> 基于 `social-sdk-parent` 架构，新建 Spring Boot 子模块 `social-sdk-xianyu-manager`
> **轻量级架构**：SQLite3 + In-Memory Cache，单管理员多账号管理

---

## 一、项目结构

```
social-sdk-parent/
├── plan/
│   ├── requirements.md  ← 详细需求文档
│   └── index.md         ← 本文件（快速参考）
├── social-sdk-core/
├── social-sdk-xianyu/
├── social-sdk-spring-boot-starter/
└── social-sdk-xianyu-manager/  ← 新建模块
    ├── pom.xml
    ├── src/main/java/cn/net/rjnetwork/xianyu/manager/
    │   ├── XianyuManagerApplication.java
    │   ├── config/                          # 配置类
    │   │   ├── SecurityConfig.java          # 安全配置 (JWT)
    │   │   ├── MybatisPlusConfig.java       # MyBatis-Plus 配置
    │   │   ├── WebSocketConfig.java         # WebSocket STOMP 配置
    │   │   ├── CacheConfig.java             # Caffeine 内存缓存
    │   │   └── DatabaseInitializer.java     # 数据库初始化
    │   ├── common/                          # 公共组件
    │   │   ├── ApiResponse.java             # 统一响应
    │   │   ├── GlobalExceptionHandler.java  # 全局异常
    │   │   └── BaseEntity.java              # 基础实体
    │   ├── auth/                            # 认证授权 ✅
    │   │   ├── controller/AuthController.java
    │   │   ├── service/AuthService.java
    │   │   ├── model/AdminUser.java
    │   │   ├── security/JwtUtils.java
    │   │   └── dto/
    │   ├── account/                         # 账号管理 ✅
    │   │   ├── controller/AccountController.java
    │   │   ├── service/AccountService.java
    │   │   ├── mapper/AccountMapper.java
    │   │   ├── model/XianyuAccount.java
    │   │   ├── dto/
    │   │   └── task/AccountHealthTask.java  # 健康检查定时任务
    │   ├── product/                         # 商品管理 ✅
    │   │   ├── controller/ProductController.java
    │   │   ├── service/ProductService.java
    │   │   ├── mapper/ProductMapper.java
    │   │   ├── model/XianyuProduct.java
    │   │   └── dto/
    │   ├── message/                         # 消息管理 ✅
    │   │   ├── controller/MessageController.java
    │   │   ├── service/MessageService.java
    │   │   ├── mapper/MessageMapper.java
    │   │   ├── model/XianyuMessage.java
    │   │   ├── dto/
    │   │   └── websocket/
    │   │       ├── MessageBroadcaster.java
    │   │       ├── MessageHandler.java
    │   │       └── MessageWebSocketHandler.java
    │   ├── order/                           # 订单管理 ✅
    │   │   ├── controller/OrderController.java
    │   │   ├── service/OrderService.java
    │   │   ├── mapper/OrderMapper.java
    │   │   └── model/XianyuOrder.java
    │   ├── rule/                            # 规则引擎 ✅
    │   │   ├── controller/RuleController.java
    │   │   ├── service/RuleService.java
    │   │   ├── mapper/RuleMapper.java
    │   │   ├── model/XianyuKeywordRule.java
    │   │   ├── engine/KeywordRuleEngine.java
    │   │   └── dto/
    │   ├── wallet/                          # 钱包/资产 ✅
    │   │   ├── controller/WalletController.java
    │   │   ├── service/WalletService.java
    │   │   ├── mapper/
    │   │   └── model/
    │   ├── collect/                         # 收藏关注 ✅
    │   │   ├── controller/CollectController.java
    │   │   ├── service/CollectService.java
    │   │   ├── mapper/CollectMapper.java
    │   │   └── model/XianyuCollect.java
    │   ├── monitor/                         # 监控告警 ✅
    │   │   ├── controller/MonitorController.java
    │   │   └── service/MonitorService.java
    │   ├── audit/                           # 审计日志 ✅
    │   │   ├── controller/AuditController.java
    │   │   ├── service/AuditService.java
    │   │   ├── mapper/AuditLogMapper.java
    │   │   ├── model/AuditLog.java
    │   │   ├── annotation/Audit.java
    │   │   └── aspect/AuditLogAspect.java
    │   └── system/                          # 系统管理 ✅
    │       └── controller/SystemController.java
    ├── src/main/resources/
    │   ├── application.yml
    │   └── db/schema.sql
    └── README.md
```

---

## 二、已完成工作 ✅

### Phase 1 — 核心功能（P0）

| 模块 | 状态 | 文件数 |
|------|------|--------|
| 认证授权 | ✅ 完成 | 6 |
| 账号管理 | ✅ 完成 | 7 |
| 商品管理 | ✅ 完成 | 7 |
| 消息管理 | ✅ 完成 | 6 |
| 规则引擎 | ✅ 完成 | 7 |
| 订单管理 | ✅ 完成 | 5 |
| 审计日志 | ✅ 完成 | 6 |
| 基础设施 | ✅ 完成 | 5 |

### Phase 2 — 增强功能（P1）

| 模块 | 状态 | 文件数 |
|------|------|--------|
| 钱包/资产 | ✅ 完成 | 5 |
| 收藏关注 | ✅ 完成 | 4 |
| 监控告警 | ✅ 完成 | 2 |
| WebSocket 实时推送 | ✅ 完成 | 3 |
| 系统管理 | ✅ 完成 | 1 |

### 总计

- **Java 源文件**: 60+
- **配置文件**: 3
- **SQL 脚本**: 1
- **编译状态**: ✅ 全部通过

---

## 三、API 端点总览

| 模块 | 路径 | 方法 | 说明 |
|------|------|------|------|
| 认证 | `/api/auth/login` | POST | 管理员登录 |
| 认证 | `/api/auth/profile` | GET | 获取当前用户 |
| 账号 | `/api/accounts` | GET/POST/DELETE | 账号 CRUD |
| 账号 | `/api/accounts/{id}/status` | PUT | 更新状态 |
| 商品 | `/api/products` | GET/POST | 商品列表/创建 |
| 商品 | `/api/products/{id}` | PUT/DELETE | 商品编辑/删除 |
| 商品 | `/api/products/{id}/shelf-on/off` | POST | 上下架 |
| 商品 | `/api/products/{id}/price/stock` | PUT | 价格/库存 |
| 消息 | `/api/messages/sessions` | GET | 会话列表 |
| 消息 | `/api/messages/history` | GET | 消息历史 |
| 消息 | `/api/messages/send` | POST | 发送消息 |
| 消息 | `/ws/messages` | WS | WebSocket 推送 |
| 订单 | `/api/orders` | GET | 订单列表 |
| 订单 | `/api/orders/{id}` | GET | 订单详情 |
| 订单 | `/api/orders/{id}/delivery` | POST | 发货 |
| 规则 | `/api/rules` | GET/POST | 规则列表/创建 |
| 规则 | `/api/rules/{id}` | PUT/DELETE | 规则编辑/删除 |
| 规则 | `/api/rules/{id}/toggle` | POST | 启用/禁用 |
| 规则 | `/api/rules/test` | POST | 测试匹配 |
| 规则 | `/api/rules/auto-reply` | POST | 自动回复 |
| 钱包 | `/api/wallet/{accountId}` | GET | 钱包余额 |
| 钱包 | `/api/wallet/{accountId}/transactions` | GET | 交易记录 |
| 钱包 | `/api/wallet/{accountId}/recent` | GET | 最近交易 |
| 收藏 | `/api/collect` | GET/POST | 收藏列表/添加 |
| 收藏 | `/api/collect/{id}` | DELETE | 取消收藏 |
| 监控 | `/api/monitor/dashboard` | GET | 仪表盘统计 |
| 监控 | `/api/monitor/accounts` | GET | 账号维度统计 |
| 监控 | `/api/monitor/cache/clear` | POST | 清除缓存 |
| 审计 | `/api/audit/logs` | GET | 审计日志 |
| 系统 | `/api/system/info` | GET | 系统信息 |
| 系统 | `/api/system/health` | GET | 健康检查 |

---

## 四、快速启动

```bash
# 1. 编译
cd E:\codes\social-sdk-parent
mvn clean install -DskipTests

# 2. 运行
cd social-sdk-xianyu-manager
mvn spring-boot:run

# 3. 访问
# 管理员登录: admin / admin123
# API: http://localhost:8080
# WebSocket: ws://localhost:8080/ws/messages
```

---

## 五、待扩展（Phase 3）

- [ ] 前端管理界面（Vue 3 + Element Plus）
- [ ] Docker 部署配置
- [ ] 数据导出（CSV/Excel）
- [ ] 更多运营分析报表
