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
- **跨平台部署**：Spring Boot JAR / Docker 容器 / Electron 桌面 / iOS / Android

### 当前进展

目前 SDK 已集成了**完整的闲鱼多账号管理平台**，涵盖 AI 客服、AI 运营、市场情报、买家画像、自动发货、监控爬虫、Chrome 容器池、代理池等企业级能力。可用于个人演示与学习。如需商业级部署，后续我们会基于商业底座开发完整的商业系统。

---

## 🏗️ 技术架构


### 当前版本
```
<dependency>
<groupId>cn.net.rjnetwork</groupId>
<artifactId>social-sdk-parent</artifactId>
<version>0.0.2</version>
</dependency>



```



### 项目结构

```
social-sdk-parent/                          # 父 POM
├── social-sdk-core/                        # SDK 核心抽象层
│   ├── provider/                           # 社交平台抽象接口（SocialProvider）
│   ├── ai/                                 # AI 客户端（OpenAI 兼容协议）
│   ├── config/                             # 全局配置模型
│   ├── model/                              # 通用 Session/UserProfile/Content 等模型
│   ├── constant/                           # 平台常量定义
│   └── exception/                          # 异常体系
├── social-sdk-xianyu/                      # 闲鱼平台业务层
│   ├── api/                                # 闲鱼 API 客户端集合
│   │   ├── XianyuApiFacade.java            # 聚合门面
│   │   ├── XianyuMtopApiClient.java        # MTOP API 请求构造
│   │   ├── XianyuImAccsClient.java         # IM 长连接（Netty NIO + 心跳 + 自动重连）
│   │   ├── XianyuLoginApiService.java      # 登录 API
│   │   ├── XianyuPublishApiService.java    # 发布商品
│   │   ├── XianyuProductApiService.java    # 商品 API
│   │   ├── XianyuMessageApiService.java    # 消息 API
│   │   ├── XianyuOrderApiService.java      # 订单 API
│   │   ├── XianyuWalletApiService.java     # 钱包 API
│   │   ├── XianyuProfileApiService.java    # 用户资料 API
│   │   ├── XianyuCollectApiService.java    # 收藏 API
│   │   ├── XianyuTradeAuxApiService.java    # 交易辅助 API
│   │   ├── XianyuMediaUploadApiService.java # 媒体上传
│   │   ├── XianyuCaptchaService.java       # 验证码
│   │   └── ...                             # 业务辅助/调试类
│   ├── model/                              # 闲鱼业务模型（PublishItem、XianyuCredentials 等）
│   └── service/XianyuSdk.java              # 闲鱼 SDK 入口
├── social-sdk-cdp-auth/                    # CDP 滑块验证码破解引擎
│   ├── service/XianyuCaptchaSolver.java    # 验证码求解主类
│   ├── service/SolverCdpSocket.java        # CDP WebSocket 连接
│   ├── service/SliderTrajectoryEngine.java  # 滑块轨迹引擎
│   ├── service/SliderDistanceCalculator.java # 滑块距离计算
│   ├── service/SliderAntiDetect.java       # 反检测模拟
│   └── model/CaptchaResult.java            # 验证码结果
├── social-sdk-proxys/                      # 统一代理池 SDK
│   ├── core/                               # 代理池管理器/健康检查/自动配置
│   ├── provider/                           # SmartProxy/快骑/阿布云/奇迹等提供商
│   └── persistence/                        # SQLite 账号-代理绑定持久化
├── social-sdk-chrome/                      # 每账号 Chrome 容器池
│   ├── core/                               # Profile管理/Session/健康检查/端口池
│   ├── config/                             # Chrome 配置属性
│   └── model/                              # 浏览器指纹配置
├── social-sdk-spring-boot-starter/         # Spring Boot 自动装配
│   ├── config/                             # 核心自动配置
│   ├── platform/xianyu/                    # 闲鱼控制台配置 + Controller + Service + Repository
│   └── platform/common/                    # 全局异常 + 统一响应
├── social-sdk-xianyu-manager/              # 管理控制台（后端 + 前端）
│   ├── src/main/java/.../                  # 66+ 包：Account/Product/Order/Message/AI/CS/Ops/
│   │                                       # Notify/Wallet/Collect/Virtual/Market/Monitor/Buyer/
│   │                                       # Rule/Chrome/Proxy/OpenApi/Audit/Config/Task...
│   └── src/main/resources/db/              # 4 套 Schema（SQLite/MySQL/PostgreSQL/Proxy）
├── social-sdk-xianyu-manager-web/          # Vue3 + Vite + Element Plus 前端
│   ├── src/views/                          # 31 个页面（独立 Node.js 项目，不通过 Maven 构建）
│   │   ├── dashboard/                      # 首页仪表盘
│   │   ├── data-board/                     # 实时数据大屏
│   │   ├── landing/                        # 欢迎引导页
│   │   ├── accounts/                       # 账号管理
│   │   ├── products/                       # 商品列表
│   │   ├── product/                        # 单个商品
│   │   ├── messages/                       # 消息管理
│   │   ├── orders/                         # 订单管理
│   │   ├── rules/                          # 规则引擎
│   │   ├── wallet/                         # 钱包
│   │   ├── collect/                        # 收藏
│   │   ├── ai/                             # AI 聊天
│   │   ├── aiCs/                           # AI 客服
│   │   ├── aiOps/                          # AI 运营
│   │   ├── buyer/                          # 买家画像
│   │   ├── market/                         # 市场情报
│   │   ├── monitor/                        # 监控面板
│   │   ├── notify/                         # 通知管理
│   │   ├── virtualShip/                    # 虚拟发货
│   │   ├── cloudStorage/                   # 网盘存储
│   │   ├── chrome/                         # Chrome 容器
│   │   ├── proxy/                          # 代理池
│   │   ├── circuitBreaker/                 # 熔断器
│   │   ├── replyLogs/                      # 回复日志
│   │   ├── reviews/                        # 评价管理
│   │   ├── tasks/                          # 定时任务
│   │   ├── audit/                          # 审计日志
│   │   ├── polish/                         # 文案润色
│   │   ├── agreement/                      # 服务协议
│   │   ├── privacy/                        # 隐私政策
│   │   └── login/                          # 登录
│   ├── src/api/                            # 22 个 API 模块
│   └── src/layouts/                        # 主布局 + 路由 + 状态管理
└── scripts/                                # 全平台打包 + Docker
    ├── build.sh                            # 统一构建入口（all/electron/ios/android/docker/jar/frontend）
    ├── electron/                           # Electron 桌面应用（Mac + Windows）
    ├── ios/                                # iOS 应用（Capacitor + Xcode）
    ├── android/                            # Android 应用（Capacitor + Gradle）
    ├── docker/                             # Docker 多阶段构建（SQLite / MySQL 8）
    └── common/                             # 共享工具函数
```

