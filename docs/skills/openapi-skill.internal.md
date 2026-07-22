# 闲鱼管理器 OpenAPI Internal Skill

> 本文件为项目内部版 skill，用于本仓库开发、排查、扩展 OpenAPI。公共发布版请使用 `docs/skills/openapi-skill.md`。

## 目的

当需要接入、调试、生成客户端代码、编写接口调用示例、排查鉴权/作用域/限流问题，或基于本项目对外开放能力设计第三方集成时，使用本 skill。

本 skill 基于当前仓库的 OpenAPI v1 实现与文档：

- 代码目录：`social-sdk-xianyu-manager/src/main/java/cn/net/rjnetwork/xianyu/manager/openapi`
- 主要文档：`docs/openapi.md`
- Swagger UI：`http://<host>:8080/openapi/v1/docs`
- OpenAPI JSON：`http://<host>:8080/openapi/v1/openapi.json`
- Base URL：`http://<host>:8080/openapi/v1`

## 什么时候使用

用户提出以下需求时，优先使用本 skill：

- “帮我接入 OpenAPI / 开放平台 / 第三方接口”
- “怎么获取 token / appKey / appSecret”
- “写一个调用商品/订单/消息/钱包接口的示例”
- “根据 OpenAPI 写 SDK / curl / Postman / Python / JavaScript 调用”
- “排查 401 / 403 / 429 / OPEN_ACCOUNT_FORBIDDEN / OPEN_INVALID_TOKEN”
- “检查某个开放接口是否暴露敏感字段”
- “新增一个 OpenAPI 端点并保持鉴权、响应格式一致”
- “根据 openapi.md 补全文档或接口清单”

## 核心规则

1. OpenAPI v1 业务接口统一走 `/openapi/v1/**`。
2. 内部应用管理接口走 `/api/openapi/apps`，需要管理后台 JWT，不属于第三方 Bearer token 范围。
3. 除 `POST /openapi/v1/oauth/token` 外，所有 OpenAPI v1 业务接口都需要：

   ```http
   Authorization: Bearer <accessToken>
   ```

4. 业务响应统一为 `OpenApiResponse<T>`：

   ```json
   {
     "code": "OK",
     "message": "Success",
     "data": {},
     "timestamp": 1721365800
   }
   ```

5. 成功判断必须以 `code === "OK"` 为准，不要只判断 HTTP 200。
6. 账号作用域通过 `OpenApp.boundAccountIds` 控制：
   - 空列表 = 可访问全部账号；
   - 非空 = 只能访问白名单账号；
   - 越权返回 `OPEN_ACCOUNT_FORBIDDEN`。
7. 对外 VO 必须脱敏，不允许暴露：
   - `cookieHeader`
   - `cookiesJson`
   - `imCookieHeader`
   - `imAccessToken`
   - `appSecret` 明文（二次查询时）
   - 云盘 token / shareLink / extractCode 等敏感字段
   - 内部错误堆栈或账号风控细节
8. 新增接口时必须复用：
   - `OpenApiResponse`
   - `OpenApiException`
   - `OpenApiErrorCode`
   - `OpenApiContext.getOpenApp()`
   - `OpenAppService.assertAccountAccessible(...)`

## 接入流程

### 1. 管理员创建开放应用

内部管理后台接口：

```http
POST /api/openapi/apps
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "appName": "第三方应用名称",
  "boundAccountIds": [1, 2, 3],
  "rateLimitPerMinute": 60
}
```

响应中会返回 `appKey` 和 `appSecret`。`appSecret` 只应明文返回一次，调用方必须保存。

### 2. 第三方换取访问令牌

```http
POST /openapi/v1/oauth/token
Content-Type: application/json

{
  "appKey": "ak_xxxxxxxx",
  "appSecret": "xxxxxxxxxxxxxxxx"
}
```

成功响应：

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

令牌有效期为 7200 秒。服务重启后内存 token 失效，需要重新换取。

### 3. 调用业务接口

```http
GET /openapi/v1/accounts
Authorization: Bearer <accessToken>
```

## 错误码处理

