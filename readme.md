# social-sdk-parent

`social-sdk-parent` 是多模块社交平台 SDK。当前已将闲鱼能力拆分为独立子模块：`social-sdk-xianyu`，不再放在 `social-sdk-chrome` 里。

## 闲鱼已集成能力

- 登录
- `cookies` 登录（无浏览器或最小浏览器介入）
- 二维码登录（服务端生成二维码会话，轮询状态后拿到登录 cookies）
- 浏览器手动登录回退（当未提供有效 cookies 时）
- 消息能力
- WebSocket 实时收发消息（包含自动重连、心跳、token 刷新、消息解码兜底）
- 文本消息发送
- 图片消息发送（支持 URL 或本地图片自动上传）
- MTOP 时间线拉取（失败时回退 DOM）

## 依赖模块

- `social-sdk-core`
- `social-sdk-chrome`
- `social-sdk-xianyu`
- `social-sdk-spring-boot-starter`

## 代码调用示例

### 1) Cookies 登录

```java
import cn.net.rjnetwork.chrome.config.ChromeConfig;
import cn.net.rjnetwork.core.model.SocialSession;
import cn.net.rjnetwork.xianyu.config.XianyuConfig;
import cn.net.rjnetwork.xianyu.model.XianyuCredentials;
import cn.net.rjnetwork.xianyu.service.XianyuProvider;

XianyuProvider provider = new XianyuProvider(new ChromeConfig(), new XianyuConfig());

XianyuCredentials credentials = new XianyuCredentials();
credentials.setAllowManualLogin(false); // 只允许 cookies，不弹人工登录
credentials.setCookieHeader("_m_h5_tk=xxx; _m_h5_tk_enc=yyy; cookie2=zzz; unb=uid");

SocialSession session = provider.authenticate(credentials);
```

也支持直接传 `Map<String, String>`：

```java
Map<String, String> cookies = new LinkedHashMap<>();
cookies.put("_m_h5_tk", "xxx");
cookies.put("_m_h5_tk_enc", "yyy");
cookies.put("cookie2", "zzz");
cookies.put("unb", "uid");

SocialSession session = provider.authenticate(cookies);
```

### 2) 二维码登录

```java
import cn.net.rjnetwork.core.model.SocialSession;
import cn.net.rjnetwork.xianyu.model.XianyuCredentials;
import cn.net.rjnetwork.xianyu.model.XianyuQrLoginSession;
import cn.net.rjnetwork.xianyu.service.XianyuProvider;

XianyuProvider provider = new XianyuProvider();

// 1. 创建二维码会话
XianyuQrLoginSession qr = provider.createQrLoginSession();
String sessionId = qr.getSessionId();

// qr.getQrCodeDataUrl() 可以直接给前端展示 <img src="...">
// 或者使用 qr.getQrContent() 自行生成二维码

// 2. 轮询扫码状态
XianyuQrLoginSession status;
while (true) {
    Thread.sleep(1500);
    status = provider.getQrLoginSessionStatus(sessionId);
    if ("success".equals(status.getStatus())
            || "expired".equals(status.getStatus())
            || "cancelled".equals(status.getStatus())
            || "verification_required".equals(status.getStatus())
            || "error".equals(status.getStatus())) {
        break;
    }
}

if ("success".equals(status.getStatus())) {
    // 3. 用二维码会话ID换登录态
    XianyuCredentials credentials = new XianyuCredentials();
    credentials.setQrLoginSessionId(sessionId);
    credentials.setAllowManualLogin(false);

    SocialSession session = provider.authenticate(credentials);
}
```

### 3) 实时消息订阅

```java
provider.subscribeMessages(session, message -> {
    System.out.println("chatId=" + message.getChatId() + ", content=" + message.getContent());
});
```

### 4) 发送消息（文本/图片）

```java
import cn.net.rjnetwork.core.model.SocialContent;

SocialContent text = new SocialContent();
text.setText("你好，这里是自动回复");
text.setRawData("{\"toUserId\":\"123456789\"}");
provider.postContent(session, text);

SocialContent image = new SocialContent();
image.setText("给你发一张图");
image.setRawData("{\"toUserId\":\"123456789\",\"imagePath\":\"/tmp/demo.jpg\"}");
provider.postContent(session, image);
```

## Spring Boot 配置

```yaml
social-sdk:
  enabled: true
  chrome:
    enabled: true
    headless: false
  xianyu:
    enabled: true
    realtime-enabled: true
    heartbeat-interval-seconds: 15
    heartbeat-timeout-seconds: 45
```

## Demo 模块（可直接串联全流程）

新增模块：`social-sdk-demo`

启动方式：

```bash
mvn -q -DskipTests install
mvn -q -f social-sdk-demo/pom.xml -DskipTests spring-boot:run
```

访问地址：

- `http://localhost:18080/`

默认已串好的能力：

- Cookies 登录
- 二维码登录（创建二维码、轮询状态、支持 success 后自动登录）
- 实时消息接收（启动/停止、SSE 实时流推送、拉取最近消息）
- 文本/图片消息发送
- 时间线查询

## 原生 CDP 能力（CdpClient）

`social-sdk-chrome` 原先只通过 Selenium WebDriver 驱动 Chrome，且网络/控制台/指纹等均以「JS 注入」方式实现，没有原始 CDP 通路。现已补充 `cn.net.rjnetwork.chrome.cdp.CdpClient`：基于 JDK `java.net.http.WebSocket` 直接对接任意 CDP 端点（本地或远程，例如 `http://192.168.1.127:9333`），对外暴露 Selenium 难以原生提供的能力：

- `Fetch` 请求拦截 / 改写 / 伪造响应（反爬绕过、MTOP 接口伪造、滑块校验绕过的核心）
- `Network.setUserAgentOverride` / `setExtraHTTPHeaders`（底层 UA 与反爬头覆盖，比 JS 重写可靠）
- `Emulation` 地理/时区/触摸/设备模拟
- `Performance.getMetrics`、`DOMSnapshot.captureSnapshot`、`Storage.clearDataForOrigin`

### 对接已运行的远程 Chrome

```java
import cn.net.rjnetwork.chrome.cdp.CdpClient;

// 直连远程调试端点（自动从 /json/version 发现 browser WebSocket）
CdpClient client = CdpClient.attachRemote("http://192.168.1.127:9333");
String targetId = client.createTarget("about:blank");
String sessionId = client.attachTarget(targetId);
client.setSessionId(sessionId);

client.navigate("https://example.com").join();
client.setUserAgentOverride("social-sdk/1.0");
client.enableFetchInterception(req -> client.continueRequest(req.get("requestId").asText()), true);
```

### 复用当前 Chrome 实例的 DevTools

```java
ChromeInstance instance = ...; // 已 start()
instance.getCdpClient().ifPresent(cdp -> cdp.setUserAgentOverride("social-sdk/1.0"));
```

### 能力测试

`cdp-test/cdp_harness.py` 为独立测试工具，逐项验证上述 CDP 能力（默认目标 `http://192.168.1.127:9333`，仅创建隔离测试目标，不改动你已打开的标签页）：

```bash
CDP_HTTP=http://192.168.1.127:9333 python cdp-test/cdp_harness.py
```

## 说明

- 闲鱼登录态校验默认关键 cookies 为：`_m_h5_tk`、`_m_h5_tk_enc`、`cookie2`。
- 二维码会话默认 300 秒过期。
- 若接入真实生产流程，建议在业务侧对 session/cookies 做持久化与轮换策略管理。
