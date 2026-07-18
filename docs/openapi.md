# 对外 OpenAPI v1

> 覆盖账号、商品、消息、订单、钱包、AI、通知等全部能力的对外开放接口。
>
> **Base URL**: `http://your-host:8080/openapi/v1`
>
> **文档 UI**: `http://your-host:8080/openapi/v1/docs` (Swagger UI)
>
> **OpenAPI JSON**: `http://your-host:8080/openapi/v1/openapi.json`

---

## 1. 接入流程

### 1.1 创建应用（管理员）

调用方先到**内部管理后台**手动创建应用，获取 `appKey` + `appSecret`：

```http
POST /api/openapi/apps
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "appName": "你的应用名",
  "boundAccountIds": [1, 2, 3],   // 绑定账号白名单；空 = 可访问全部账号
  "rateLimitPerMinute": 60        // 限流：每分钟最大请求数；0 = 不限制
}
```

**响应**：

```json
{
  "code": "OK",
  "message": "Success",
  "data": {
    "id": 1,
    "appName": "你的应用名",
    "appKey": "ak_xxxxxxxx",
    "appSecret": "xxxxxxxxxxxxxxxx",   // ⚠️ 仅此一次明文返回，请妥善保管
    "status": "ENABLED",
    "boundAccountIds": [1, 2, 3],
    "rateLimitPerMinute": 60,
    "expireAt": null,
    "createdAt": "2026-07-19T10:30:00"
  },
  "timestamp": 1721365800
}
```

### 1.2 换取 Bearer 令牌

```http
POST /openapi/v1/oauth/token
Content-Type: application/json

{
  "appKey": "ak_xxxxxxxx",
  "appSecret": "xxxxxxxxxxxxxxxx"
}
```

**响应**：

```json
{
  "code": "OK",
  "message": "Success",
  "data": {
    "accessToken": "xxxxxxxxxxxxxxxx",
    "tokenType": "Bearer",
    "expiresIn": 7200
  },
  "timestamp": 1721365800
}
```

- 令牌有效期：**2 小时**（7200 秒），过期后需重新换取。
- 内存缓存（Caffeine），单实例足够；重启服务令牌失效，需重新获取。

### 1.3 调用业务接口

```http
GET /openapi/v1/accounts
Authorization: Bearer xxxxxxxxxxxxxxxx
```

---

## 2. 鉴权机制

| 项 | 说明 |
|---|------|
| 方式 | `Authorization: Bearer <token>` |
| 令牌端点 | `POST /openapi/v1/oauth/token`（公开，无需 Bearer） |
| 有效期 | 7200 秒（2 小时） |
| 缓存 | Caffeine（内存），最大 10,000 条，自动 LRU 淘汰 |
| 限流 | 按 `appKey` 粒度，每分钟独立计数；超限返回 `OPEN_RATE_LIMIT` |
| 作用域 | 账号维度白名单 `boundAccountIds`，空 = 不限制 |

---

## 3. 统一响应信封

所有接口返回 `OpenApiResponse<T>` 结构：

```json
{
  "code": "OK",
  "message": "Success",
  "data": { ... },
  "timestamp": 1721365800
}
```

- 成功：`code = "OK"`
- 失败：`code = "OPEN_xxxx"`（业务错误码，详见下方）

---

## 4. 错误码

| code | HTTP 状态 | 说明 |
|------|----------|------|
| `OPEN_UNAUTHORIZED` | 401 | 未提供访问令牌 |
| `OPEN_INVALID_TOKEN` | 401 | 访问令牌无效或已过期 |
| `OPEN_APP_DISABLED` | 403 | 应用已被禁用 |
| `OPEN_APP_EXPIRED` | 403 | 应用凭证已过期 |
| `OPEN_RATE_LIMIT` | 429 | 请求过于频繁，请稍后重试 |
| `OPEN_ACCOUNT_FORBIDDEN` | 403 | 无权访问该账号 |
| `OPEN_INVALID_PARAM` | 400 | 请求参数错误 |
| `OPEN_NOT_FOUND` | 404 | 资源不存在 |
| `OPEN_INTERNAL` | 500 | 服务内部错误 |

---

## 5. 应用管理（内部管理员接口）

> 路径前缀：`/api/openapi/apps`，需要 **管理员 JWT**。

### 5.1 创建应用

```http
POST /api/openapi/apps
Authorization: Bearer <admin-jwt>
```

