# Social SDK Parent

> Java 一站式社交电商平台 SDK —— 以闲鱼为起点，构建多平台、多账号、AI 驱动的开源电商运营底座。

## 📖 项目介绍

市面上有许多类似项目，但绝大部分基于 Python、Go 或 Node.js 构建。作为 Java 出身的开发者，我一直没有遇到一个真正"属于 Java 的、完整的、开放的"社交电商 SDK。

于是我们打磨了 **Social SDK**：

- **Java 全家桶**：Java 17 + Spring Boot 3.5 + MyBatis-Plus + Netty
- **嵌入式零依赖**：SQLite 开箱即用，无需额外安装数据库
- **多平台可扩展**：以闲鱼为首发，架构预留了微信、钉钉等平台的扩展位
- **AI 原生**：集成 OpenAI 兼容协议大模型，支持 AI 客服、AI 运营助手
- **运营闭环**：商品、订单、消息、自动回复、通知、钱包、监控全覆盖

### 当前进展

目前 SDK 已集成了**初级的闲鱼多账号管理平台**，可用于个人演示与学习。如需商业级部署，后续我们会基于商业底座开发完整的商业系统。

---

## 🏗️ 技术架构

### 项目结构

```
social-sdk-parent/                      # 父 POM
├── social-sdk-core/                    # SDK 核心抽象层
│   ├── provider/                       # 社交平台抽象接口（SocialProvider）
│   ├── ai/                             # AI 客户端（OpenAI 兼容协议）
│   ├── config/                         # 全局配置模型
│   ├── model/                          # 通用 Session/UserProfile/Content 等模型
│   └── constant/                       # 平台常量定义
├── social-sdk-xianyu/                  # 闲鱼平台业务层
│   ├── api/                            # 闲鱼 API 客户端集合
│   │   ├── XianyuApiFacade.java        # 聚合门面
│   │   ├── XianyuImAccsClient.java     # IM 长连接（Netty NIO + 心跳 + 自动重连）
│   │   ├── XianyuMessageApiService.java # 消息收发
│   │   ├── XianyuOrderApiService.java  # 订单 API
│   │   ├── XianyuProductApiService.java # 商品 API
│   │   ├── XianyuWalletApiService.java # 钱包 API
│   │   ├── XianyuCollectApiService.java # 收藏关注 API
│   │   └── ...                         # 登录、发布、资料、交易辅助等
│   ├── model/                          # 闲鱼业务模型（PublishItem、XianyuCredentials 等）
│   └── service/XianyuSdk.java          # 闲鱼 SDK 入口
├── social-sdk-spring-boot-starter/     # Spring Boot 自动装配
│   └── 自动装配 Chrome / XianyuProvider / Console
└── social-sdk-xianyu-manager/          # 管理控制台（后端 + 前端）
    ├── social-sdk-xianyu-manager-web/  # Vue3 + Vite 前端
    └── social-sdk-xianyu-manager/      # Spring Boot 后端
```

### 技术选型

| 层级 | 技术栈 | 说明 |
|------|--------|------|
| 运行时 | **Java 17** (LTS) | 长期支持版本 |
| 后端框架 | **Spring Boot 3.5.4** | 自动装配、WebSocket、调度任务 |
| ORM / 数据库 | **SQLite3 + MyBatis-Plus 3.5** | 嵌入式数据库，开箱即用 |
| 缓存 | **Caffeine** | 本地高性能内存缓存 |
| 长连接 | **Netty 4.1.108** | IM 客户端，NIO + IdleStateHandler 心跳 + 自动重连 |
| 前端 | **Vue 3 + Vite + Element Plus** | 现代 SPA 管理后台 |
| 认证 | **JWT** | 无状态 Token 鉴权 |
| 任务调度 | **Spring Schedule** | 自动发货、定时同步、健康检测 |
| AI | **OpenAI 兼容协议** | 任意兼容 OpenAI 的大模型（OpenAI / DeepSeek / Kimi / 自部署等） |
| 工具 | **Lombok** | 精简样板代码 |

### 架构设计原则

- **平台隔离 + 公共抽象下沉**：`social-sdk-core` 只定义抽象与通用模型，各平台独立实现，避免代码耦合
- **配置驱动**：通过 YAML 文件开关各能力：Chrome 驱动、闲鱼 Console、AI、通知等
- **AES 加密敏感配置**：Cookie、API Key、通知通道配置等一律加密存储
- **扩展性优先**：包结构预留了 `wechat`、`dingding`、`quark_netdisk`、`baidu_netdisk` 等平台扩展位