### 技术选型

| 层级 | 技术栈 | 说明 |
|------|--------|------|
| 运行时 | **Java 17** (LTS) | 长期支持版本 |
| 后端框架 | **Spring Boot 3.5.4** | 自动装配、WebSocket、调度任务 |
| ORM / 数据库 | **SQLite3 + MyBatis-Plus 3.5** | 嵌入式数据库，开箱即用 |
| 多库支持 | **SQLite / MySQL 8 / PostgreSQL** | 三选一 profile 切换 |
| 连接池 | **Druid 1.2.23** | SQLite 单连接最优（maxActive=1 + busy_timeout=30s）+ WAL 模式 |
| 缓存 | **Caffeine** | 本地高性能内存缓存 |
| 长连接 | **Netty 4.1.108.Final** | IM 客户端，NIO + IdleStateHandler 心跳 + 自动重连 |
| 浏览器自动化 | **Chrome CDP** | 滑块验证码破解 + 每账号独立 Chrome 容器 + 指纹注入 |
| 代理池 | **多提供商** | SmartProxy / 快骑 / 阿布云 / 奇迹（短效+隧道） |
| 前端 | **Vue 3 + Vite + Element Plus** | 现代 SPA 管理后台 |
| 认证 | **JWT** | 无状态 Token 鉴权 |
| 任务调度 | **Spring Schedule** | 自动发货、定时同步、健康检测 |
| AI | **OpenAI 兼容协议** | 任意兼容 OpenAI 的大模型（OpenAI / DeepSeek / Kimi / 自部署等） |
| 工具 | **Lombok** | 精简样板代码 |
| 桌面应用 | **Electron 28** | Mac + Windows 桌面打包 |
| 移动端 | **Capacitor** | iOS / Android 原生打包 |
| 容器化 | **Docker** | 多阶段构建 + 非 root 用户 + 健康检查 |