| code | HTTP 状态 | 含义 | 处理建议 |
|---|---:|---|---|
| `OPEN_UNAUTHORIZED` | 401 | 未提供 Bearer token | 检查 Authorization 请求头 |
| `OPEN_INVALID_TOKEN` | 401 | token 无效、过期或服务重启后丢失 | 重新调用 `/oauth/token` |
| `OPEN_APP_DISABLED` | 403 | 应用被禁用 | 管理后台启用应用 |
| `OPEN_APP_EXPIRED` | 403 | 应用过期 | 延长应用有效期或重建应用 |
| `OPEN_RATE_LIMIT` | 429 | 超过每分钟限流 | 降低调用频率，指数退避重试 |
| `OPEN_ACCOUNT_FORBIDDEN` | 403 | 访问非绑定账号 | 检查 `boundAccountIds` 或更换账号 ID |
| `OPEN_INVALID_PARAM` | 400 | 参数错误 | 检查 query/path/body |
| `OPEN_NOT_FOUND` | 404 | 资源不存在或不可见 | 检查资源 ID 与账号作用域 |
| `OPEN_INTERNAL` | 500 | 服务内部错误 | 查后端日志 |

## 当前 OpenAPI 端点清单

### OAuth

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/openapi/v1/oauth/token` | 无 | 用 `appKey` + `appSecret` 换取 Bearer token |

### 账号

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/accounts` | `accountId?` | 查询账号列表，按应用绑定账号过滤 |
| GET | `/openapi/v1/accounts/{id}` | `id` | 查询账号详情，并校验账号作用域 |

### 商品与本地商品

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/products` | `accountId?` | 查询闲鱼商品列表 |
| GET | `/openapi/v1/products/{id}` | `id` | 查询闲鱼商品详情 |
| GET | `/openapi/v1/local-products` | `accountId?`, `status?`, `keyword?` | 查询本地商品列表 |
| GET | `/openapi/v1/local-products/{id}` | `id` | 查询本地商品详情 |

### 消息与订单

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/messages` | `accountId?` | 查询消息列表 |
| GET | `/openapi/v1/messages/{id}` | `id` | 查询消息详情 |
| GET | `/openapi/v1/orders` | `accountId?` | 查询订单列表 |
| GET | `/openapi/v1/orders/{id}` | `id` | 查询订单详情 |

### 钱包

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/wallets` | `accountId?` | 查询钱包列表 |
| GET | `/openapi/v1/wallets/{accountId}` | `accountId` | 按账号查询钱包 |
| GET | `/openapi/v1/wallets/{accountId}/transactions` | `accountId` | 查询账号钱包流水 |

### 收藏关注

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/collects` | `accountId?` | 查询收藏/关注列表 |
| GET | `/openapi/v1/collects/{id}` | `id` | 查询收藏/关注详情 |

### 关键词与自动回复

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/keyword-rules` | `accountId?` | 查询关键词规则 |
| GET | `/openapi/v1/keyword-rules/{id}` | `id` | 查询关键词规则详情 |
| GET | `/openapi/v1/auto-reply-configs` | `accountId?` | 查询自动回复配置 |
| GET | `/openapi/v1/auto-reply-configs/{id}` | `id` | 查询自动回复配置详情 |
| GET | `/openapi/v1/auto-reply-logs` | `accountId?`, `replyType?`, `matched?`, `from?`, `to?` | 查询自动回复日志 |

### AI

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/ai/providers` | `enabled?` | 查询 AI 厂商目录，脱敏返回 |
| GET | `/openapi/v1/ai/models` | 无 | 查询 AI 模型目录，脱敏返回 |
| GET | `/openapi/v1/ai/ops/tasks` | `accountId?` | 查询 AI 运营任务 |
| GET | `/openapi/v1/ai/ops/tasks/{id}` | `id` | 查询 AI 运营任务详情 |
| GET | `/openapi/v1/ai/ops/suggestions` | `accountId?` | 查询 AI 运营建议 |
| GET | `/openapi/v1/ai/ops/suggestions/{id}` | `id` | 查询 AI 运营建议详情 |
| GET | `/openapi/v1/ai/cs/sessions` | `accountId?` | 查询 AI 客服会话 |
| GET | `/openapi/v1/ai/cs/sessions/{id}` | `id` | 查询 AI 客服会话详情 |
| GET | `/openapi/v1/ai/cs/knowledge` | `accountId?` | 查询 AI 客服知识库 |
| GET | `/openapi/v1/ai/cs/knowledge/{id}` | `id` | 查询 AI 客服知识详情 |
| GET | `/openapi/v1/ai/cs/policies` | `accountId?` | 查询 AI 客服策略 |
| GET | `/openapi/v1/ai/cs/policies/{id}` | `id` | 查询 AI 客服策略详情 |