---

## ✨ 功能特性

### 1. 🖥️ 仪表盘 / 监控面板
- 首页运营概览
- 系统资源与连接状态实时可观测

### 2. 👤 多账号管理
- Cookie / 扫码登录
- 账号状态管理：`ACTIVE` / `PENDING_VERIFY` / `FAILED`
- 个人资料自动同步（头像、粉丝、关注、信用分、已卖出/已买入、店铺等级）
- Cookie 过期自动告警 + 风控触发等候机制
- 账号健康定时检测任务（`AccountHealthTask`）

### 3. 📦 商品管理
- 商品 CRUD
- 上架 / 下架 / 刷新
- 支持**实物**与**虚拟商品**两种类型
- 虚拟商品发货模板配置（卡密 / 账号 / 链接 / 文件）
- 图片 / 视频 媒体上传

### 4. 💬 消息管理
- 会话列表 + 消息历史
- 单条消息发送（文字 / 图片）
- **WebSocket 实时推送**（`MessageWebSocketHandler` + `MessageBroadcaster`）
- IM 长连接监听（`ImMessageWatcherService`）

### 5. 🤖 自动回复 / 规则引擎
- 关键词规则（`KEYWORD`）、AI 回复（`AI`）、纯自动（`AUTO`）三种模式
- 匹配类型：`CONTAINS` 等多种策略
- 优先级 + 启用/禁用控制
- 全局自动回复配置：欢迎语、兜底回复、闲置超时回复、离线回复
- 规则在线测试（`/rules/test`、`/rules/match`）

### 6. 🧠 AI 客服
- 会话自动分组（按账号 + 买家）
- 意图识别：议价、商品咨询、物流、售后、闲聊
- 多模式策略：`AUTO`（全自动）、`ASSIST`（AI 建议运营一键发）、`HYBRID`（闲聊自动 + 议价辅助）
- 独立知识库（商品 FAQ + 通用话术）
- 议价策略：底价比例、降价幅度、最多降价次数
- 风控：每小时最大自动回复数、敏感意图自动转人工
- 话术风格：友好 / 专业 / 休闲 / 幽默
- 生效时段控制
- 每日效果统计

### 7. 🏭 AI 运营助手
- 批量上品任务（`BATCH_CREATE`）
- 多账号同步（`MULTI_ACCOUNT_SYNC`）
- AI 生成优化建议（定价、刷新时间、标题优化）
- 建议采纳跟踪
- 运营知识库

### 8. 📨 系统通知
- 通知通道：**Email (SMTP)**、**Webhook**（企业微信 / 钉钉 / 飞书 / 通用）
- 通知模板按场景变量渲染（`TemplateRenderer`）
- 订阅规则：ALL / CUSTOM（按通道、按接收人、按账号）
- 投递日志完整记录
- 站内收件箱（未读计数 + 已读状态）

### 9. 🛒 订单管理
- 订单列表 + 状态跟踪
- 发货操作
- **虚拟商品自动发货**：
  - 卡密池（`virtual_card_pool`）管理
  - 自动发货任务队列（`virtual_ship_task`）
  - 支付后延时发货（防风控）
  - N 天后自动确认收货
  - 发货后站内通知运营

### 10. 💰 钱包资产
- 余额、冻结金额、可提现金额、总资产
- 交易记录流水
- 绑定支付宝 / 银行卡信息

### 11. ⭐ 收藏 / 关注
- 收藏商品管理
- 关注账号管理

### 12. ☁️ 网盘存储（虚拟发货扩展）
- 百度网盘 / 夸克网盘双 provider
- 文件管理 + 分享链接 + 提取码
- 上传状态跟踪

### 13. 📜 审计日志
- AOP 切面自动记录关键操作
- 操作人、资源、操作类型、IP、详情

### 14. 🤖 AI 厂商 / 模型管理
- 任意 OpenAI 兼容协议厂商配置（`ai_provider`）
- 多模型管理（TEXT / IMAGE / VIDEO）
- 能力标签（streaming / tools / thinking / image_input）

---

## 🚀 快速开始

### 1. 编译整个项目