### 架构设计原则

- **平台隔离 + 公共抽象下沉**：`social-sdk-core` 只定义抽象与通用模型，各平台独立实现，避免代码耦合
- **配置驱动**：通过 YAML 文件开关各能力：Chrome 驱动、闲鱼 Console、AI、通知、代理池等
- **AES 加密敏感配置**：Cookie、API Key、通知通道配置等一律加密存储
- **扩展性优先**：包结构预留了 `wechat`、`dingding`、`quark_netdisk`、`baidu_netdisk` 等平台扩展位

---

## ✨ 功能特性

### 1. 🖥️ 仪表盘 / 监控面板
- 首页运营概览
- 实时数据大屏（`data-board`）：多账号实时数据轮询展示
- 系统资源与连接状态实时可观测
- 欢迎页（`landing`）：产品介绍 + 功能引导

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
- 本地商品库（`local_product`）与闲鱼线上商品分离管理
- 本地商品批量导入

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
- 回复日志（按会话维度完整记录匹配路径 + 命中规则 + 发送结果）

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
- 投递日志完整记录 + 重试机制（5 次退避）
- 站内收件箱（未读计数 + 已读状态）每日摘要邮件

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

### 15. 🌐 开放平台 OpenAPI
- 完整 API 鉴权机制（appKey + appSecret → Bearer 令牌，2h 有效期）
- 账号维度白名单限流（按应用粒度，每分钟可配限额）
- 统一响应信封（`code/message/data/timestamp`）
- 标准错误码（`OPEN_` 前缀，区分业务与系统错误）
- 内置 Swagger UI（`/openapi/v1/docs`）
- 已开放 18+ 域名接口（账号 / 商品 / 消息 / 订单 / 规则 / 钱包 / 收藏 / AI 客服 / AI 运营 / AI 模型 / 网盘 / 虚拟发货 / 买家 / 监控 / 市场 / 通知 / 应用管理）

### 16. 🔍 市场情报
- 市场商品抓取与比价
- 价格历史跟踪（`price_history`）
- 卖家画像分析

### 17. 👥 买家画像
- 买家会话维度标签
- 购买历史与偏好分析
- 与 AI 客服联动提供个性化服务

### 18. 🕵️ 监控爬虫
- 定时抓取任务调度（`monitor_task`）
- 抓取结果存储（`monitor_result`）
- 卖家主页监控与变更检测

### 19. 🖥️ Chrome 容器池
- 每账号独立 Chrome 实例（CDP 连接）
- 浏览器指纹注入（Canvas/WebGL/字体/插件等）
- 代理IP绑定 + 健康检查 + 自动回收
- CDP 滑块验证码自动破解（轨迹模拟 + 反检测）
- CDP 代理连接器（`CdpProxyController`）

### 20. 🌐 账号代理池
- 多提供商支持（SmartProxy / 快骑 / 阿布云 / 奇迹）
- 短效代理 / 隧道代理双模式
- 账号-代理绑定持久化 + 自动健康检查
- 代理租约机制（防止并发复用）

### 21. 📦 OpenList 集成
- 轻量级清单管理
- 独立数据存储（`/app/data/openlist`）
- 独立端口配置（默认 5244）

