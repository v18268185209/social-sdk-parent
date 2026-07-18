package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼 SDK 门面（多账号环境隔离的统一入口）。
 *
 * <p>上层（demo / starter / 业务应用）只与本类交互，不直接接触会话池或具体 bot。
 * 按 {@code accountKey} 路由到该账号专属的 {@link XianyuCdpSessionManager}、
 * {@link XianyuCdpBot}、{@link XianyuPublishBot}、{@link XianyuTradeBot}、
 * {@link XianyuAccountManager}，各账号的 CdpClient + target 互不干扰。</p>
 *
 * <p>典型用法：
 * <pre>
 *   XianyuSdk sdk = new XianyuSdk("http://117.147.111.183:30004", "https://www.goofish.com", 5);
 *   XianyuSdk.XianyuAccount acc = sdk.account("default");
 *
 *   // 1. 二维码登录
 *   XianyuApiFacade.QrLoginResult qr = acc.api().createQrLoginSession();
 *   System.out.println(qr.qrCodeDataUrl);  // base64 二维码
 *   XianyuApiFacade.QrLoginResult login = acc.api().waitForLogin(qr.sessionId);
 *   System.out.println(login.cookieHeader); // 登录成功后的 Cookie
 *
 *   // 2. 更新 Cookie 并调用 API
 *   acc.api().updateCookie(login.cookieHeader);
 *
 *   // 3. 使用各种 API
 *   JsonNode info = acc.api().getUserInfo();
 *   JsonNode products = acc.api().getMyProducts("1", "20");
 * </pre>
 *
 * <p>本类无 Spring 注解，由上层按需实例化（demo 在 @Bean 中 new，starter 在 auto-config 中 new）。
 * 线程安全；各账号 bot 不可跨账号复用（每账号独立 bot 实例）。</p>
 */
public class XianyuSdk {

    private final XianyuCdpSessionPool pool;

    public XianyuSdk(String endpoint, String xianyuUrl, int maxAccounts) {
        this.pool = new XianyuCdpSessionPool(endpoint, xianyuUrl, maxAccounts);
    }

    /** 默认单账号门面（maxAccounts=1，等价于单账号用法）。*/
    public XianyuSdk(String endpoint, String xianyuUrl) {
        this(endpoint, xianyuUrl, 1);
    }

    /** 选取账号上下文，返回该账号专属的能力聚合。*/
    public XianyuAccount account(String accountKey) {
        XianyuCdpSessionManager session = pool.sessionOf(accountKey);
        return new XianyuAccount(accountKey, session);
    }

    /** 默认账号（key="default"）。单账号场景下直接用此方法即可。*/
    public XianyuAccount account() {
        return account("default");
    }

    /** 列出当前所有账号与连接状态。*/
    public Map<String, Boolean> listAccounts() {
        return pool.listAccounts();
    }

    /** 释放某账号会话。*/
    public void release(String accountKey) {
        pool.release(accountKey);
    }

    /** 释放所有账号会话（容器关闭时调用）。*/
    public void destroyAll() {
        pool.destroyAll();
    }

    String getEndpoint() { return pool.getEndpoint(); }
    String getXianyuUrl() { return pool.getXianyuUrl(); }
    XianyuCdpSessionManager sessionRaw(String accountKey) { return pool.sessionOf(accountKey); }

    /**
     * 单账号的能力聚合（懒加载多个 bot，按需实例化）。
     * 每个 {@link XianyuAccount} 实例绑定一个 {@link XianyuCdpSessionManager}，
     * 多次调用 {@link #bot()} 复用同一 bot，避免重复建连。
     */
    public static class XianyuAccount {
        private final String key;
        private final XianyuCdpSessionManager session;
        private volatile XianyuCdpBot bot;
        private volatile XianyuPublishBot publishBot;
        private volatile XianyuProductBot productBot;
        private volatile XianyuTradeBot tradeBot;
        private volatile XianyuOrderBot orderBot;
        private volatile XianyuMessageBot messageBot;
        private volatile XianyuAccountManager accountManager;
        private volatile XianyuApiFacade apiFacade;
        private volatile String cachedCookie;  // 缓存从 CDP 提取的 Cookie

        XianyuAccount(String key, XianyuCdpSessionManager session) {
            this.key = key;
            this.session = session;
        }

