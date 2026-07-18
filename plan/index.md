# 闲鱼多账号管理平台 — 快速参考索引

> 基于 `social-sdk-parent` 架构，新建 Spring Boot 子模块 `social-sdk-xianyu-manager` + Vue 3 前端
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
├── social-sdk-xianyu-manager/          ← 后端（Spring Boot）
│   ├── pom.xml
│   ├── README.md
│   └── src/main/java/cn/net/rjnetwork/xianyu/manager/
│       ├── XianyuManagerApplication.java
│       ├── config/          Security/MyBatis/WebSocket/Cache/DB Init
│       ├── common/          ApiResponse/ExceptionHandler/BaseEntity
│       ├── auth/            认证授权
│       ├── account/         账号管理 + 健康检查定时任务
│       ├── product/         商品管理
│       ├── message/         消息管理 + WebSocket STOMP
│       ├── order/           订单管理
│       ├── rule/            规则引擎
│       ├── wallet/          钱包/资产
│       ├── collect/         收藏关注
│       ├── monitor/         监控告警 + 仪表盘统计
│       ├── audit/           审计日志 + AOP
│       └── system/          系统信息 + 健康检查
└── social-sdk-xianyu-manager-web/    ← 前端（Vue 3 + Element Plus）
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── main.js              入口
        ├── App.vue              根组件
        ├── api/                 Axios 封装
        ├── store/               Pinia 状态管理
        ├── router/              Vue Router
        ├── layouts/             MainLayout（侧边栏 + 头部）
        └── views/               页面组件
            ├── login/           登录页
            ├── dashboard/       仪表盘
            ├── accounts/        账号管理
            ├── products/        商品管理
            ├── messages/        消息管理
            ├── orders/          订单管理
            ├── rules/           规则管理
            ├── wallet/          钱包资产
            ├── collect/         收藏关注
            ├── monitor/         监控面板
            └── audit/           审计日志
```

---

## 二、全部已完成 ✅

### 后端（60+ Java 源文件）

| Phase | 模块 | 状态 |
|-------|------|------|
| P1 | 认证授权 | ✅ |
| P1 | 账号管理 | ✅ |
| P1 | 商品管理 | ✅ |
| P1 | 消息管理 | ✅ |
| P1 | 规则引擎 | ✅ |
| P1 | 订单管理 | ✅ |
| P1 | 审计日志 | ✅ |
| P2 | 钱包/资产 | ✅ |
| P2 | 收藏关注 | ✅ |
| P2 | 监控告警 | ✅ |
| P2 | WebSocket 推送 | ✅ |
| P2 | 系统管理 | ✅ |

### 前端（20+ Vue 组件）

| 页面 | 功能 |
|------|------|
| 登录页 | 用户名密码登录 |
| 主布局 | 侧边栏导航 + 顶部用户信息 |
| 仪表盘 | 8 项统计卡片 + 账号状态表格 |
| 账号管理 | 添加账号(Cookie) + 切换状态 + 删除 |
| 商品管理 | 搜索/筛选 + 创建 + 上架/下架 + 改价/改库存 |
| 消息管理 | 会话列表 + 消息历史(时间线) + 发送消息 |
| 规则管理 | 规则 CRUD + 开关 + 测试匹配 |
| 订单管理 | Tab 切换(卖出/买到) + 发货 |
| 钱包资产 | 余额 + 交易记录 |
| 收藏关注 | 列表 + 添加/移除 |
| 监控面板 | 统计卡片 + 账号维度表格 |
| 审计日志 | 操作日志列表 |

---

## 三、快速启动

### 后端

```bash
cd E:\codes\social-sdk-parent
mvn clean install -DskipTests
cd social-sdk-xianyu-manager
mvn spring-boot:run
# 访问: http://localhost:8080
# 默认管理员: admin / admin123
```

### 前端

```bash
cd social-sdk-xianyu-manager-web
npm install
npm run dev
# 访问: http://localhost:3000
```

Vite 开发服务器自动代理 `/api` 到后端 `localhost:8080`。

---

## 四、API 端点总览

| 模块 | 路径 | 方法 |
|------|------|------|
| 认证 | `/api/auth/login` | POST |
| 账号 | `/api/accounts` | GET/POST/DELETE |
| 账号 | `/api/accounts/{id}/status` | PUT |
| 商品 | `/api/products` | GET/POST |
| 商品 | `/api/products/{id}` | PUT/DELETE |
| 商品 | `/api/products/{id}/shelf-on/off` | POST |
| 商品 | `/api/products/{id}/price` | PUT |
| 商品 | `/api/products/{id}/stock` | PUT |
| 消息 | `/api/messages/sessions` | GET |
| 消息 | `/api/messages/history` | GET |
| 消息 | `/api/messages/send` | POST |
| 消息 | `/ws/messages` | WebSocket(STOMP) |
| 订单 | `/api/orders` | GET |
| 订单 | `/api/orders/{id}/delivery` | POST |
| 规则 | `/api/rules` | GET/POST |
| 规则 | `/api/rules/{id}` | PUT/DELETE |
| 规则 | `/api/rules/{id}/toggle` | POST |
| 规则 | `/api/rules/test` | POST |
| 规则 | `/api/rules/auto-reply` | POST |
| 钱包 | `/api/wallet/{id}` | GET |
| 钱包 | `/api/wallet/{id}/transactions` | GET |
| 收藏 | `/api/collect` | GET/POST |
| 收藏 | `/api/collect/{id}` | DELETE |
| 监控 | `/api/monitor/dashboard` | GET |
| 监控 | `/api/monitor/accounts` | GET |
| 审计 | `/api/audit/logs` | GET |
| 系统 | `/api/system/info` | GET |
| 系统 | `/api/system/health` | GET |