### 22. 💬 AI 聊天
- 独立 AI 对话页面
- 多模型切换
- 流式输出
- AI 能力演示（`/api/ai/demo`）：商品描述优化、关键词提取、标题生成

### 23. ✨ AI 文案润色
- 商品标题 / 描述一键润色
- 多风格支持

### 24. ⭐ 评价管理
- 买家评价查看与回复
- 评价统计

### 25. 🗄️ 定时任务中心
- 账号健康检测（5min）
- Cookie 过期清理（每日 02:00）
- 审计日志清理（月度）
- 数据聚合（每日 00:00）
- 市场情报抓取调度

### 26. 🔌 账号熔断器
- 账号异常自动断开（`circuit_breaker`）
- 熔断事件记录（`circuit_breaker_event`）
- 熔断恢复检测

### 27. 📜 法律文档
- 服务协议（`agreement`）
- 隐私政策（`privacy`）

> 📖 **完整接口文档**：[docs/openapi.md](./docs/openapi.md)  
> 🔗 **Swagger UI**：`http://localhost:8080/openapi/v1/docs`  
> 📋 **OpenAPI JSON**：`http://localhost:8080/openapi/v1/openapi.json`

---

## 🚀 快速开始

### 方式一：源码运行（推荐开发）

#### 1. 编译整个项目

```bash
cd social-sdk-parent
mvn clean install -DskipTests
```

#### 2. 准备 Chrome 驱动（登录用）