见上方 [1.1 创建应用](#11-创建应用管理员)。

### 5.2 查询应用列表

```http
GET /api/openapi/apps
Authorization: Bearer <admin-jwt>
```

**响应**：`OpenAppResponse` 列表（`appSecret` 字段恒为 `null`，不会返回）。

### 5.3 应用生命周期（待扩展）

- `POST /api/openapi/apps/{id}/disable` —— 禁用应用
- `POST /api/openapi/apps/{id}/enable` —— 启用应用
- `PUT /api/openapi/apps/{id}` —— 修改绑定/限流

---

## 6. 业务接口（OpenAPI v1）

### 6.1 OAuth 域

| 方法 | 路径 | 公开 | 说明 |
|------|------|:----:|------|
| POST | `/openapi/v1/oauth/token` | ✅ | 换取 Bearer 令牌 |

### 6.2 账号域

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/accounts` | 账号列表（按绑定白名单过滤） |
| GET | `/openapi/v1/accounts?accountId=1` | 按 ID 过滤 |
| GET | `/openapi/v1/accounts/{id}` | 账号详情（做作用域校验） |

**账号视图（OpenApiAccountVO，已脱敏）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 账号 ID |
| accountName | String | 内部别名 |
| userId | String | 闲鱼用户 ID |
| displayName | String | 昵称 |
| status | String | ACTIVE / PENDING_VERIFY / FAILED |
| remark | String | 备注 |
| avatar | String | 头像 URL |
| ipLocation | String | IP 属地 |
| followers | Integer | 粉丝数 |
| following | Integer | 关注数 |
| soldCount | Integer | 已卖出 |
| purchaseCount | Integer | 已买入 |
| collectionCount | Integer | 收藏数 |
| onSaleCount | Integer | 在售数 |
| shopLevel | String | 店铺等级 |
| creditScore | Integer | 信用分 |
| reviewNum | Integer | 好评数 |
| lastLoginAt | DateTime | 最近登录 |
| profileSyncedAt | DateTime | 资料同步时间 |

> ⚠️ **安全设计**：绝不暴露 `cookieHeader`、`cookiesJson`、`lastError` 等内部字段。

### 6.3 商品域（待扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/products` | 商品列表 |
| POST | `/openapi/v1/products` | 创建商品 |
| GET | `/openapi/v1/products/{id}` | 商品详情 |
| POST | `/openapi/v1/products/{id}/shelf-on` | 上架 |
| POST | `/openapi/v1/products/{id}/shelf-off` | 下架 |

### 6.4 消息域（待扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/messages/sessions` | 会话列表 |
| GET | `/openapi/v1/messages/history` | 消息历史 |
| POST | `/openapi/v1/messages/send` | 发送消息 |

### 6.5 订单域（待扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/orders` | 订单列表 |
| POST | `/openapi/v1/orders/{id}/delivery` | 发货 |

### 6.6 钱包域（待扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/wallet/{accountId}` | 钱包资产 |

### 6.7 AI 域（待扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/cs/sessions` | AI 客服会话列表 |
| GET | `/openapi/v1/cs/messages` | AI 客服消息记录 |

### 6.8 通知域（待扩展）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/openapi/v1/notify/messages` | 站内通知 |

---

## 7. 限流算法

- **粒度**：单 `appKey`，每分钟滑动窗口
- **实现**：内存 ConcurrentHashMap，`[当前分钟 epoch, 计数]`
- **超限响应**：HTTP 429，`code = "OPEN_RATE_LIMIT"`
- **默认**：60 次/分钟（可在创建应用时自定义）

---

## 8. 数据安全

| 项 | 措施 |
|---|------|
| appSecret 存储 | AES 加密（`appSecretEnc`），明文不落库 |
| 令牌存储 | Caffeine 内存缓存，不持久化 |
| 响应脱敏 | `OpenApiAccountVO` 不包含 Cookie、内部错误等敏感字段 |
| 凭证过期 | `expireAt` 字段控制，过期自动拒绝 |
| 包隔离 | OpenApiExceptionHandler 最高优先级，避免内部异常处理干扰 |

---

## 9. 调用示例（curl）

```bash
# 1. 换取令牌
TOKEN=$(curl -s -X POST http://localhost:8080/openapi/v1/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"appKey":"ak_xxx","appSecret":"xxx"}' | jq -r '.data.accessToken')

# 2. 调用账号列表
curl -s http://localhost:8080/openapi/v1/accounts \
  -H "Authorization: Bearer $TOKEN" | jq .

# 3. 查询单个账号
curl -s http://localhost:8080/openapi/v1/accounts/1 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## 10. 后续扩展

- [ ] 商品、消息、订单、钱包、AI、通知等业务接口完整导出
- [ ] 分页 + 游标支持
- [ ] Webhook 主动推送（消息、订单状态变更）
- [ ] 单点登出 / 令牌吊销
- [ ] 审计日志（外部调用记录）
- [ ] 多实例令牌共享（Redis 等分布式缓存）
- [ ] SDK 自动生成（OpenAPI Generator）

---

## 11. 数据表

```sql
CREATE TABLE open_app (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_name VARCHAR(128) NOT NULL,
    app_key VARCHAR(64) NOT NULL UNIQUE,
    app_secret_enc TEXT NOT NULL,           -- AES 加密后的 appSecret
    status VARCHAR(16) DEFAULT 'ENABLED',   -- ENABLED / DISABLED
    bound_account_ids TEXT,                 -- JSON 数组 "[1,2,3]"
    rate_limit_per_minute INTEGER DEFAULT 60,
    expire_at DATETIME,
    last_used_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);
```

---

## 12. 相关代码

| 模块 | 路径 |
|------|------|
| 配置 | `openapi/config/OpenApiConfig.java` |
| 拦截器 | `openapi/security/OpenApiAuthInterceptor.java` |
| 应用服务 | `openapi/service/OpenAppService.java` |
| 响应信封 | `openapi/common/OpenApiResponse.java` |
| 错误码 | `openapi/common/OpenApiErrorCode.java` |
| 异常处理 | `openapi/common/OpenApiExceptionHandler.java` |
| 令牌缓存 | `openapi/common/OpenApiTokenCache.java` |
| 上下文 | `openapi/common/OpenApiContext.java` |
| 应用控制器 | `openapi/controller/OpenApiAppController.java` |
| OAuth 控制器 | `openapi/controller/OpenApiAuthController.java` |
| 账号控制器 | `openapi/controller/OpenApiAccountController.java` |
