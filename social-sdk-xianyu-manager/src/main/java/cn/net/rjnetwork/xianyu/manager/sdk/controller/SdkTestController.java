package cn.net.rjnetwork.xianyu.manager.sdk.controller;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.api.XianyuCaptchaService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SDK 能力直暴 Controller — 给前端测试面板（sdk-test.html）暴露的真接 SDK 入口。
 * <p>所有方法都是「按 accountId 拿 cookie → new XianyuApiFacade → 真调 SDK → 返回 JsonNode」，
 * 不落本地 DB，直接看闲鱼平台真响应。供前端「一键测试」按钮调通验证 SDK 能力真闭环。</p>
 *
 * <p>真验接口名走真接通；候选接口名调通后看到 FAIL_SYS_API_NOT_FOUNDED 即知是命名规律候选错，
 * 后续真抓闲鱼 App WebView 域后微调即可，无需改前端。</p>
 */
@RestController
@RequestMapping("/api")
public class SdkTestController {

    private final AccountMapper accountMapper;

    public SdkTestController(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    private XianyuApiFacade facade(Long accountId) {
        XianyuAccount a = accountMapper.selectById(accountId);
        if (a == null) throw new IllegalArgumentException("账号不存在: " + accountId);
        if (a.getCookieHeader() == null || a.getCookieHeader().isBlank())
            throw new IllegalStateException("账号未设置 Cookie: " + accountId);
        return new XianyuApiFacade(a.getCookieHeader());
    }

    // ==================== 商品删除/擦亮（真验接口名） ====================

    /** 真删闲鱼商品 — SDK mtop.alibaba.idle.seller.pc.item.delete v1.0 */
    @PostMapping("/products/sdk-delete")
    @Audit("真删闲鱼商品")
    public ApiResponse<JsonNode> sdkDelete(@RequestParam Long accountId, @RequestParam String itemId) {
        return ApiResponse.ok(facade(accountId).deleteProduct(itemId));
    }

    /** 商品擦亮（提升曝光排名）— SDK mtop.taobao.idle.item.polish v1.0 */
    @PostMapping("/products/sdk-polish")
    @Audit("擦亮闲鱼商品")
    public ApiResponse<JsonNode> sdkPolish(@RequestParam Long accountId, @RequestParam String itemId) {
        return ApiResponse.ok(facade(accountId).polishItem(itemId));
    }

    // ==================== 虚拟发货/免拼发货/关闭订单（真验接口名） ====================

    /** 虚拟发货（确认发货/无需物流）— SDK mtop.taobao.idle.logistic.consign.dummy v1.0 */
    @PostMapping("/orders/sdk-dummy")
    public ApiResponse<JsonNode> sdkDummy(@RequestParam Long accountId, @RequestParam String orderId) {
        return ApiResponse.ok(facade(accountId).dummyDelivery(orderId));
    }

    /** 免拼发货（团购免拼一键发货）— SDK mtop.idle.groupon.activity.seller.freeshipping v1.0 */
    @PostMapping("/orders/sdk-freeshipping")
    public ApiResponse<JsonNode> sdkFreeShipping(@RequestParam Long accountId, @RequestParam String orderId,
                                                  @RequestParam String itemId, @RequestParam String buyerId) {
        return ApiResponse.ok(facade(accountId).freeShipping(orderId, itemId, buyerId));
    }

    /** 卖家主动关闭订单 — SDK mtop.taobao.idle.trade.merchant.close.by.seller v2.0 */
    @PostMapping("/orders/sdk-close")
    public ApiResponse<JsonNode> sdkClose(@RequestParam Long accountId, @RequestParam String orderNo) {
        return ApiResponse.ok(facade(accountId).closeOrderBySeller(orderNo));
    }

    // ==================== 资料页 nav/head（真验接口名） ====================

    /** 拉资料 nav — SDK mtop.idle.web.user.page.nav */
    @GetMapping("/profile/nav")
    public ApiResponse<JsonNode> profileNav(@RequestParam Long accountId) {
        return ApiResponse.ok(facade(accountId).getUserPageNav());
    }

    /** 拉资料 head — SDK mtop.idle.web.user.page.head */
    @GetMapping("/profile/head")
    public ApiResponse<JsonNode> profileHead(@RequestParam Long accountId) {
        return ApiResponse.ok(facade(accountId).getUserPageHead(true));
    }

    // ==================== 评价/退款/数据概览（真验接口名） ====================

    /** 评价列表 — SDK mtop.idle.web.trade.rate.list v1.0 */
    @GetMapping("/trade/reviews")
    public ApiResponse<JsonNode> reviews(@RequestParam Long accountId,
                                          @RequestParam(defaultValue = "1") String page,
                                          @RequestParam(defaultValue = "20") String pageSize) {
        return ApiResponse.ok(facade(accountId).getReviewList(null, page, pageSize));
    }

    /** 退款/售后列表 — SDK mtop.taobao.idle.merchant.refund.list v1.0 */
    @GetMapping("/trade/refunds")
    public ApiResponse<JsonNode> refunds(@RequestParam Long accountId,
                                          @RequestParam(defaultValue = "1") String page,
                                          @RequestParam(defaultValue = "20") String pageSize) {
        return ApiResponse.ok(facade(accountId).getRefundList(null, page, pageSize));
    }

    /** 卖家数据概览 — SDK mtop.alibaba.idle.seller.pc.datacompass.singleuser.seller.summary v1.0 */
    @GetMapping("/trade/seller-summary")
    public ApiResponse<JsonNode> sellerSummary(@RequestParam Long accountId) {
        return ApiResponse.ok(facade(accountId).getSellerSummary());
    }

    /** 流量分布概览 — SDK mtop.alibaba.idle.seller.pc.datacompass.singleuser.browse.summary v1.0 */
    @GetMapping("/trade/browse-summary")
    public ApiResponse<JsonNode> browseSummary(@RequestParam Long accountId) {
        return ApiResponse.ok(facade(accountId).getBrowseSummary());
    }

    // ==================== 黑名单/红花（真验接口名） ====================

    /** 查黑名单 — SDK mtop.taobao.idlemessage.pc.blacklist.query v1.0 */
    @GetMapping("/im/blacklist")
    public ApiResponse<JsonNode> blacklistQuery(@RequestParam Long accountId) {
        return ApiResponse.ok(facade(accountId).queryBlacklist());
    }

    /** 添加黑名单 — SDK mtop.taobao.idlemessage.pc.blacklist.add v2.0 */
    @PostMapping("/im/blacklist/add")
    public ApiResponse<JsonNode> blacklistAdd(@RequestParam Long accountId, @RequestParam String userId) {
        return ApiResponse.ok(facade(accountId).addBlacklist(userId));
    }

    /** 移除黑名单 — SDK mtop.taobao.idlemessage.pc.blacklist.remove v1.0 */
    @PostMapping("/im/blacklist/remove")
    public ApiResponse<JsonNode> blacklistRemove(@RequestParam Long accountId, @RequestParam String userId) {
        return ApiResponse.ok(facade(accountId).removeBlacklist(userId));
    }

    /** 红花（点赞/送花）— SDK mtop.taobao.idlemessage.red.flower v1.0 */
    @PostMapping("/im/red-flower")
    public ApiResponse<JsonNode> redFlower(@RequestParam Long accountId,
                                            @RequestParam String targetId, @RequestParam String targetType) {
        return ApiResponse.ok(facade(accountId).sendRedFlower(targetId, targetType));
    }

    // ==================== 验证码解题（真验通的判定流程接口） ====================

    /** 登录续期检测 — SDK mtop.taobao.idlemessage.pc.loginuser.get v1.0 */
    @GetMapping("/login/captcha/check-renew")
    public ApiResponse<JsonNode> checkRenew(@RequestParam Long accountId) {
        return ApiResponse.ok(facade(accountId).checkLoginRenew());
    }

    /** 重新请求 token 拿新鲜验证链接 — SDK XianyuCaptchaService.requestFreshCaptchaUrl */
    @GetMapping("/login/captcha/fresh-url")
    public ApiResponse<XianyuCaptchaService.CaptchaRefetchResult> freshUrl(
            @RequestParam Long accountId, @RequestParam(required = false) String deviceId) {
        return ApiResponse.ok(facade(accountId).requestFreshCaptchaUrl(deviceId));
    }

    /** 判断 URL 是否仍在风控 punish 页 — SDK XianyuCaptchaService.isPunishUrl */
    @GetMapping("/login/captcha/is-punish")
    public ApiResponse<Map<String, Object>> isPunish(@RequestParam String url) {
        // 这个不需要账号，直接静态判断
        XianyuCaptchaService stub = new XianyuCaptchaService(null);
        return ApiResponse.ok(Map.of("isPunish", stub.isPunishUrl(url), "url", url));
    }

    /** 计算滑块需滑动的距离 — SDK XianyuCaptchaService.calculateSlideDistance */
    @GetMapping("/login/captcha/distance")
    public ApiResponse<Map<String, Object>> distance(
            @RequestParam double track, @RequestParam double btn,
            @RequestParam(defaultValue = "false") boolean scratch) {
        XianyuCaptchaService stub = new XianyuCaptchaService(null);
        double d = stub.calculateSlideDistance(track, btn, scratch);
        return ApiResponse.ok(Map.of("distance", d, "track", track, "btn", btn, "scratch", scratch));
    }
}
