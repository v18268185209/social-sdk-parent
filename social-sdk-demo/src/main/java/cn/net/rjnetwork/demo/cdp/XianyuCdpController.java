package cn.net.rjnetwork.demo.cdp;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import cn.net.rjnetwork.demo.model.ApiResponse;
import cn.net.rjnetwork.xianyu.model.PublishItem;
import cn.net.rjnetwork.xianyu.service.XianyuCdpBot;
import cn.net.rjnetwork.xianyu.service.XianyuCdpSessionManager;
import cn.net.rjnetwork.xianyu.service.XianyuPublishBot;
import cn.net.rjnetwork.xianyu.service.XianyuTradeBot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼 CDP 能力 REST 接口（验证壳）。
 * 所有操作均通过 SDK 层的 {@link XianyuCdpSessionManager} 和 {@link XianyuCdpBot} 完成。
 */
@RestController
@RequestMapping("/api/xianyu/cdp")
public class XianyuCdpController {

    private final XianyuCdpSessionManager sessionManager;

    public XianyuCdpController(XianyuCdpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    private XianyuCdpBot bot() {
        try {
            CdpClient client = sessionManager.getClient();
            return new XianyuCdpBot(client, sessionManager.getXianyuUrl());
        } catch (Exception e) {
            throw new IllegalStateException("无法获取 CDP 会话: " + e.getMessage(), e);
        }
    }

    @PostMapping("/connect")
    public ApiResponse<?> connect() {
        try {
            sessionManager.connect();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("endpoint", sessionManager.getEndpoint());
            data.put("connected", true);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/disconnect")
    public ApiResponse<?> disconnect() {
        try {
            sessionManager.reconnect(); // 重建连接以释放旧标签页
            return ApiResponse.ok(Map.of("disconnected", true));
        } catch (Exception e) {
            return ApiResponse.fail("断开/重建失败: " + e.getMessage());
        }
    }

    // ---------------- 1. 登录管理 ----------------

    @GetMapping("/login/status")
    public ApiResponse<?> loginStatus() {
        try {
            cn.net.rjnetwork.xianyu.service.XianyuCdpBot b = bot();
            Map<String, Object> status = b.loginStatus();
            if (!Boolean.TRUE.equals(status.get("pageReady"))) {
                sessionManager.reconnect();
                status = bot().loginStatus();
            }
            return ApiResponse.ok(status);
        } catch (Exception e) {
            return ApiResponse.fail("读取登录态失败: " + e.getMessage());
        }
    }

    @GetMapping("/login/qr")
    public ApiResponse<?> loginQr() {
        CdpClient c = null;
        try {
            c = sessionManager.acquireLoginTarget();
            XianyuCdpBot bot = new XianyuCdpBot(c, sessionManager.getXianyuUrl());
            Map<String, Object> qr = bot.getLoginQrBase64();
            return ApiResponse.ok(qr);
        } catch (Exception e) {
            return ApiResponse.fail("获取登录二维码失败: " + e.getMessage());
        } finally {
            if (c != null) {
                try { c.close(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * 长轮询：展示二维码后由前端调用，阻塞至登录成功或超时。
     * 用于「页面扫码 → 代码检测登录 → 自动继续业务」闭环。
     */
    @GetMapping("/login/wait")
    public ApiResponse<?> waitLogin(@RequestParam(value = "timeout", defaultValue = "90000") long timeout) {
        try {
            timeout = Math.min(Math.max(timeout, 1000), 120000);
            return ApiResponse.ok(bot().waitForLogin(timeout));
        } catch (Exception e) {
            return ApiResponse.fail("等待登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout() {
        try {
            return ApiResponse.ok(bot().logout());
        } catch (Exception e) {
            return ApiResponse.fail("退出登录失败: " + e.getMessage());
        }
    }

    // ---------------- 2. 基本信息管理 ----------------

    @GetMapping("/info")
    public ApiResponse<?> basicInfo() {
        try {
            return ApiResponse.ok(bot().getBasicInfo());
        } catch (Exception e) {
            return ApiResponse.fail("读取基本信息失败: " + e.getMessage());
        }
    }

    // ---------------- 3. 商品上下架 ----------------

    @GetMapping("/products")
    public ApiResponse<?> listProducts() {
        try {
            return ApiResponse.ok(bot().listProducts());
        } catch (Exception e) {
            return ApiResponse.fail("加载商品失败: " + e.getMessage());
        }
    }

    @PostMapping("/product/upshelf")
    public ApiResponse<?> upShelf(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(bot().upShelf(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("上架失败: " + e.getMessage());
        }
    }

    @PostMapping("/product/downshelf")
    public ApiResponse<?> downShelf(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(bot().downShelf(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("下架失败: " + e.getMessage());
        }
    }

    // ---------------- 4. 消息收发 ----------------

    @GetMapping("/messages")
    public ApiResponse<?> listMessages() {
        try {
            return ApiResponse.ok(bot().listConversations());
        } catch (Exception e) {
            return ApiResponse.fail("加载会话失败: " + e.getMessage());
        }
    }

    @PostMapping("/message/send")
    public ApiResponse<?> sendMessage(@RequestParam("conversation") String conversation,
                                      @RequestParam("text") String text) {
        try {
            return ApiResponse.ok(bot().sendMessage(conversation, text));
        } catch (Exception e) {
            return ApiResponse.fail("发送消息失败: " + e.getMessage());
        }
    }

    // ---------------- 5. 自动发货 ----------------

    @GetMapping("/shipments")
    public ApiResponse<?> listShipments() {
        try {
            return ApiResponse.ok(bot().listPendingShipments());
        } catch (Exception e) {
            return ApiResponse.fail("加载待发货失败: " + e.getMessage());
        }
    }

    @PostMapping("/shipment/ship")
    public ApiResponse<?> ship(@RequestParam("keyword") String keyword,
                               @RequestParam(value = "logisticsNo", required = false) String logisticsNo) {
        try {
            return ApiResponse.ok(bot().ship(keyword, logisticsNo));
        } catch (Exception e) {
            return ApiResponse.fail("发货失败: " + e.getMessage());
        }
    }

    // ---------------- 6. 收货验收 ----------------

    @GetMapping("/receipts")
    public ApiResponse<?> listReceipts() {
        try {
            return ApiResponse.ok(bot().listPendingReceipts());
        } catch (Exception e) {
            return ApiResponse.fail("加载待收货失败: " + e.getMessage());
        }
    }

    @PostMapping("/receipt/accept")
    public ApiResponse<?> accept(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(bot().acceptOrder(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("确认收货失败: " + e.getMessage());
        }
    }

    // ---------------- 7. 商品发布（草稿/正式） ----------------

    private XianyuPublishBot publishBot() {
        try {
            CdpClient client = sessionManager.getClient();
            return new XianyuPublishBot(client, sessionManager.getXianyuUrl(), bot());
        } catch (Exception e) {
            throw new IllegalStateException("无法获取 CDP 会话: " + e.getMessage(), e);
        }
    }

    @PostMapping("/publish/draft")
    public ApiResponse<?> saveDraft(@RequestBody PublishItem item) {
        try {
            return ApiResponse.ok(publishBot().saveDraft(item));
        } catch (Exception e) {
            return ApiResponse.fail("草稿保存失败: " + e.getMessage());
        }
    }

    @PostMapping("/publish/submit")
    public ApiResponse<?> publish(@RequestBody PublishItem item) {
        try {
            item.setPublish(true);
            return ApiResponse.ok(publishBot().fillAndPublish(item));
        } catch (Exception e) {
            return ApiResponse.fail("发布失败: " + e.getMessage());
        }
    }

    @GetMapping("/publish/form")
    public ApiResponse<?> inPublishForm() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("inForm", publishBot().isInPublishForm());
            data.put("screenshot", bot().screenshotViewport());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("检测发布表单失败: " + e.getMessage());
        }
    }

    // ---------------- 8. 交易闭环 ----------------

    private XianyuTradeBot tradeBot() {
        try {
            CdpClient client = sessionManager.getClient();
            return new XianyuTradeBot(client, bot());
        } catch (Exception e) {
            throw new IllegalStateException("无法获取 CDP 会话: " + e.getMessage(), e);
        }
    }

    @GetMapping("/trade/sold")
    public ApiResponse<?> soldOrders() {
        try {
            return ApiResponse.ok(tradeBot().monitorSoldOrders());
        } catch (Exception e) {
            return ApiResponse.fail("加载待发货订单失败: " + e.getMessage());
        }
    }

    @PostMapping("/trade/ship/batch")
    public ApiResponse<?> batchShip(@RequestParam("keyword") String keyword,
                                    @RequestParam(value = "logisticsNo", required = false) String logisticsNo) {
        try {
            return ApiResponse.ok(tradeBot().batchShip(keyword, logisticsNo));
        } catch (Exception e) {
            return ApiResponse.fail("批量发货失败: " + e.getMessage());
        }
    }

    @GetMapping("/trade/order/detail")
    public ApiResponse<?> orderDetail(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(tradeBot().orderDetail(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("查询订单详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/trade/review")
    public ApiResponse<?> review(@RequestParam("keyword") String keyword,
                                 @RequestParam(value = "comment", required = false) String comment,
                                 @RequestParam(value = "stars", defaultValue = "5") int stars) {
        try {
            return ApiResponse.ok(tradeBot().review(keyword, comment, stars));
        } catch (Exception e) {
            return ApiResponse.fail("提交评价失败: " + e.getMessage());
        }
    }

    @PostMapping("/trade/message/auto-reply")
    public ApiResponse<?> autoReply(@RequestParam(value = "filter", required = false) String filter,
                                    @RequestParam("reply") String reply) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("replied", tradeBot().autoReply(filter, reply));
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("自动回复失败: " + e.getMessage());
        }
    }

    // ---------------- 9. 账户管理（多账号环境隔离） ----------------

    private cn.net.rjnetwork.xianyu.service.XianyuSdk sdk() {
        // demo 单账号场景下直接复用默认会话管理器即可；多账号由 /account/list 管理
        return sdk;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cn.net.rjnetwork.xianyu.service.XianyuSdk sdk;

    @GetMapping("/accounts")
    public ApiResponse<?> listAccounts() {
        if (sdk == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("default", true);
            return ApiResponse.ok(data);
        }
        return ApiResponse.ok(sdk.listAccounts());
    }

    private cn.net.rjnetwork.xianyu.service.XianyuAccountManager accountManager() {
        try {
            return sdk == null
                    ? new cn.net.rjnetwork.xianyu.service.XianyuAccountManager(sessionManager.getClient(), bot())
                    : sdk.account().accountManager();
        } catch (Exception e) {
            throw new IllegalStateException("无法获取账户管理器: " + e.getMessage(), e);
        }
    }

    @GetMapping("/account/verify")
    public ApiResponse<?> verifyLogin() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("loggedIn", accountManager().verifyLogin());
            data.put("status", accountManager().loginStatus());
            data.put("nickname", accountManager().nickname());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("验证登录态失败: " + e.getMessage());
        }
    }

    @PostMapping("/account/logout")
    public ApiResponse<?> accountLogout() {
        try {
            return ApiResponse.ok(accountManager().logout());
        } catch (Exception e) {
            return ApiResponse.fail("登出失败: " + e.getMessage());
        }
    }

    @PostMapping("/account/switch")
    public ApiResponse<?> switchAccount() {
        try {
            return ApiResponse.ok(accountManager().switchAccount());
        } catch (Exception e) {
            return ApiResponse.fail("切换账号失败: " + e.getMessage());
        }
    }

    @GetMapping("/account/personal")
    public ApiResponse<?> personalInfo() {
        try {
            return ApiResponse.ok(accountManager().personalInfo());
        } catch (Exception e) {
            return ApiResponse.fail("读取个人信息失败: " + e.getMessage());
        }
    }

    // ---------------- 10. 商品能力聚合（编辑/批量/搜索/浏览/点赞） ----------------

    private cn.net.rjnetwork.xianyu.service.XianyuProductBot productBot() {
        try {
            CdpClient client = sessionManager.getClient();
            return new cn.net.rjnetwork.xianyu.service.XianyuProductBot(client, sessionManager.getXianyuUrl(), bot(), publishBot());
        } catch (Exception e) {
            throw new IllegalStateException("无法获取商品 bot: " + e.getMessage(), e);
        }
    }

    @PostMapping("/product/edit")
    public ApiResponse<?> editProduct(@RequestParam("keyword") String keyword, @RequestBody PublishItem item) {
        try {
            return ApiResponse.ok(productBot().edit(keyword, item));
        } catch (Exception e) {
            return ApiResponse.fail("编辑商品失败: " + e.getMessage());
        }
    }

    @PostMapping("/product/batch-shelf")
    public ApiResponse<?> batchShelf(@RequestParam(value = "keywords", required = false) String keywordsCsv,
                                      @RequestParam("action") String action) {
        try {
            java.util.List<String> kws = new java.util.ArrayList<>();
            if (keywordsCsv != null && !keywordsCsv.isBlank()) {
                for (String k : keywordsCsv.split(",")) {
                    if (!k.isBlank()) kws.add(k.trim());
                }
            }
            return ApiResponse.ok(productBot().batchShelf(kws, action));
        } catch (Exception e) {
            return ApiResponse.fail("批量上下架失败: " + e.getMessage());
        }
    }

    @GetMapping("/product/search")
    public ApiResponse<?> searchProduct(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(productBot().search(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("搜索商品失败: " + e.getMessage());
        }
    }

    @GetMapping("/product/visit")
    public ApiResponse<?> visitProduct(@RequestParam("url") String url,
                                        @RequestParam(value = "scroll", defaultValue = "3") int scroll) {
        try {
            return ApiResponse.ok(productBot().visit(url, scroll));
        } catch (Exception e) {
            return ApiResponse.fail("浏览商品失败: " + e.getMessage());
        }
    }

    @GetMapping("/product/like")
    public ApiResponse<?> likeProduct(@RequestParam("url") String url) {
        try {
            return ApiResponse.ok(productBot().like(url));
        } catch (Exception e) {
            return ApiResponse.fail("点赞失败: " + e.getMessage());
        }
    }

    @GetMapping("/product/browse-and-like")
    public ApiResponse<?> browseAndLike(@RequestParam("keyword") String keyword,
                                         @RequestParam(value = "max", defaultValue = "5") int max) {
        try {
            return ApiResponse.ok(productBot().browseAndLike(keyword, max));
        } catch (Exception e) {
            return ApiResponse.fail("自动浏览点赞失败: " + e.getMessage());
        }
    }

    // ---------------- 11. 消息能力聚合（关键词/自定义词/AI/群发） ----------------

    private cn.net.rjnetwork.xianyu.service.XianyuMessageBot messageBot() {
        try {
            return new cn.net.rjnetwork.xianyu.service.XianyuMessageBot(sessionManager.getClient(), bot());
        } catch (Exception e) {
            throw new IllegalStateException("无法获取消息 bot: " + e.getMessage(), e);
        }
    }

    @PostMapping("/message/reply-by-keyword")
    public ApiResponse<?> replyByKeyword(@RequestParam("keyword") String keyword, @RequestParam("reply") String reply) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sent", messageBot().replyByKeyword(keyword, reply));
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("关键词回复失败: " + e.getMessage());
        }
    }

    @PostMapping("/message/reply-by-rules")
    public ApiResponse<?> replyByRules(@RequestBody Map<String, String> rules) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sent", messageBot().replyByCustomRules(rules));
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("自定义词回复失败: " + e.getMessage());
        }
    }

    @PostMapping("/message/reply-by-ai")
    public ApiResponse<?> replyByAi(@RequestParam("aiUrl") String aiUrl,
                                    @RequestParam(value = "token", required = false) String token) {
        try {
            // 默认 AI 接管：用一个固定回复兜底，上层有 LLM 可改注入 Function
            java.util.function.Function<String, String> responder = ctx -> {
                try {
                    String prompt = java.net.URLEncoder.encode("你是闲鱼卖家客服，根据买家消息简短回复（≤50字）: " + ctx, "UTF-8");
                    java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                            .proxy(java.net.ProxySelector.of(null)).build();
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(aiUrl + "?prompt=" + prompt))
                            .timeout(java.time.Duration.ofSeconds(15)).GET().build();
                    if (token != null && !token.isBlank()) {
                        req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(aiUrl + "?prompt=" + prompt))
                                .header("Authorization", "Bearer " + token).timeout(java.time.Duration.ofSeconds(15)).GET().build();
                    }
                    String body = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    return body == null || body.isBlank() ? null : body.trim();
                } catch (Exception e) {
                    return "您好，商品在的，欢迎咨询~";
                }
            };
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sent", messageBot().replyByAi(responder));
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("AI 接管回复失败: " + e.getMessage());
        }
    }

    @PostMapping("/message/broadcast")
    public ApiResponse<?> broadcast(@RequestParam("text") String text) {
        try {
            return ApiResponse.ok(messageBot().broadcast(text));
        } catch (Exception e) {
            return ApiResponse.fail("群发失败: " + e.getMessage());
        }
    }

    // ---------------- 12. 订单能力聚合（监控/自动收货/批量发货/详情/评价） ----------------

    private cn.net.rjnetwork.xianyu.service.XianyuOrderBot orderBot() {
        try {
            return new cn.net.rjnetwork.xianyu.service.XianyuOrderBot(sessionManager.getClient(), bot());
        } catch (Exception e) {
            throw new IllegalStateException("无法获取订单 bot: " + e.getMessage(), e);
        }
    }

    @GetMapping("/order/sold")
    public ApiResponse<?> orderSold() {
        try {
            return ApiResponse.ok(orderBot().monitorSold());
        } catch (Exception e) {
            return ApiResponse.fail("加载待发货订单失败: " + e.getMessage());
        }
    }

    @GetMapping("/order/bought")
    public ApiResponse<?> orderBought() {
        try {
            return ApiResponse.ok(orderBot().monitorBought());
        } catch (Exception e) {
            return ApiResponse.fail("加载待收货订单失败: " + e.getMessage());
        }
    }

    @PostMapping("/order/ship/batch")
    public ApiResponse<?> orderBatchShip(@RequestParam("keyword") String keyword,
                                          @RequestParam(value = "logisticsNo", required = false) String logisticsNo) {
        try {
            return ApiResponse.ok(orderBot().batchShip(keyword, logisticsNo));
        } catch (Exception e) {
            return ApiResponse.fail("批量发货失败: " + e.getMessage());
        }
    }

    @PostMapping("/order/accept/batch")
    public ApiResponse<?> orderBatchAccept(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(orderBot().batchAccept(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("批量确认收货失败: " + e.getMessage());
        }
    }

    @GetMapping("/order/detail")
    public ApiResponse<?> orderDetail2(@RequestParam("keyword") String keyword) {
        try {
            return ApiResponse.ok(orderBot().detail(keyword));
        } catch (Exception e) {
            return ApiResponse.fail("查询订单详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/order/review")
    public ApiResponse<?> orderReview(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "comment", required = false) String comment,
                                       @RequestParam(value = "stars", defaultValue = "5") int stars) {
        try {
            return ApiResponse.ok(orderBot().review(keyword, comment, stars));
        } catch (Exception e) {
            return ApiResponse.fail("提交评价失败: " + e.getMessage());
        }
    }

    // ---------------- 工具 ----------------

    @PostMapping("/navigate")
    public ApiResponse<?> navigate(@RequestParam("url") String url) {
        try {
            bot().navigate(url);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            data.put("currentUrl", bot().getUrl());
            data.put("title", evalTitle());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("导航失败: " + e.getMessage());
        }
    }

    @PostMapping("/eval")
    public ApiResponse<?> eval(@RequestBody Map<String, Object> body) {
        try {
            String expression = (String) body.get("expression");
            if (expression == null || expression.isBlank()) {
                return ApiResponse.fail("expression 不能为空");
            }
            String result = bot().evalExpression(expression);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("result", result);
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("执行失败: " + e.getMessage());
        }
    }

    private String evalTitle() {
        try {
            String t = bot().evalExpression("document.title");
            return t != null ? t.replace("\"", "") : "";
        } catch (Exception e) {
            return "";
        }
    }

    @GetMapping("/screenshot")
    public ApiResponse<?> screenshot() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("image", bot().screenshotViewport());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("截图失败: " + e.getMessage());
        }
    }
}
