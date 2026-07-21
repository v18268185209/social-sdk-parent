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

## 无代理直连模式

**默认行为**：当没有配置任何供应商（或所有供应商都不可用）时，代理池自动退化为 **直连模式**，
`ProxyPoolManager.acquire()` 永远不会抛异常，而是返回一个 `ProxyInfo.isDirect() == true` 的特殊 lease。

### 什么会触发直连模式

| 场景 | 行为 |
|---|---|
| 没配置任何供应商 | 启动时 `ProxyPoolRegistrar` 发现 `providers=0`，日志 WARN，然后自动直连 |
| 供应商都挂了 / 全部被风控 | acquire 走完所有供应商 → 冷名单兜底失败 → 返回 `ProxyInfo.direct()` |
| 测试环境直接关代理 | `proxy.enabled=false` 即可（和旧版行为一致） |

### 业务方如何识别直连

```java
try (ProxyLease lease = proxyPoolManager.acquire(request)) {
    ProxyInfo proxy = lease.getProxy();
    if (proxy.isDirect()) {
        // 直连模式：走本地网络，不经过代理
        httpClient = HttpClient.newBuilder().build();
    } else {
        // 代理模式
        httpClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())))
                .build();
    }
    // ...用 httpClient 发请求
}
```

**注意**：`XianyuMtopApiClient` 和 `XianyuCaptchaService` 已经内置这个判断，业务方无需手动处理。

### 关闭直连兜底（默认开启）

如果你希望"没代理就报错"而不是默默直连，可以关闭：

```yaml
proxy:
  direct-mode-auto-fallback: false   # 默认 true
```

关闭后，所有供应商失败会抛出 `ProxyException.noAvailableProxy()`，适用于**必须走代理**的线上风控场景。

### 直连模式的内部实现

```java
ProxyInfo direct = ProxyInfo.direct();   // host="DIRECT", port=0
// acquire 返回直连 lease
// release 识别 isDirect()，跳过 provider.release()
// 健康检查和绑定关系：直连 lease 不会进冷名单、不会进 binding 表
```

---

## 青果网络 (qg.net) 接入

QG 网络是 国内代理 IP 服务商，官网 https://www.qg.net/product/proxyip.html 。
social-sdk-proxys 内置两个 Provider：**隧道代理（快速跑通）** + **短效代理（长期稳定）**。

### 1. 开通 QG

1. 注册 QG 账号 https://www.qg.net
2. 后台获取产品的 `apiKey`（唯一产品标识）
3. 设置白名单 IP 或配置账密鉴权
4. 开通套餐（隧道代理 / 短效代理 / 按量提取）

### 2. 二选一或全都要

| 套餐 | 适用场景 | Provider |
|---|---|---|
| **隧道代理** | 快速上线，账号-IP 绑定要求不强，云端自动切 IP | `QgTunnelProvider` |
| **短效代理** | 每个账号固定一个住宅 IP，降低风控 | `QgShortLivedProvider` |
| **两者配合** | 短效代理优先，隧道代理兜底 | 两者都启用 |

### 3. 配置 application.yml

```yaml
proxy:
  providers:
    # 青果网络 (qg.net) 共用一个 apiKey
    qg:
      enabled: true                       # 整体开关（仅作标记，bean 注册看内部子开关）
      apiKey: xxxxx_product_key_xxxx      # 产品唯一标识，隧道/短效共用
      authKey: your_user                   # 账密鉴权用户名（白名单模式可不填）
      authPwd: your_pass                   # 账密鉴权密码

      # 方案 A：隧道代理（固定入口，云端自动切 IP）
      tunnel:
        enabled: true                       # 加这个才装配 qgTunnelProvider
        host: tun-szbhry.qg.net             # 隧道服务器地址
        port: 25889
        areaCode: "110100"                  # 可选：指定地区编码（北京）
        keepAliveSec: 60                    # 保持同一 IP 的秒数（账号密码模式）
        protocol: http                      # http 或 socks5

      # 方案 B：短效代理（每次调 API 提取，账号固定 IP）
      shortLived:
        enabled: false                      # 设为 true 启用短效代理
        plan: extract_by_count              # 提取模式：extract_by_count / elastic / uniform / channel
        areaCode: ""                        # 可选：限定 IP 地区编码
        isp: 0                              # 运营商筛选：0=不筛选 1=电信 2=移动 3=联通
        distinct: true                      # 去重提取：避免分到其他账号用过的 IP
        num: 1                              # 单次提取数量（建议 1）
        keepAliveSec: 60                    # IP 存活秒数
        maxLatencyMs: 3000                  # 健康检查最大延迟（超过此值自动换 IP）
```

### 4. 两种方案的请求流

**方案 A：隧道代理**

```
XianyuMtopApiClient
    ↓ acquire(ProxyAcquireRequest)
QgTunnelProvider
    ↓ 返回固定 host:port（cloud 自动切每个请求的出口 IP）
DefaultProxyPoolManager
    ↓ cache binding (accountId → QG-tunnel)
    ↓ 返回 ProxyLease
```

* 请求示例：`http://user:pass@tun-szbhry.qg.net:25889`
* QG 云端自动路由到不同出口 IP，SDK 无感知
* 支持 `-A-xxx` 参数地区 / `-T-60` 参数保持 IP

**方案 B：短效代理**

