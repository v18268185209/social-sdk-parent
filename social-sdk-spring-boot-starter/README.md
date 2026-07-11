# social-sdk-spring-boot-starter

该模块用于统一封装各平台能力，当前包含：

- `SocialProvider` 自动装配（core/chrome/xianyu）
- 闲鱼业务控制台后端（多账号、商品、消息收发、聊天接管、关键词回复规则）
- 闲鱼控制台前端源码：`xianyu-vue/`（Vue3 + Vite，包含消息收发/聊天接管）

## 包结构规划（支持后续扩展 wechat/dingding）

- `com.socialsdk.starter.platform.common`：跨平台通用模型
- `com.socialsdk.starter.platform.xianyu`：闲鱼平台能力
- 未来可新增：
  - `com.socialsdk.starter.platform.wechat`
  - `com.socialsdk.starter.platform.dingding`

保持“平台隔离 + 公共抽象下沉”的结构，避免后续平台代码互相耦合。

## 启用闲鱼控制台能力

```yaml
social-sdk:
  chrome:
    driver-path: /Users/vim/Desktop/codes/chromedriver-mac-arm64/chromedriver
    executable-path: /Users/vim/Desktop/codes/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing
  xianyu:
    enabled: true

  console:
    xianyu:
      enabled: true
      sqlite-path: ./data/social-sdk-xianyu.db
      auto-init-schema: true
      auto-open-verification-url: true
```

## 闲鱼控制台接口

接口前缀：`/api/social-sdk/xianyu`

- 健康检查：`GET /health`
- 账号：`GET /accounts`、`POST /accounts/login`、`PUT /accounts/{id}/cookies`、`GET /accounts/{id}/profile`、`PUT /accounts/{id}/status`、`DELETE /accounts/{id}`
- 消息收发：`POST /messages/send`、`GET /messages/timeline/{accountId}`
- 聊天接管：`POST /chats/takeover/start`、`POST /chats/takeover/stop/{accountId}`、`GET /chats/takeover/status`
- 聊天事件/SSE：`GET /chats/events/{accountId}`、`GET /chats/stream/{accountId}`
- 商品：`/products`
- 规则：`/rules`、`/rules/match`

自动回复可结合关键词规则使用：通过规则接口维护命中关键词与回复内容，消息流入后可按规则匹配并发送回复，也可在聊天接管场景下由人工处理。

账号状态约定：

- `ACTIVE`：可正常使用
- `PENDING_VERIFY`：触发风控，等待人工验证
- `FAILED`：登录/刷新失败

## SQLite 表

启动时自动创建：

- `xianyu_accounts`
- `xianyu_products`
- `xianyu_keyword_rules`