### 通知

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/notify/messages` | `accountId?` | 查询站内通知 |
| GET | `/openapi/v1/notify/messages/{id}` | `id` | 查询站内通知详情 |
| GET | `/openapi/v1/notify/templates` | 无 | 查询通知模板目录 |
| GET | `/openapi/v1/notify/channels` | 无 | 查询通知通道目录，配置脱敏 |
| GET | `/openapi/v1/notify/logs` | `accountId?`, `scenario?`, `status?`, `from?`, `to?` | 查询通知发送日志 |
| GET | `/openapi/v1/notify/subscriptions` | `scenario?`, `channelId?` | 查询通知订阅 |
| GET | `/openapi/v1/notify/subscriptions/{id}` | `id` | 查询通知订阅详情 |

### 监控、市场与买家画像

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/monitor/tasks` | `accountId?`, `status?` | 查询监控任务 |
| GET | `/openapi/v1/monitor/tasks/{id}` | `id` | 查询监控任务详情 |
| GET | `/openapi/v1/monitor/results` | `accountId?`, `taskId?`, `from?`, `to?` | 查询监控结果 |
| GET | `/openapi/v1/market/daily-stats` | `keyword?`, `date?` | 查询每日市场统计 |
| GET | `/openapi/v1/market/sellers` | `accountId?`, `keyword?` | 查询卖家画像 |
| GET | `/openapi/v1/market/price-history` | `accountId?`, `itemId?`, `keyword?` | 查询价格历史 |
| GET | `/openapi/v1/buyer/profiles` | `accountId?` | 查询买家画像 |
| GET | `/openapi/v1/buyer/profiles/{id}` | `id` | 查询买家画像详情 |

### 虚拟发货与云盘

| 方法 | 路径 | 常用参数 | 说明 |
|---|---|---|---|
| GET | `/openapi/v1/virtual-ship/configs` | `accountId?` | 查询虚拟发货配置 |
| GET | `/openapi/v1/virtual-ship/configs/{id}` | `id` | 查询虚拟发货配置详情 |
| GET | `/openapi/v1/virtual-ship/tasks` | `accountId?` | 查询虚拟发货任务 |
| GET | `/openapi/v1/virtual-ship/tasks/{id}` | `id` | 查询虚拟发货任务详情 |
| GET | `/openapi/v1/cloud/accounts` | `accountId?` | 查询云盘账号，token 脱敏 |
| GET | `/openapi/v1/cloud/accounts/{id}` | `id` | 查询云盘账号详情 |
| GET | `/openapi/v1/cloud/files` | `accountId?` | 查询云盘文件，分享信息脱敏 |
| GET | `/openapi/v1/cloud/files/{id}` | `id` | 查询云盘文件详情 |

## 调用示例

### curl：获取 token 并查询账号

```bash
BASE_URL="http://localhost:8080"
APP_KEY="ak_xxxxxxxx"
APP_SECRET="xxxxxxxxxxxxxxxx"

TOKEN=$(curl -s -X POST "$BASE_URL/openapi/v1/oauth/token" \
  -H 'Content-Type: application/json' \
  -d "{\"appKey\":\"$APP_KEY\",\"appSecret\":\"$APP_SECRET\"}" \
  | jq -r '.data.accessToken')

curl -s "$BASE_URL/openapi/v1/accounts" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### JavaScript：统一客户端

```js
const baseUrl = 'http://localhost:8080/openapi/v1'

