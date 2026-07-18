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
    ├── src/
    │   ├── main/
    │   │   ├── java/cn/net/rjnetwork/xianyu/manager/
    │   │   │   ├── XianyuManagerApplication.java
    │   │   │   ├── config/
    │   │   │   ├── common/
    │   │   │   ├── account/
    │   │   │   ├── product/
    │   │   │   ├── message/
    │   │   │   ├── order/
    │   │   │   ├── rule/
    │   │   │   ├── auth/
    │   │   │   ├── wallet/
    │   │   │   ├── collect/
    │   │   │   ├── monitor/
    │   │   │   ├── audit/
    │   │   │   └── system/
    │   │   └── resources/
    │   │       ├── application.yml
    │   │       └── mapper/
    │   └── test/
    └── docker/
```

---

## 二、已完成工作

| 序号 | 文件 | 状态 |
|------|------|------|
| 1 | `plan/requirements.md` | ✅ 已更新（SQLite3 + In-Memory Cache） |
| 2 | `social-sdk-xianyu-manager/pom.xml` | ✅ 已创建 |
| 3 | `XianyuManagerApplication.java` | ✅ 已创建 |
| 4 | `application.yml` | ✅ 已创建 |
| 5 | `ApiResponse.java` | ✅ 已创建 |
| 6 | `GlobalExceptionHandler.java` | ✅ 已创建 |
| 7 | `BaseEntity.java` | ✅ 已创建 |
| 8 | `MyMetaObjectHandler.java` | ✅ 已创建 |
| 9 | `MybatisPlusConfig.java` | ✅ 已创建 |
| 10 | `XianyuAccount.java` (Entity) | ✅ 已创建 |
| 11 | `AccountMapper.java` | ✅ 已创建 |
| 12 | `AccountLoginRequest.java` (DTO) | ✅ 已创建 |
| 13 | `AccountStatusUpdateRequest.java` (DTO) | ✅ 已创建 |
| 14 | `AccountService.java` | ✅ 已创建 |
| 15 | 父 POM 更新 | ✅ 已添加新模块 |
| 16 | 编译验证 | ✅ 全部通过 |

---

## 三、下一步计划

### Phase 1 — 核心模块（P0）

| 模块 | 待创建文件 | 优先级 |
|------|-----------|--------|
| 认证授权 | `AuthController.java`, `AuthService.java`, `AdminUser.java`, `JwtUtils.java`, `SecurityConfig.java` | P0 |
| 账号管理 | `AccountController.java` | P0 |
| 商品管理 | `ProductController.java`, `ProductService.java`, `ProductMapper.java`, `XianyuProduct.java`, DTOs | P0 |
| 消息管理 | `MessageController.java`, `MessageService.java`, `MessageMapper.java`, `XianyuMessage.java`, DTOs | P0 |
| 规则引擎 | `RuleController.java`, `RuleService.java`, `RuleMapper.java`, `XianyuKeywordRule.java`, `KeywordRuleEngine.java`, DTOs | P0 |
| 审计日志 | `AuditController.java`, `AuditService.java`, `AuditLogMapper.java`, `AuditLog.java`, `AuditLogAspect.java` | P0 |

### Phase 2 — 增强功能（P1）

| 模块 | 待创建文件 | 优先级 |
|------|-----------|--------|
| 订单管理 | `OrderController.java`, `OrderService.java`, `OrderMapper.java`, `XianyuOrder.java`, DTOs | P1 |
| 钱包/资产 | `WalletController.java`, `WalletService.java`, DTOs | P1 |
| 收藏关注 | `CollectController.java`, `CollectService.java`, DTOs | P1 |
| 监控告警 | `MonitorController.java`, `MonitorService.java`, 定时任务 | P1 |
| WebSocket | `MessageWebSocketHandler.java`, `WebSocketConfig.java` | P1 |

### Phase 3 — 高级功能（P2/P3）

| 模块 | 待创建文件 | 优先级 |
|------|-----------|--------|
| 数据统计 | Dashboard 相关 Controller/Service | P2 |
| 前端集成 | Vue 3 + Element Plus 管理后台 | P3 |
| Docker 部署 | `Dockerfile`, `docker-compose.yml` | P3 |

---

## 四、关键技术点

### 4.1 数据库配置

- **SQLite3**：嵌入式数据库，单文件存储，无需额外安装
- **MyBatis-Plus**：提供 CRUD 操作和分页插件
- **逻辑删除**：使用 `@TableLogic` 注解实现软删除

### 4.2 缓存策略

- **In-Memory Cache**：使用 `ConcurrentHashMap` 或 Caffeine
- **缓存内容**：账号信息、商品列表、规则引擎结果
- **过期策略**：根据业务需求设置 TTL

### 4.3 安全设计

- **JWT 认证**：单管理员登录，Token 有效期 24 小时
- **Cookie 加密**：AES-256-GCM 加密存储闲鱼 Cookie
- **操作审计**：全量记录管理操作日志

### 4.4 SDK 集成

- **复用 `social-sdk-xianyu`**：所有闲鱼 API 调用通过 `XianyuSdk` 门面类
- **异步处理**：消息自动回复、账号健康检测使用异步线程池
- **WebSocket**：单实例无需 Redis Pub/Sub，直接使用 Spring WebSocket

---

## 五、常用命令

```bash
# 编译整个项目
mvn clean compile -DskipTests

# 打包
mvn clean package -DskipTests

# 运行管理平台
cd social-sdk-xianyu-manager
mvn spring-boot:run

# 测试 SQLite 数据库
# 数据文件位于: ./data/xianyu-manager.db
```

---

## 六、API 端点预览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 管理员登录 |
| GET | `/api/accounts` | 账号列表 |
| POST | `/api/accounts/login` | Cookie 登录 |
| PUT | `/api/accounts/{id}/status` | 更新账号状态 |
| GET | `/api/products` | 商品列表 |
| POST | `/api/products` | 创建商品 |
| GET | `/api/messages/sessions` | 会话列表 |
| POST | `/api/messages/send` | 发送消息 |
| GET | `/api/rules` | 规则列表 |
| POST | `/api/rules` | 创建规则 |

---

## 七、注意事项

1. **SQLite 并发限制**：SQLite 使用文件锁，并发写入能力有限，建议单实例部署
2. **Cookie 安全**：所有 Cookie 数据必须加密存储，密钥通过环境变量注入
3. **SDK 依赖**：确保 `social-sdk-xianyu` 已正确安装到本地 Maven 仓库
4. **前端集成**：Phase 1 先完成后端 API，前端可后续独立开发或通过 git submodule 引用