下载与 Chrome 版本对应的 [ChromeDriver](https://googlechromelabs.github.io/chrome-for-testing/)，路径配置在 `application.yml` 中。

#### 3. 配置 `application.yml`

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

#### 4. 启动管理后台

```bash
cd social-sdk-xianyu-manager
mvn spring-boot:run
```

#### 5. 访问

- 管理后台：`http://localhost:8080`
- 默认管理员 / 密码：`admin` / `admin123`

> ⚠️ 首次启动会自动执行 `db/schema.sql` 初始化 SQLite 表结构。

### 方式二：Docker 部署（推荐生产）

#### 使用预构建 Dockerfile

```bash
# SQLite 模式（默认）
docker build --build-arg DB_MODE=sqlite -t xianyu-manager:sqlite .

# MySQL 8 模式
docker build --build-arg DB_MODE=mysql -t xianyu-manager:mysql .
```

#### 使用 docker-compose

```bash
# SQLite
docker-compose up -d

# MySQL 8
docker-compose -f scripts/docker/docker-compose.mysql.yml up -d
```

#### 数据持久化

```bash
# 数据存储在以下卷
./data                    # SQLite 数据库 / MySQL 数据
./chrome-profiles         # Chrome 容器配置
./logs                    # 应用日志
./config                  # 自定义配置
```

### 方式三：Electron 桌面应用

```bash
# 构建桌面应用（Mac + Windows）
./scripts/build.sh electron

# 产物
scripts/electron/dist_electron/
├── xianyu-manager-x64.dmg    # Mac 安装包
├── xianyu-manager-x64.zip    # Mac 便携版
├── xianyu-manager-x64.exe    # Windows 安装包
└── xianyu-manager-portable.exe # Windows 便携版
```

### 方式四：移动端

```bash
# iOS（需要 macOS + Xcode 15+）
./scripts/build.sh ios

# Android（需要 Android Studio Hedgehog+）
./scripts/build.sh android
```

---

## 🔌 API 一览

### 内部管理接口（前缀 `/api/`）

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `POST /api/auth/login` | 管理员登录 |
| 认证 | `GET /api/auth/profile` | 获取当前用户信息 |
| 账号 | `GET /api/accounts` | 账号列表 |
| 账号 | `POST /api/accounts/login` | Cookie / 扫码登录 |
| 账号 | `PUT /api/accounts/{id}/cookies` | 更新 Cookie |
| 账号 | `GET /api/accounts/{id}/profile` | 同步个人资料 |
| 账号 | `PUT /api/accounts/{id}/status` | 更新状态 |
| 验证码 | `POST /api/captcha/control` | 验证码求解控制 |
| 验证码 | `GET /api/captcha/proxy` | CDP 代理连接状态 |
| 商品 | `GET /api/local-products` | 本地商品列表 |
| 商品 | `POST /api/local-products/import` | 本地商品批量导入 |
| 商品 | `GET/POST /api/products` | 商品列表 / 创建 |
| 商品 | `PUT /api/products/{id}` | 编辑商品 |
| 商品 | `POST /api/products/{id}/shelf-on` | 上架 |
| 商品 | `POST /api/products/{id}/shelf-off` | 下架 |
| 消息 | `GET /api/messages/sessions` | 会话列表 |
| 消息 | `GET /api/messages/history` | 消息历史 |
| 消息 | `POST /api/messages/send` | 发送消息 |
| 消息 | `WS /ws/messages` | 实时推送 |
| 订单 | `GET /api/orders` | 订单列表 |
| 订单 | `POST /api/orders/{id}/delivery` | 发货 |
| 评价 | `GET /api/reviews` | 评价管理 |
| 规则 | `GET /api/rules` | 规则列表 |
| 规则 | `POST /api/rules` | 创建规则 |
| 规则 | `POST /api/rules/test` | 测试规则匹配 |
| 规则 | `POST /api/rules/auto-reply` | 自动回复 |
| 回复日志 | `GET /api/reply-logs` | 回复日志 |
| AI 厂商 | `GET/POST /api/ai/providers` | 厂商管理 |
| AI 模型 | `GET/POST /api/ai/models` | 模型管理 |
| AI 聊天 | `POST /api/ai/chat` | AI 聊天 |
| AI 客服 | `GET /api/cs/sessions` | 客服会话列表 |
| AI 客服 | `GET /api/cs/messages` | 客服消息记录 |
| AI 运营 | `GET /api/ops/tasks` | 运营任务列表 |
| AI 运营 | `GET /api/ops/suggestions` | 运营建议 |
| 文案润色 | `POST /api/polish` | AI 文案润色 |
| 通知 | `GET /api/notify/messages` | 站内通知 |
| 通知 | `GET/POST /api/notify/channels` | 通知通道 |
| 通知 | `GET/POST /api/notify/templates` | 通知模板 |
| 通知 | `GET/POST /api/notify/subscriptions` | 订阅规则 |
| 通知 | `GET /api/notify/logs` | 投递日志 |
| 钱包 | `GET /api/wallet` | 钱包资产 |
| 收藏 | `GET /api/collect` | 收藏关注列表 |
| 监控 | `GET /api/monitor` | 监控面板 |
| 审计 | `GET /api/audit/logs` | 审计日志 |
| 买家 | `GET /api/buyers` | 买家画像 |
| 市场 | `GET /api/market` | 市场情报 |
| Chrome | `GET /api/chrome` | Chrome 容器管理 |
| 代理 | `GET/POST /api/proxy` | 代理池管理 |
| 虚拟发货 | `GET /api/virtual-ship` | 虚拟商品发货 |
| 网盘 | `GET /api/cloud-storage` | 网盘存储 |
| OpenAPI 应用 | `GET /api/openlist` | 应用管理 |
| 系统 | `GET /api/system` | 系统信息 |
| 系统 | `GET /api/system/health` | 健康检查 |

### 对外 OpenAPI（前缀 `/openapi/v1/`）

| 模块 | 说明 |
|------|------|
| 应用管理 | `GET /openapi/v1/apps` - 应用凭证申请与管理 |
| 账号域 | 账号 CRUD / 登录 / 状态 / 资料同步 |
| 商品域 | 商品 CRUD / 上下架 / 价格 / 库存 |
| 消息域 | 会话 / 历史 / 发送 |
| 订单域 | 列表 / 详情 / 发货 |
| 规则域 | 关键词规则引擎 |
| 钱包域 | 资产 / 交易记录 |
| 收藏域 | 收藏 / 关注 |
| AI 客服域 | 会话 / 消息 / 知识库 / 策略 |
| AI 运营域 | 任务 / 建议 / 知识库 |
| AI 模型域 | 模型查询 |
| 网盘域 | 文件 / 分享 |
| 虚拟发货域 | 卡密 / 任务 |
| 买家域 | 画像 |
| 监控域 | 爬虫结果 |
| 市场域 | 情报数据 |
| 通知域 | 通道 / 模板 |
| 任务域 | 定时任务 |

---

## 🗄️ 数据库

### 多数据库支持

| 数据库 | Profile | 说明 |
|--------|---------|------|
| **SQLite 3** | `sqlite` (默认) | 嵌入式，开箱即用，零配置 |
| **MySQL 8** | `mysql` | 生产级，高并发 |
| **PostgreSQL** | `postgres` | 生产级，JSON 支持 |

切换方式：

```bash
# 命令行参数
java -jar app.jar --spring.profiles.active=mysql

# Docker
docker run -e SPRING_PROFILES_ACTIVE=mysql ...
```

### 核心数据表（48 张）

| 类别 | 表名 | 用途 |
|------|------|------|
| **系统** | `admin_user` | 管理员用户 |
| **系统** | `open_app` | 对外 OpenAPI 应用凭证 |
| **账号** | `xianyu_account` | 闲鱼账号（Cookie、状态、个人资料、Chrome 容器、代理绑定） |
| **商品** | `xianyu_product` | 线上商品 |
| **商品** | `local_product` | 本地商品库 |
| **商品** | `price_history` | 价格历史 |
| **消息** | `xianyu_message` | 聊天记录 |
| **订单** | `xianyu_order` | 订单（含虚拟发货字段） |
| **评价** | 评价数据 | 评价管理 |
| **规则** | `xianyu_keyword_rule` | 关键词 / 自动回复规则 |
| **规则** | `xianyu_auto_reply_config` | 全局自动回复配置 |
| **钱包** | `xianyu_wallet` / `xianyu_wallet_transaction` | 钱包资产 / 交易流水 |
| **收藏** | `xianyu_collect` | 收藏关注 |
| **AI** | `ai_provider` / `ai_model` | AI 厂商 / 模型 |
| **AI 客服** | `ai_cs_session` / `ai_cs_message` / `ai_cs_knowledge` / `ai_cs_policy` / `ai_cs_daily_stats` / `ai_cs_session_state` | AI 客服全套（会话/消息/知识库/策略/统计/状态） |
| **AI 运营** | `ai_ops_task` / `ai_ops_suggestion` / `ai_ops_knowledge` | AI 运营全套 |
| **通知** | `notify_channel` / `notify_template` / `notify_subscription` / `notify_log` / `notify_message` / `notify_digest_config` / `notify_retry` | 通知体系全套 |
| **虚拟** | `virtual_card_pool` / `virtual_ship_task` / `virtual_ship_config` | 虚拟卡密 / 发货任务 / 配置 |
| **网盘** | `cloud_storage_account` / `cloud_storage_file` | 网盘存储 |
| **AI** | 聊天会话 / 消息 | AI 独立聊天 |
| **市场** | `market_snapshot` / `price_history` / `market_daily_stat` | 市场情报 + 价格历史和每日统计 |
| **监控** | `monitor_task` / `monitor_result` / `seller_profile` | 监控爬虫 + 卖家画像 |
| **买家** | `buyer_profile` | 买家画像 |
| **熔断** | `circuit_breaker` / `circuit_breaker_event` | 账号熔断状态 |
| **审计** | `audit_log` / `proxy_audit_log` | 操作审计日志 / 代理审计日志 |
| **代理** | `proxy_account_binding` / `proxy_cool_down` | 账号-代理绑定 / 代理冷却 |
| **定时** | 任务调度数据 | 定时任务（Spring Schedule 内存 + DB） |

---

## 🔐 环境变量 / 配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `XIANYU_JWT_SECRET` | JWT 签名密钥 | `defaultSecretKeyForDevelopmentOnlyChangeInProduction` |
| `XIANYU_CRYPTO_SECRET` | Cookie / 敏感数据加密密钥 | `defaultCryptoSecretKeyForDevelopmentOnly` |
| `SPRING_PROFILES_ACTIVE` | 数据库 profile：`sqlite` / `mysql` / `postgres` | `sqlite` |
| `DB_PATH` | SQLite 数据库文件路径 | `./data/xianyu-manager.db` |
| `DB_MODE` | Docker 构建模式：`sqlite` / `mysql` | `sqlite` |
| `JAVA_OPTS` | JVM 参数 | `-Xmx512m -Xms256m` |
| `TZ` | 时区 | `Asia/Shanghai` |

> **⚠️ 生产环境务必修改上述密钥。**

---

## 🛣️ 后续路线图

### ✅ 已完成

**核心能力**
- [x] 闲鱼多账号管理（Cookie 登录 / 状态管理 / 健康检测）
- [x] 商品 CRUD + 上下架 + 刷新
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

**风控与反爬**
- [x] CDP 滑块验证码自动破解（轨迹模拟 + 反检测）
- [x] 每账号 Chrome 容器池（指纹注入 + 代理绑定）
- [x] 多提供商代理池（SmartProxy / 快骑 / 阿布云 / 奇迹）
- [x] 账号熔断器（异常自动断开 + 事件记录）

**扩展能力**
- [x] AI 聊天 + 文案润色
- [x] 市场情报（抓取 / 比价 / 价格历史 / 卖家画像）
- [x] 监控爬虫（定时抓取 / 变更检测）
- [x] 买家画像
- [x] 评价管理
- [x] OpenList 集成
- [x] OpenAPI 开放平台（18+ 域名接口）
- [x] 多数据库支持（SQLite / MySQL / PostgreSQL）

**部署与分发**
- [x] Docker 容器化（多阶段构建 + 非 root + 健康检查）
- [x] Electron 桌面应用（Mac + Windows）
- [x] iOS / Android 移动端（Capacitor）
- [x] 自动化构建脚本（`scripts/build.sh`）

### 🚧 进行中 / 计划中

- [ ] 商品同步管理（批量同步多账号商品、一键上下架）
- [ ] 定时任务中心（可视化 Cron 调度：定时刷新、定时回复、定时清理）
- [ ] 系统通知对接优化（与业务事件深度打通）
- [ ] 接入更多闲鱼风控场景处理

### 🔮 未来规划

- [ ] **微信平台** SDK 抽象与实现
- [ ] **钉钉平台** SDK 抽象与实现
- [ ] 跨平台数据统一聚合视图
- [ ] 多租户 / 团队权限（当前为单管理员）
- [ ] 商业级部署方案（Redis 集群 / Kafka / Elasticsearch）
- [ ] 抖音 / 小红书 等新平台调研与接入
- [ ] 多语言国际化（i18n）
- [ ] 移动端管理 App（Flutter / uni-app）增强

---

## 🏢 商业版

当前开源版本适用于**个人演示与学习**。如需**商业级、生产级**的完整系统，可参考我们的商业底座：

- [brick-bootkit-springboot](https://github.com/v18268185209/brick-bootkit-springboot.git) —— 商业后端框架
- [brick-bootkit-admin](https://github.com/v18268185209/brick-bootkit-admin.git) —— 商业管理后台

也欢迎进入商业专用群咨询：**第一批售价 199 元，可免费获得商业底层框架系统授权**。

---

## 💬 交流群

<img src="https://raw.githubusercontent.com/v18268185209/social-sdk-parent/refs/heads/main/docs/qqqunliao.jpg" width="200" />

---

## 📄 License

见 [LICENSE](./LICENSE)

---

## 🙏 欢迎贡献

欢迎提交 Issue、PR 或参与讨论，一起把这个属于 Java 的开源社交电商 SDK 做得更好！

### 打赏【虚拟币地址】

<img src="https://raw.githubusercontent.com/v18268185209/social-sdk-parent/refs/heads/main/docs/usdc.jpg" width="300" />