```
XianyuMtopApiClient
    ↓ acquire(ProxyAcquireRequest)
QgShortLivedProvider
    ↓ 调用 share.proxy.qg.net/get → 返回 ip:port
    ↓ 构造 ProxyInfo（带 deadline）
DefaultProxyPoolManager
    ↓ binding (accountId → ip:port)
    ↓ 健康检查 (httpbin.org/ip)
    ↓ 返回 ProxyLease
```

* 请求示例：`http://123.54.55.24:59419`
* SDK 记录 `deadline`（到期自动失效）
* 可开启 `distinct=true` 去重，避免分到已用过的 IP

### 5. 推荐优先级

在 `ProxyPoolRegistrar` 中，默认按注册顺序排列：短效代理可设为更高优先级，
配合 `directModeAutoFallback=true` 实现逐级降级：

```
短效 QG 代理 → 隧道 QG 代理 → 阿布云 / 快代理 → 直连兜底
```

---

## 快代理 (kuaidaili.com) 接入

快代理是国内老牌代理 IP 服务商，官网 https://www.kuaidaili.com 。
social-sdk-proxys 内置两个 Provider：**隧道代理（快速跑通）** + **私密代理（长期稳定）**。

### 1. 开通快代理

1. 注册快代理账号 https://www.kuaidaili.com
2. 后台获取订单的 `secretId` 和 `secretKey`（每个订单对应一组密钥）
3. 设置白名单 IP 或配置账密鉴权
4. 开通套餐（隧道代理 / 私密代理）

### 2. 二选一或全都要

| 套餐 | 适用场景 | Provider |
|---|---|---|
| **隧道代理** | 快速上线，账号-IP 绑定要求不强，云端自动切 IP | `KuaidailiTunnelProvider` |
| **私密代理** | 每个账号固定一个住宅 IP，降低风控 | `KuaidailiPrivateProvider` |
| **两者配合** | 私密代理优先，隧道代理兜底 | 两者都启用 |

### 3. 配置 application.yml

```yaml
proxy:
  providers:
    # 快代理 (kuaidaili.com) 共用一组 secretId/secretKey
    kuaidaili:
      enabled: true                       # 整体开关（仅作标记，bean 注册看内部子开关）
      secretId: your_secret_id            # 订单 SecretId
      secretKey: your_secret_key          # 订单 SecretKey（hmacsha1 模式必填）
      authType: token                     # token（默认）或 hmacsha1

      # 方案 A：隧道代理（固定入口，云端自动切 IP）
      tunnel:
        enabled: true                     # 加这个才装配 kuaidailiTunnelProvider
        num: 1                            # 提取数量（固定为 1）
        format: json                      # 返回格式：json / text / xml

      # 方案 B：私密代理（每次调 API 提取，账号固定 IP）
      private:
        enabled: false                    # 设为 true 启用私密代理
        num: 1                            # 单次提取数量（建议 1）
        pt: 1                             # IP 协议：1=http 2=socks5
        distinct: true                    # 去重提取：避免分到其他账号用过的 IP
        format: json                      # 返回格式：json / text / xml
        areaCode: ""                      # 可选：限定 IP 地区编码
        isp: 0                            # 运营商筛选：0=不筛选 1=电信 2=移动 3=联通
        keepAliveSec: 120                 # IP 可用时长（秒）
```

### 4. 两种方案的请求流

**方案 A：隧道代理**

```
XianyuMtopApiClient
    ↓ acquire(ProxyAcquireRequest)
KuaidailiTunnelProvider
    ↓ 调用 tps.kdlapi.com/api/gettps → 返回固定 host:port
    ↓ 快代理云端自动切每个请求的出口 IP
DefaultProxyPoolManager
    ↓ cache binding (accountId → kdl-tunnel)
    ↓ 返回 ProxyLease
```

* 请求示例：`http://secretId:secretKey@tps121.kdlapi.com:15818`
* 快代理云端自动路由到不同出口 IP，SDK 无感知
* 支持通过 `changetpsip` 接口主动换 IP

**方案 B：私密代理**

```
XianyuMtopApiClient
    ↓ acquire(ProxyAcquireRequest)
KuaidailiPrivateProvider
    ↓ 调用 dps.kdlapi.com/api/getdps → 返回真实 ip:port
    ↓ 构造 ProxyInfo（带 expireAt）
DefaultProxyPoolManager
    ↓ binding (accountId → ip:port)
    ↓ 健康检查
    ↓ 返回 ProxyLease
```

* 请求示例：`http://123.54.55.24:59419`（使用 secretId/secretKey 作为账密）
* SDK 记录 `expireAt`（到期自动失效）
* 可开启 `distinct=true` 去重，避免分到已用过的 IP
* 支持 `checkdpsvalid` / `getdpsvalidtime` 检测代理有效性

### 5. 鉴权方式

| 方式 | 说明 | 配置 |
|---|---|---|
| **token**（默认） | 调用 `auth.kdlapi.com/api/get_secret_token` 获取 token，直接作为 signature | `authType: token` |
| **hmacsha1** | 用 secret_key 对请求串做 HMAC-SHA1 + Base64 签名，更安全 | `authType: hmacsha1` |

参考文档：https://help.kuaidaili.com/api/auth/

### 6. 推荐优先级

在 `ProxyPoolRegistrar` 中，默认按注册顺序排列：

```
私密快代理 → 隧道快代理 → 阿布云 → 直连兜底
```

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
  direct-mode-auto-fallback: true   # 无代理时自动直连（false=抛异常）

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
