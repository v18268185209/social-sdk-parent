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