async function getToken(appKey, appSecret) {
  const res = await fetch(`${baseUrl}/oauth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ appKey, appSecret })
  })
  const json = await res.json()
  if (json.code !== 'OK') throw new Error(`${json.code}: ${json.message}`)
  return json.data.accessToken
}

async function openapiGet(token, path, params = {}) {
  const url = new URL(`${baseUrl}${path}`)
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v)
  })
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const json = await res.json()
  if (json.code !== 'OK') throw new Error(`${json.code}: ${json.message}`)
  return json.data
}

const token = await getToken('ak_xxxxxxxx', 'xxxxxxxxxxxxxxxx')
const accounts = await openapiGet(token, '/accounts')
const products = await openapiGet(token, '/products', { accountId: accounts[0]?.id })
console.log({ accounts, products })
```

### Python：查询订单

```python
import requests

base_url = 'http://localhost:8080/openapi/v1'
app_key = 'ak_xxxxxxxx'
app_secret = 'xxxxxxxxxxxxxxxx'

def unwrap(resp):
    payload = resp.json()
    if payload.get('code') != 'OK':
        raise RuntimeError(f"{payload.get('code')}: {payload.get('message')}")
    return payload.get('data')

token = unwrap(requests.post(
    f'{base_url}/oauth/token',
    json={'appKey': app_key, 'appSecret': app_secret},
))['accessToken']

orders = unwrap(requests.get(
    f'{base_url}/orders',
    headers={'Authorization': f'Bearer {token}'},
    params={'accountId': 1},
))

print(orders)
```

## 新增 OpenAPI 接口时的实现模板

### Controller 模板

```java
@RestController
@RequestMapping("/openapi/v1/example")
public class OpenApiExampleController {

    private final ExampleMapper exampleMapper;
    private final OpenAppService openAppService;

    public OpenApiExampleController(ExampleMapper exampleMapper, OpenAppService openAppService) {
        this.exampleMapper = exampleMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiExampleVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);

        List<OpenApiExampleVO> result = exampleMapper.selectList(new LambdaQueryWrapper<ExampleEntity>())
                .stream()
                .filter(e -> bound.isEmpty() || bound.contains(e.getAccountId()))
                .filter(e -> accountId == null || accountId.equals(e.getAccountId()))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiExampleVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        ExampleEntity entity = exampleMapper.selectById(id);
        if (entity == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "资源不存在");
        }
        openAppService.assertAccountAccessible(app, entity.getAccountId());
        return OpenApiResponse.ok(toVo(entity));
    }

    private OpenApiExampleVO toVo(ExampleEntity e) {
        OpenApiExampleVO vo = new OpenApiExampleVO();
        vo.setId(e.getId());
        vo.setAccountId(e.getAccountId());
        return vo;
    }
}
```

### 新增接口检查清单

新增或修改 OpenAPI 端点前后必须检查：

- [ ] 路径是否在 `/openapi/v1/**` 下。
- [ ] 是否返回 `OpenApiResponse.ok(data)`。
- [ ] 是否用 `OpenApiException(OpenApiErrorCode.xxx, message)` 抛业务错误。
- [ ] 列表接口是否按 `boundAccountIds` 过滤。
- [ ] 详情接口是否根据资源反查 `accountId` 并调用 `assertAccountAccessible`。
- [ ] VO 是否脱敏，不能直接返回数据库实体。
- [ ] `appSecret`、Cookie、Token、密文配置、风控错误、堆栈不能出现在响应中。
- [ ] 是否需要补充 `docs/openapi.md` 与本 skill 的端点清单。
- [ ] 编译校验：`mvn -pl social-sdk-xianyu-manager -am -DskipTests -Dskip.frontend=true compile`。

## 排查流程

### 401：未授权或 token 无效

1. 检查是否带了请求头：`Authorization: Bearer <token>`。
2. 确认 token 是否来自当前服务实例。服务重启后内存 token 会失效。
3. 重新调用 `/openapi/v1/oauth/token` 获取 token。
4. 如果仍失败，检查 `OpenApiAuthInterceptor` 和 `OpenApiTokenCache`。

### 403：账号越权

1. 确认请求参数里的 `accountId`。
2. 查询内部应用配置的 `boundAccountIds`。
3. 如果访问详情接口，确认资源所属账号是否在绑定列表内。
4. 空 `boundAccountIds` 代表不限制；非空时必须包含目标账号。

### 429：限流

1. 查询应用 `rateLimitPerMinute`。
2. 按 appKey 粒度统计，多个调用方共享同一 appKey 会互相影响。
3. 客户端应做指数退避或本地限速。

### 500：内部错误

1. 先看后端日志中的 `GlobalExceptionHandler` 或 `OpenApiExceptionHandler`。
2. 常见原因是旧库缺表/缺列、Mapper 查询字段不一致、VO 映射空指针。
3. 如果是缺表，优先补 schema 文件和 `DatabaseInitializer` 旧库迁移兜底。

## 与管理后台 API 的区别

| 类型 | 路径 | 鉴权 | 使用者 | 说明 |
|---|---|---|---|---|
| 管理后台 API | `/api/**` | 管理员 JWT | 内部前端 | 可读写敏感配置，权限更高 |
| OpenAPI | `/openapi/v1/**` | appKey/appSecret 换取 Bearer | 第三方应用 | 只暴露脱敏后的业务数据，并受账号白名单和限流约束 |
| 应用管理 | `/api/openapi/apps` | 管理员 JWT | 管理员 | 创建/查询开放应用，返回 appKey/appSecret |

## 文档维护约定

- `docs/openapi.md` 是面向接入方的接口说明。
- `docs/skills/openapi-skill.internal.md` 是面向项目内部开发者的操作型 skill，包含排查流程、实现模板和安全约束。
- `docs/skills/openapi-skill.md` 是公共发布版 skill，不应包含内部源码路径、Java 类名、数据库迁移或构建命令。
- 新增 Controller 后，需要同步更新：
  1. Swagger 注解或 OpenAPI JSON 输出；
  2. `docs/openapi.md`；
  3. 内部版和公共版 skill 的端点清单；
  4. 必要时补充调用示例。
