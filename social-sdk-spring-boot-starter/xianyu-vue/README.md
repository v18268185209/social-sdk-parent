# xianyu-vue

`social-sdk-spring-boot-starter` 内置的闲鱼控制台前端源码（Vue3 + Vite）。

## 功能

- 账号管理：登录、Cookie 更新、资料同步、状态维护与删除。
- 商品管理：商品列表、新增、编辑与删除。
- 关键词规则：规则列表、新增、编辑、删除与匹配测试。
- 消息收发：支持发送消息、拉取消息时间线、会话接管开始/停止/状态查询、查看事件列表，以及通过 SSE 实时订阅事件流。

## 开发

```bash
npm install
npm run dev
```

默认会将 `/api` 代理到 `http://localhost:8080`。

## 打包

```bash
npm run build
```

输出目录为 `dist/`，可按需部署到任意静态站点，或者复制到宿主应用静态资源目录。

## 后端接口

前端对接接口前缀：`/api/social-sdk/xianyu`

- `GET /health`
- `GET /accounts`
- `POST /accounts/login`
- `PUT /accounts/{id}/cookies`
- `GET /accounts/{id}/profile`
- `PUT /accounts/{id}/status`
- `DELETE /accounts/{id}`
- `POST /messages/send`
- `GET /messages/timeline/{accountId}`
- `POST /chats/takeover/start`
- `POST /chats/takeover/stop/{accountId}`
- `GET /chats/takeover/status`
- `GET /chats/events/{accountId}`
- `GET /chats/stream/{accountId}`
- `GET /products`
- `POST /products`
- `PUT /products/{id}`
- `DELETE /products/{id}`
- `GET /rules`
- `POST /rules`
- `PUT /rules/{id}`
- `DELETE /rules/{id}`
- `POST /rules/match`