        public String key() { return key; }
        public XianyuCdpSessionManager session() { return session; }

        /** 获取已建立的 CdpClient；未连接则建立。*/
        public CdpClient client() throws Exception { return session.getClient(); }

        /** 账号专属的基础能力 bot（懒加载，复用实例）。*/
        public XianyuCdpBot bot() throws Exception {
            if (bot == null) {
                synchronized (this) {
                    if (bot == null) {
                        bot = new XianyuCdpBot(client(), session.getXianyuUrl());
                    }
                }
            }
            return bot;
        }

        /** 账号专属的商品发布能力 bot（懒加载，复用实例）。*/
        public XianyuPublishBot publish() throws Exception {
            if (publishBot == null) {
                synchronized (this) {
                    if (publishBot == null) {
                        publishBot = new XianyuPublishBot(client(), session.getXianyuUrl(), bot());
                    }
                }
            }
            return publishBot;
        }

        /** 账号专属的商品能力聚合 bot（发布/编辑/上下架/批量/搜索/浏览/点赞，懒加载）。*/
        public XianyuProductBot product() throws Exception {
            if (productBot == null) {
                synchronized (this) {
                    if (productBot == null) {
                        productBot = new XianyuProductBot(client(), session.getXianyuUrl(), bot(), publish());
                    }
                }
            }
            return productBot;
        }

        /** 账号专属的交易闭环能力 bot（懒加载，复用实例）。*/
        public XianyuTradeBot trade() throws Exception {
            if (tradeBot == null) {
                synchronized (this) {
                    if (tradeBot == null) {
                        tradeBot = new XianyuTradeBot(client(), bot());
                    }
                }
            }
            return tradeBot;
        }

        /** 账号专属的订单能力聚合 bot（监控/自动收货/自动发货/批量/详情/评价，懒加载）。*/
        public XianyuOrderBot order() throws Exception {
            if (orderBot == null) {
                synchronized (this) {
                    if (orderBot == null) {
                        orderBot = new XianyuOrderBot(client(), bot());
                    }
                }
            }
            return orderBot;
        }

        /** 账号专属的消息能力聚合 bot（接收/回复/关键词/自定义词/AI 接管，懒加载）。*/
        public XianyuMessageBot message() throws Exception {
            if (messageBot == null) {
                synchronized (this) {
                    if (messageBot == null) {
                        messageBot = new XianyuMessageBot(client(), bot());
                    }
                }
            }
            return messageBot;
        }

        /** 账号专属的账户管理能力 bot（懒加载，复用实例）。*/
        public XianyuAccountManager accountManager() throws Exception {
            if (accountManager == null) {
                synchronized (this) {
                    if (accountManager == null) {
                        accountManager = new XianyuAccountManager(client(), bot());
                    }
                }
            }
            return accountManager;
        }

        // ---------- API 门面（新的 MTOP API 调用）----------

        /**
         * 账号专属的 API 门面（懒加载，复用实例）。
         * 首次调用时自动从 CDP 会话中提取 Cookie。
         */
        public XianyuApiFacade api() throws Exception {
            if (apiFacade == null) {
                synchronized (this) {
                    if (apiFacade == null) {
                        String cookie = extractCookieFromClient(client());
                        cachedCookie = cookie;
                        apiFacade = new XianyuApiFacade(cookie);
                    }
                }
            }
            return apiFacade;
        }

        /**
         * 用指定 Cookie 重新初始化 API 门面（登录成功后调用）。
         */
        public void refreshApiFacadeWithCookie(String newCookie) {
            synchronized (this) {
                cachedCookie = newCookie;
                apiFacade = new XianyuApiFacade(newCookie);
            }
        }

