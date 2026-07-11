# social-sdk-demo

演示工程，已通过 `social-sdk-spring-boot-starter` 自动装配闲鱼能力。

## 启动

```bash
mvn -pl social-sdk-demo -am -DskipTests install
cd social-sdk-demo
mvn -DskipTests spring-boot:run
```

默认端口：`18080`

## 主要入口

- 新控制台首页：`/`
- 旧 demo 页面：`/legacy-demo.html`
- 原 demo API：`/api/xianyu/*`
- starter 控制台 API：`/api/social-sdk/xianyu/*`

新控制台能力包括账号管理、消息发送、消息时间线、聊天接管（支持 SSE 实时事件）、商品管理和关键词规则管理。所有后端接口均使用前缀：`/api/social-sdk/xianyu`。

后端接口清单：

- `GET /health`
- `GET /accounts`
- `POST /accounts/login`
- `PUT /accounts/{id}/cookies`
- `GET /accounts/{id}/profile`
- `POST /messages/send`
- `GET /messages/timeline/{accountId}`
- `POST /chats/takeover/start`
- `POST /chats/takeover/stop/{accountId}`
- `GET /chats/takeover/status`
- `GET /chats/events/{accountId}`
- `GET /chats/stream/{accountId}`
- 商品相关接口
- 关键词规则相关接口

账号状态支持：

- `ACTIVE`
- `PENDING_VERIFY`（风控待验证）
- `FAILED`

## 前端同步

`xianyu-vue` 源码位于：`social-sdk-spring-boot-starter/xianyu-vue`

默认在 `social-sdk-demo` 的 Maven 构建阶段（`generate-resources`）会自动执行前端同步脚本。

更新前端后执行：

```bash
./scripts/sync-xianyu-vue-to-demo.sh
```

该脚本会重新构建 Vue 并覆盖到 `social-sdk-demo/src/main/resources/static`。

如果你只想跳过前端构建，可使用：

```bash
mvn -pl social-sdk-demo -DskipTests -Dsocial.sdk.skip.vue.build=true compile
```

在 CI 环境下（`CI=true`）会自动激活 `ci` profile 并跳过前端构建：

```bash
CI=true mvn -pl social-sdk-demo -DskipTests compile
```
