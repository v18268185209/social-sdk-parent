package cn.net.rjnetwork.demo.cdp;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import cn.net.rjnetwork.demo.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼 CDP 能力 REST 接口。所有操作均通过 {@link CdpSessionManager} 拿到远程 Chrome 上的
 * 闲鱼标签页，由 {@link XianyuCdpBot} 以 CDP 驱动完成。
 *
 * <p>未登录时请先调用 {@code GET /login/qr} 获取二维码，手机扫码登录；登录态下其余能力
 * 直接可用。</p>
 */
@RestController
@RequestMapping("/api/xianyu/cdp")
public class XianyuCdpController {

    private final CdpSessionManager sessionManager;

    public XianyuCdpController(CdpSessionManager sessionManager) {
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
            return ApiResponse.ok(bot().loginStatus());
        } catch (Exception e) {
            return ApiResponse.fail("读取登录态失败: " + e.getMessage());
        }
    }

    @GetMapping("/login/qr")
    public ApiResponse<?> loginQr() {
        try {
            // 每次取码前强制新建 target：旧 target 在后台越久越被 Chrome 节流冻结
            // （evaluate 卡死 60s），新建 target 短暂在前台活跃，保证秒出码。
            sessionManager.freshTarget();
            Map<String, Object> qr = bot().getLoginQrBase64();
            return ApiResponse.ok(qr);
        } catch (Exception e) {
            return ApiResponse.fail("获取登录二维码失败: " + e.getMessage());
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

    // ---------------- 工具 ----------------

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