```bash
cd social-sdk-parent
mvn clean install -DskipTests
```

### 2. 准备 Chrome 驱动（登录用）

下载与 Chrome 版本对应的 [ChromeDriver](https://googlechromelabs.github.io/chrome-for-testing/)，路径配置在 `application.yml` 中。

### 3. 配置 `application.yml`

```yaml
social-sdk:
  chrome:
    driver-path: /path/to/chromedriver
    executable-path: /path/to/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing
  xianyu:
    enabled: true
  console:
    xianyu:
      enabled: true
      sqlite-path: ./data/xianyu-manager.db
      auto-init-schema: true
      auto-open-verification-url: true
```

### 4. 启动管理后台

```bash
cd social-sdk-xianyu-manager
mvn spring-boot:run
```

### 5. 访问

- 管理后台：`http://localhost:8080`
- 默认管理员 / 密码：`admin` / `admin123`

> ⚠️ 首次启动会自动执行 `db/schema.sql` 初始化 SQLite 表结构。

---

## 🔌 API 一览（REST 接口前缀）

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `POST /api/auth/login` | 管理员登录 |
| 认证 | `GET /api/auth/profile` | 获取当前用户信息 |
| 账号 | `GET /api/accounts` | 账号列表 |
| 账号 | `POST /api/accounts/login` | Cookie / 扫码登录 |
| 账号 | `PUT /api/accounts/{id}/cookies` | 更新 Cookie |
| 账号 | `GET /api/accounts/{id}/profile` | 同步个人资料 |
| 账号 | `PUT /api/accounts/{id}/status` | 更新状态 |
| 商品 | `GET /api/products` | 商品列表（分页） |
| 商品 | `POST /api/products` | 创建商品 |
| 商品 | `PUT /api/products/{id}` | 编辑商品 |
| 商品 | `POST /api/products/{id}/shelf-on` | 上架 |
| 商品 | `POST /api/products/{id}/shelf-off` | 下架 |
| 消息 | `GET /api/messages/sessions` | 会话列表 |
| 消息 | `GET /api/messages/history` | 消息历史 |
| 消息 | `POST /api/messages/send` | 发送消息 |
| 消息 | `WS /ws/messages` | 实时推送 |
| 订单 | `GET /api/orders` | 订单列表 |
| 订单 | `POST /api/orders/{id}/delivery` | 发货 |
| 规则 | `GET /api/rules` | 规则列表 |
| 规则 | `POST /api/rules` | 创建规则 |
| 规则 | `POST /api/rules/test` | 测试规则匹配 |
| 规则 | `POST /api/rules/auto-reply` | 自动回复 |
| AI 厂商 | `GET/POST /api/ai/providers` | 厂商管理 |
| AI 模型 | `GET/POST /api/ai/models` | 模型管理 |
| AI 客服 | `GET /api/cs/sessions` | 客服会话列表 |
| AI 客服 | `GET /api/cs/messages` | 客服消息记录 |
| AI 运营 | `GET /api/ops/tasks` | 运营任务列表 |
| AI 运营 | `GET /api/ops/suggestions` | 运营建议 |
| 通知 | `GET /api/notify/messages` | 站内通知 |
| 通知 | `GET/POST /api/notify/channels` | 通知通道 |
| 通知 | `GET/POST /api/notify/templates` | 通知模板 |
| 通知 | `GET/POST /api/notify/subscriptions` | 订阅规则 |
| 通知 | `GET /api/notify/logs` | 投递日志 |
| 钱包 | `GET /api/wallet` | 钱包资产 |
| 收藏 | `GET /api/collect` | 收藏关注列表 |
| 监控 | `GET /api/monitor` | 监控面板 |
| 审计 | `GET /api/audit/logs` | 审计日志 |

---

## 🗄️ 数据库

采用**嵌入式 SQLite3**，数据库文件位于 `./data/xianyu-manager.db`。

核心数据表（自动初始化）：

| 表名 | 用途 |
|------|------|
| `admin_user` | 管理员用户 |
| `xianyu_account` | 闲鱼账号（Cookie、状态、个人资料） |
| `xianyu_product` | 商品 |
| `xianyu_message` | 聊天记录 |
| `xianyu_order` | 订单（含虚拟发货字段） |
| `xianyu_keyword_rule` | 关键词 / 自动回复规则 |
| `xianyu_auto_reply_config` | 全局自动回复配置 |
| `xianyu_wallet` / `xianyu_wallet_transaction` | 钱包资产 / 交易流水 |
| `xianyu_collect` | 收藏关注 |
| `ai_provider` / `ai_model` | AI 厂商 / 模型 |
| `notify_channel` / `notify_template` / `notify_subscription` / `notify_log` / `notify_message` | 通知体系全套 |
| `virtual_card_pool` / `virtual_ship_task` / `virtual_ship_config` | 虚拟卡密 / 发货任务 / 配置 |
| `ai_cs_session` / `ai_cs_message` / `ai_cs_knowledge` / `ai_cs_policy` / `ai_cs_daily_stats` | AI 客服全套 |
| `ai_ops_task` / `ai_ops_suggestion` / `ai_ops_knowledge` | AI 运营全套 |
| `cloud_storage_account` / `cloud_storage_file` | 网盘存储 |
| `audit_log` | 审计日志 |

---

## 🔐 环境变量 / 配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `XIANYU_JWT_SECRET` | JWT 签名密钥 | `defaultSecretKeyForDevelopmentOnlyChangeInProduction` |
| `XIANYU_CRYPTO_SECRET` | Cookie 加密密钥 | `defaultCryptoSecretKeyForDevelopmentOnly` |

> **⚠️ 生产环境务必修改上述密钥。**

---

## 🛣️ 后续路线图

### ✅ 已完成
- [x] 闲鱼多账号管理（Cookie 登录 / 状态管理 / 健康检测）
- [x] 商品 CRUD + 上下架
- [x] 消息收发 + WebSocket 实时推送
- [x] 订单管理 + 发货
- [x] 关键词自动回复规则引擎
- [x] AI 客服（知识库 + 意图识别 + 议价策略 + 多模式策略）
- [x] 系统通知体系（邮件 / Webhook / 模板 / 订阅 / 日志）
- [x] 钱包资产 / 收藏关注 / 审计日志
- [x] 虚拟商品自动发货（卡密池 + 任务队列）
- [x] 虚拟商品网盘发货（百度 / 夸克）
- [x] AI 运营助手（批量上品 / 同步 / 建议）
- [x] AI 厂商 / 模型管理（OpenAI 兼容）

### 🚧 进行中 / 计划中
- [ ] 闲鱼自动回复日志（按会话维度完整记录匹配路径 + 命中规则 + 发送结果）
- [ ] 商品同步管理（批量同步多账号商品、一键上下架）
- [ ] 定时任务中心（可视化 Cron 调度：定时刷新、定时回复、定时清理）
- [ ] 系统通知对接优化（与业务事件深度打通）
- [ ] 接入更多闲鱼风控场景处理
- [ ] 账号多 IP 代理支持

### 🔮 未来规划
- [ ] **微信平台** SDK 抽象与实现
- [ ] **钉钉平台** SDK 抽象与实现
- [ ] 跨平台数据统一聚合视图
- [ ] 多租户 / 团队权限（当前为单管理员）
- [ ] 商业级部署方案（MySQL / PostgreSQL / Redis 等外置组件）
- [ ] 抖音 / 小红书 等新平台调研与接入
- [ ] 多语言国际化（i18n）
- [ ] 移动端管理 App（Flutter / uni-app）

---

## 🏢 商业版

当前开源版本适用于**个人演示与学习**。如需**商业级、生产级**的完整系统，可参考我们的商业底座：

- [brick-bootkit-springboot](https://github.com/v18268185209/brick-bootkit-springboot.git) —— 商业后端框架
- [brick-bootkit-admin](https://github.com/v18268185209/brick-bootkit-admin.git) —— 商业管理后台

也欢迎进入商业专用群咨询：**第一批售价 199 元，可免费获得商业底层框架系统授权**。

---

## 💬 交流群

<div style="width:200px;height:auto;">
<img src="https://raw.githubusercontent.com/v18268185209/social-sdk-parent/refs/heads/main/docs/qqqunliao.jpg" width="200">
</div>

---

## 📄  License

见 [LICENSE](./LICENSE)

---

## 🙏 欢迎贡献

欢迎提交 Issue、PR 或参与讨论，一起把这个属于 Java 的开源社交电商 SDK 做得更好！


### 打赏【虚拟币地址】
