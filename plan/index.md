# 闲鱼多账号管理平台 - 快速参考索引

## 项目结构
```
social-sdk-parent/
├── social-sdk-xianyu/           # 核心SDK层（纯HTTP/MTOP API调用）
├── social-sdk-spring-boot-starter/  # Spring Boot Starter（集成层）
├── social-sdk-xianyu-manager/   # 管理后台（Spring Boot + SQLite3 + In-Memory Cache）
└── social-sdk-xianyu-manager-web/  # 管理后台前端（Vue 3 + Element Plus）
```

## 后端 API 端点总览

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| Auth | POST | /api/auth/login | 管理员登录 |
| Auth | GET | /api/auth/profile | 获取当前用户信息 |
| Account | GET | /api/accounts | 账号列表 |
| Account | GET | /api/accounts/{id} | 账号详情 |
| Account | POST | /api/accounts/login | 添加账号 |
| Account | PUT | /api/accounts/{id}/status | 更新账号状态 |
| Account | DELETE | /api/accounts/{id} | 删除账号 |
| Product | GET | /api/products | 商品列表（分页） |
| Product | GET | /api/products/{id} | 商品详情 |
| Product | POST | /api/products | 创建商品 |
| Product | PUT | /api/products/{id} | 更新商品 |
| Product | DELETE | /api/products/{id} | 删除商品 |
| Product | POST | /api/products/{id}/shelf-on | 上架 |
| Product | POST | /api/products/{id}/shelf-off | 下架 |
| Product | PUT | /api/products/{id}/price | 修改价格 |
| Product | PUT | /api/products/{id}/stock | 修改库存 |
| Message | GET | /api/messages/sessions | 会话列表 |
| Message | GET | /api/messages/history | 消息历史 |
| Message | POST | /api/messages/send | 发送消息 |
| Rule | GET | /api/rules | 规则列表 |
| Rule | POST | /api/rules | 创建规则 |
| Rule | PUT | /api/rules/{id} | 更新规则 |
| Rule | DELETE | /api/rules/{id} | 删除规则 |
| Rule | POST | /api/rules/{id}/toggle | 启用/禁用规则 |
| Rule | POST | /api/rules/test | 测试规则匹配 |
| Rule | POST | /api/rules/auto-reply | 自动回复 |
| Order | GET | /api/orders | 订单列表（分页） |
| Order | GET | /api/orders/{id} | 订单详情 |
| Order | POST | /api/orders/{id}/delivery | 发货 |
| Audit | GET | /api/audit/logs | 审计日志 |
| Wallet | GET | /api/wallet/{accountId} | 钱包信息 |
| Wallet | GET | /api/wallet/{accountId}/transactions | 交易记录 |
| Wallet | GET | /api/wallet/{accountId}/recent | 最近交易 |
| Collect | GET | /api/collect | 收藏列表 |
| Collect | POST | /api/collect | 添加收藏 |
| Collect | DELETE | /api/collect/{id} | 移除收藏 |
| Monitor | GET | /api/monitor/dashboard | 仪表盘统计 |
| Monitor | GET | /api/monitor/accounts | 账号维度统计 |
| Monitor | POST | /api/monitor/cache/clear | 清除缓存 |
| System | GET | /api/system/info | 系统信息 |
| System | GET | /api/system/health | 健康检查 |

## 前端页面路由

| 路由 | 页面 | 对应 API |
|------|------|----------|
| /login | 登录 | POST /api/auth/login |
| /dashboard | 仪表盘 | GET /api/monitor/dashboard, /api/monitor/accounts, /api/system/info |
| /accounts | 账号管理 | CRUD /api/accounts |
| /products | 商品管理 | CRUD /api/products + shelf/price/stock |
| /messages | 消息管理 | Sessions/History/Send /api/messages |
| /rules | 规则管理 | CRUD /api/rules + test/auto-reply |
| /orders | 订单管理 | List/Delivery /api/orders |
| /wallet | 钱包资产 | GET /api/wallet |
| /collect | 收藏关注 | CRUD /api/collect |
| /monitor | 监控面板 | GET /api/monitor |
| /audit | 审计日志 | GET /api/audit/logs |

## 启动方式

### 后端
```bash
cd social-sdk-xianyu-manager
mvn spring-boot:run
# 默认端口: 8080
# 默认管理员: admin / admin123
```

### 前端
```bash
cd social-sdk-xianyu-manager-web
npm install
npm run dev
# 默认端口: 3000
# 访问: http://localhost:3000
```

## 技术栈
- **后端**: Spring Boot 3.5 + Java 17 + MyBatis-Plus + SQLite3 + JWT + Caffeine + WebSocket(STOMP)
- **前端**: Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios

## 已修复问题
1. ✅ 缺失 JWT 认证过滤器 - 新增 JwtAuthenticationFilter
2. ✅ SecurityConfig 未注册 JWT Filter - 已添加
3. ✅ schema.sql 缺少 wallet/collect 表 - 已补充
4. ✅ AuditService 缺少 @Service 注解 - 已修复
5. ✅ AuditLogAspect resourceType 赋值错误 - 已修正
6. ✅ Dashboard 双 .data 访问错误 - 已修正
7. ✅ Rules 页面硬编码 accountId=1 - 已改为下拉选择
8. ✅ 前端各页面 API 调用对齐 - 已全部修正
9. ✅ @EnableScheduling 缺失 - 已添加到启动类
