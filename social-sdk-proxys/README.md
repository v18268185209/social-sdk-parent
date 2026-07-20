# social-sdk-proxys

统一代理池 SDK。声明一条代理，屏蔽阿布云 / 快代理 / Smartproxy / 自建等供应商差异，
内置账号-IP 绑定复用、冷名单、健康检查、租约泄露检测。

---

## 核心概念

```
业务方 ──→ ProxyPoolManager ──→ ProxyProvider (多个)
                │
                ├── leases (租约, AutoCloseable)
                ├── bindings (账号-IP 绑定)
                ├── coolingDown (冷名单)
                └── healthChecker (健康检查)
```

| 概念 | 说明 |
|---|---|
| **ProxyProvider** | 供应商适配器，每个供应商一个实现 |
| **ProxyPoolManager** | 业务方唯一入口，按策略选供应商 |
| **ProxyLease** | 租约，必须归还，支持 try-with-resources |
| **binding** | 账号 ↔ IP 绑定，同一账号优先复用同一 IP |
| **cooling down** | 冷名单，连续失败 3 次打入冷却，30 分钟后复原 |

---

## 快速接入

### 1. 加依赖

```xml
<dependency>
    <groupId>cn.net.rjnetwork</groupId>
    <artifactId>social-sdk-proxys</artifactId>
    <version>0.0.1</version>
</dependency>
```

### 2. 配置 application.yml

```yaml
proxy:
  enabled: true
  reuse-bound-ip: true          # 同一账号复用已绑定的 IP
  max-binding-use-count: 100    # 一条绑定最多使用次数
  cool-down-recovery-minutes: 30
  lease-leak-threshold-minutes: 60

  health-check:
    enabled: true
    interval-minutes: 5
    max-latency-ms: 3000
    geo-check: true

  providers:
    # 阿布云 (abuyun.com)
    abuyun:
      enabled: true
      username: your_username
      password: your_password
      host: http-dyn.abuyun.com
      port: 9020

    # 快代理 (kuaidaili.com)
    kuaidaili:
      enabled: false
      orderId: your_order_id
      apiKey: your_api_key
      tunnelHost: tunnel.kuaidaili.com
      tunnelPort: 15818

    # Smartproxy (国际住宅)
    smartproxy:
      enabled: false
      username: your_username
      password: your_password
      host: gate.smartproxy.com
      port: 7000
      city: Shanghai
```

### 3. 在业务代码里用

```java
@Autowired private ProxyPoolManager proxyPoolManager;

// ===== 方式一：声明式 =====
public void doSomething(Long accountId) {
    ProxyAcquireRequest request = ProxyAcquireRequest.defaultRequest(accountId);
    try (ProxyLease lease = proxyPoolManager.acquire(request)) {
        ProxyInfo proxy = lease.getProxy();
        // 用 proxy 去发 HTTP 请求...
        // lease 归还时自动释放代理
    }
}

// ===== 方式二：MTOP 客户端（已内置代理感知） =====
XianyuMtopApiClient client = new XianyuMtopApiClient(
        cookie, proxyPoolManager, accountId);
JsonNode resp = client.callMtop("mtop.taobao.idleuser.info.get", "{}");

// ===== 方式三：验证码服务（已内置代理感知） =====
XianyuCaptchaService captcha = new XianyuCaptchaService(
        cookie, proxyPoolManager, accountId);
```

---

## 集成到闲鱼业务

`XianyuMtopApiClient` 和 `XianyuCaptchaService` 已经内置代理感知：

```java
// Spring Bean 装配示例
@Bean
public XianyuMtopApiClient mtopClient(
        @Value("${xianyu.cookie}") String cookie,
        ProxyPoolManager proxyPoolManager,
        @Value("${xianyu.account-id}") Long accountId) {
    return new XianyuMtopApiClient(cookie, proxyPoolManager, accountId);
}
```

失败重试策略（内置）：
- 连接超时 / IO 异常 → 自动换代理重试（最多 3 次）
- HTTP 5xx → 直接返回，由上层处理
- 风控拦截 / 4xx → 直接返回，由 `isRiskControlTriggered()` 识别

---

## 持久化

启动时自动从 `proxy_account_binding` 表加载有效绑定，还原到内存。
业务方无需关心，`BindingPersistenceAutoConfiguration` 自动装配。

```sql
-- 表结构见 src/main/resources/db/proxy-bindings.sql
-- 与应用同一个 SQLite 库，表名前缀 proxy_
```

---

## 自定义供应商

实现 `ProxyProvider` 接口，注册到 `ProxyPoolManager`：

```java
ProxyProvider myProvider = new ProxyProvider() {
    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) { ... }
    @Override
    public void release(ProxyInfo proxy) { ... }
    @Override
    public String queryBalance() { return "OK"; }
};

proxyPoolManager.registerProvider(ProviderType.CUSTOM, myProvider);
```

---

## 监控指标

```java
DefaultProxyPoolManager manager = ...;
DefaultProxyPoolManager.PoolMetrics m = manager.getMetrics();
System.out.println(m);
// PoolMetrics{providers=1, activeLeases=2, bindings=5, coolingDown=1, totalAcquire=100, fail=3, successRate=97.0%}
```

---

## 包结构

```
cn.net.rjnetwork.xianyu.proxy
├── config/                  # ProxyInfo / ProxyType / ProviderType / ProxyProperties
├── core/                    # ProxyProvider / ProxyPoolManager / ProxyLease / ProxyException
│   ├── DefaultProxyPoolManager.java
│   └── ProxyAutoConfiguration.java
├── provider/                # Abuyun / Kuaidaili / Smartproxy 适配器
├── health/                  # DefaultHealthChecker
├── persistence/             # SQLite BindingRepository + schema
└── util/                    # HttpUtils (OkHttp)
```