        /**
         * 从 CDP 浏览器会话中提取 Cookie。
         * 通过远端 Chrome DevTools Protocol 的 /json 接口获取目标标签页的 Cookie。
         */
        private String extractCookieFromClient(CdpClient cdpClient) throws Exception {
            // 如果已经有缓存的 Cookie，优先使用
            if (cachedCookie != null && !cachedCookie.isEmpty()) {
                return cachedCookie;
            }

            try {
                // 方法：通过 CDP 的 Network.getCookies 获取
                // 由于 CdpClient 可能没有直接暴露此方法，我们使用 HTTP 调用 CDP 端点
                String endpoint = session.getEndpoint().replaceAll("/$", "");
                
                // 尝试通过 CDP HTTP API 获取 cookies
                // CDP 提供了 Runtime.evaluate 可以执行 JS 获取 document.cookie
                String cookieFromEvaluate = extractCookieViaEvaluate(cdpClient);
                if (cookieFromEvaluate != null && !cookieFromEvaluate.isEmpty()) {
                    return cookieFromEvaluate;
                }

                // 备用：从 session 管理的已有 cookie 中获取
                return extractCookieFromSession();
            } catch (Exception e) {
                System.err.println("[XianyuSdk] Failed to extract cookie from CDP: " + e.getMessage());
                return "";
            }
        }

        /**
         * 通过 CDP Runtime.evaluate 执行 JavaScript 获取 document.cookie
         */
        private String extractCookieViaEvaluate(CdpClient cdpClient) throws Exception {
            try {
                // 调用 Runtime.evaluate 获取 document.cookie
                String cookie = cdpClient.evaluateString("document.cookie");
                if (cookie != null && !cookie.isEmpty()) {
                    return cookie;
                }
            } catch (Exception e) {
                // 如果 evaluate 失败，尝试其他方式
            }
            return null;
        }

        /**
         * 从 Session 管理中提取 Cookie
         */
        private String extractCookieFromSession() throws Exception {
            // 尝试通过 CDP 获取浏览器的 cookies
            String endpoint = session.getEndpoint().replaceAll("/$", "");
            
            // 方法1：通过 CDP 的 Target 获取当前页面的 cookie
            if (session.isConnected() && session.getClient() != null) {
                try {
                    // 尝试调用 CDP 的 Runtime.evaluate 获取 cookie
                    CdpClient client = session.getClient();
                    String cookie = client.evaluateString("document.cookie");
                    if (cookie != null && !cookie.isEmpty()) {
                        return cookie;
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }

            // 方法2：通过 HTTP 调用 CDP 端点的 /json 接口
            try {
                String listUrl = endpoint + "/json/list";
                HttpRequest req = HttpRequest.newBuilder(URI.create(listUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();
                
                HttpClient http = HttpClient.newBuilder()
                        .proxy(ProxySelector.of(null))
                        .build();
                
                HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode list = mapper.readTree(response.body());
                
                // 查找 goofish.com 的页面
                for (JsonNode target : list) {
                    String url = target.path("url").asText("");
                    if (url.contains("goofish.com") || url.contains("idle.fish")) {
                        String id = target.path("id").asText("");
                        // 通过 CDP 获取该页面的 cookie
                        return extractCookieFromTarget(endpoint, id);
                    }
                }
            } catch (Exception e) {
                // 忽略
            }

            return "";
        }

        /**
         * 通过 CDP 获取特定 target 的 cookie
         */
        private String extractCookieFromTarget(String endpoint, String targetId) throws Exception {
            // 使用 CDP WebSocket 发送请求获取 cookie
            // 这里简化处理：返回空字符串，让上层通过 QR 登录后手动设置 Cookie
            return "";
        }

        // ---------- 便捷转发到 bot（单账号高频用例免写 .bot()）----------

        public boolean verifyLogin() throws Exception { return bot().isLoggedIn(); }
        public Map<String, Object> loginStatus() throws Exception { return bot().loginStatus(); }
        public Map<String, Object> loginQr() throws Exception { return bot().getLoginQrBase64(); }
        public Map<String, Object> personalInfo() throws Exception { return bot().getBasicInfo(); }
        public java.util.List<Map<String, Object>> listProducts() throws Exception { return bot().listProducts(); }
        public Map<String, Object> upShelf(String keyword) throws Exception { return bot().upShelf(keyword); }
        public Map<String, Object> downShelf(String keyword) throws Exception { return bot().downShelf(keyword); }
        public java.util.List<Map<String, Object>> listConversations() throws Exception { return bot().listConversations(); }
        public Map<String, Object> sendMessage(String to, String text) throws Exception { return bot().sendMessage(to, text); }
        public String screenshotViewport() throws Exception { return bot().screenshotViewport(); }
    }

    /** 兼容单账号旧用法：返回默认账号的 bot。*/
    public XianyuCdpBot bot() throws Exception { return account().bot(); }
}
