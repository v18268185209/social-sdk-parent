package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

/**
 * 闲鱼 API 门面类
 * 整合所有 API 服务，提供统一的 API 调用入口。
 *
 * <p>覆盖能力范围（按优先级排序）：</p>
 * <ol>
 *   <li>登录 — 二维码登录 / Cookie 登录 / 登录态检测</li>
 *   <li>个人信息 — 昵称、主页、信用等</li>
 *   <li>商品管理 — 列表、上下架、详情、搜索、Feed</li>
 *   <li>商品编辑 — 编辑/批量上下架/价格调整/库存/分类/删除/复制/统计</li>
 *   <li>商品发布 — 预加载数据/草稿管理/AI分类/价格建议/运费模板</li>
 *   <li>图片上传 — MTOP 接口上传到 CDN</li>
 *   <li>消息收发 — 会话列表、发消息、收消息</li>
 *   <li>订单管理 — 我买到的、我卖出的、详情、自动发货</li>
 *   <li>交易辅助 — 评价、退款/售后、议价、举报、地址管理</li>
 *   <li>收藏关注 — 收藏列表、关注列表、足迹</li>
 *   <li>钱包资金 — 余额、账单、提现、银行卡</li>
 * </ol>
 */
public class XianyuApiFacade {

    private final XianyuMtopApiClient apiClient;
    private final XianyuLoginApiService loginApiService;
    private final XianyuProfileApiService profileApiService;
    private final XianyuProductApiService productApiService;
    private final XianyuProductEditApiService productEditApiService;
    private final XianyuPublishFormApiService publishFormApiService;
    private final XianyuPublishApiService publishApiService;
    private final XianyuMediaUploadApiService mediaUploadApiService;
    private final XianyuMessageApiService messageApiService;
    private final XianyuOrderApiService orderApiService;
    private final XianyuTradeAuxApiService tradeAuxApiService;
    private final XianyuCollectApiService collectApiService;
    private final XianyuWalletApiService walletApiService;
    private final XianyuCaptchaService captchaService;

    public XianyuApiFacade(String cookie) {
        this.apiClient = new XianyuMtopApiClient(cookie);
        this.loginApiService = new XianyuLoginApiService(cookie);
        this.profileApiService = new XianyuProfileApiService(apiClient);
        this.productApiService = new XianyuProductApiService(apiClient);
        this.productEditApiService = new XianyuProductEditApiService(apiClient);
        this.publishFormApiService = new XianyuPublishFormApiService(apiClient);
        this.publishApiService = new XianyuPublishApiService(apiClient);
        this.mediaUploadApiService = new XianyuMediaUploadApiService(apiClient);
        this.messageApiService = new XianyuMessageApiService(apiClient);
        this.orderApiService = new XianyuOrderApiService(apiClient);
        this.tradeAuxApiService = new XianyuTradeAuxApiService(apiClient);
        this.collectApiService = new XianyuCollectApiService(apiClient);
        this.walletApiService = new XianyuWalletApiService(apiClient);
        this.captchaService = new XianyuCaptchaService(cookie);
    }

    // ==================== Cookie 管理 ====================

    /** 获取当前 Cookie */
    public String getCookie() {
        return apiClient.getCookie();
    }

    /** 更新 Cookie（登录成功后调用）*/
    public void updateCookie(String newCookie) {
        apiClient.updateCookie(newCookie);
    }

    // ======================== 1. 登录 ========================

    /** 创建二维码登录会话 */
    public XianyuLoginApiService.QrLoginResult createQrLoginSession() {
        return loginApiService.createQrLoginSession();
    }

    /** 轮询二维码状态 */
    public XianyuLoginApiService.QrLoginResult pollQrStatus(String sessionId) {
        return loginApiService.pollQrStatus(sessionId);
    }

    /** 阻塞等待二维码登录完成（默认 5 分钟超时）*/
    public XianyuLoginApiService.QrLoginResult waitForLogin(String sessionId, long timeoutSeconds) {
        return loginApiService.waitForLogin(sessionId, timeoutSeconds);
    }

    /** 阻塞等待二维码登录完成（默认 5 分钟超时）*/
    public XianyuLoginApiService.QrLoginResult waitForLogin(String sessionId) {
        return loginApiService.waitForLogin(sessionId, 300);
    }

    /** 取消二维码登录会话 */
    public boolean cancelQrLogin(String sessionId) {
        return loginApiService.cancelQrLogin(sessionId);
    }

    /** Cookie 登录 */
    public XianyuLoginApiService.LoginResult cookieLogin(String cookieHeader) {
        return loginApiService.cookieLogin(cookieHeader);
    }

    /** 检测当前会话是否已登录 */
    public XianyuLoginApiService.LoginStatusResult checkLoginStatus() {
        return loginApiService.checkLoginStatus();
    }

    /** 检测指定 Cookie 的登录状态 */
    public XianyuLoginApiService.LoginStatusResult checkLoginStatus(String cookieHeader) {
        return loginApiService.checkLoginStatus(cookieHeader);
    }

    /** 清理过期的二维码登录会话 */
    public void cleanupQrSessions() {
        loginApiService.cleanupExpiredSessions();
    }

    // ======================== 2. 个人信息 ========================

    public JsonNode getUserInfo() { return profileApiService.getUserInfo(); }
    public JsonNode getLoginUserInfo() { return profileApiService.getLoginUserInfo(); }
    public JsonNode getUserHomePage(String userId) { return profileApiService.getUserHomePage(userId); }
    public JsonNode getUserPageNav() { return profileApiService.getUserPageNav(); }
    public JsonNode getUserCredit(String userId) { return profileApiService.getUserCredit(userId); }

    // ======================== 3. 商品管理 ========================

    public JsonNode searchProducts(String keyword, String page, String pageSize) {
        return productApiService.searchProducts(keyword, page, pageSize);
    }
    public JsonNode getMyProducts(String page, String pageSize) {
        return productApiService.getMyProducts(page, pageSize);
    }
    public JsonNode getProductDetail(String itemId) {
        return productApiService.getProductDetail(itemId);
    }
    public JsonNode updateProductStatus(String itemId, String status) {
        return productApiService.updateProductStatus(itemId, status);
    }
    public JsonNode getHomeFeed() { return productApiService.getHomeFeed(); }
    public JsonNode activateSearch(String keyword) { return productApiService.activateSearch(keyword); }
    public JsonNode getSearchShade() { return productApiService.getSearchShade(); }

    // ======================== 4. 商品编辑（新增）========================

    /** 编辑商品基本信息 */
    public JsonNode editProduct(String itemId, String title, String description,
                                 String price, String originalPrice,
                                 String categoryId, String location) {
        return productEditApiService.editProduct(itemId, title, description, price, originalPrice, categoryId, location);
    }

    /** 编辑商品详情图 */
    public JsonNode editProductDetails(String itemId, String images) {
        return productEditApiService.editProductDetails(itemId, images);
    }

    /** 商品上架 */
    public JsonNode shelfOn(String itemId) {
        return productEditApiService.shelfOn(itemId);
    }

    /** 商品下架 */
    public JsonNode shelfOff(String itemId) {
        return productEditApiService.shelfOff(itemId);
    }

    /** 批量上架 */
    public JsonNode batchShelfOn(String itemIds) {
        return productEditApiService.batchShelfOn(itemIds);
    }

    /** 批量下架 */
    public JsonNode batchShelfOff(String itemIds) {
        return productEditApiService.batchShelfOff(itemIds);
    }

    /** 调整价格 */
    public JsonNode updatePrice(String itemId, String price) {
        return productEditApiService.updatePrice(itemId, price);
    }

    /** 调整原价 */
    public JsonNode updateOriginalPrice(String itemId, String originalPrice) {
        return productEditApiService.updateOriginalPrice(itemId, originalPrice);
    }

    /** 调整库存 */
    public JsonNode updateStock(String itemId, String stock) {
        return productEditApiService.updateStock(itemId, stock);
    }

    /** 获取分类列表 */
    public JsonNode getCategoryList(String parentId) {
        return productEditApiService.getCategoryList(parentId);
    }

    /** AI 推荐分类 */
    public JsonNode recommendCategory(String title, String description) {
        return productEditApiService.recommendCategory(title, description);
    }

    /** 删除商品 */
    public JsonNode deleteProduct(String itemId) {
        return productEditApiService.deleteProduct(itemId);
    }

    /** 批量删除商品 */
    public JsonNode batchDeleteProducts(String itemIds) {
        return productEditApiService.batchDeleteProducts(itemIds);
    }

    /** 复制商品（一键转卖）*/
    public JsonNode copyProduct(String sourceItemId) {
        return productEditApiService.copyProduct(sourceItemId);
    }

    /** 获取商品完整信息 */
    public JsonNode getProductFullInfo(String itemId) {
        return productEditApiService.getProductFullInfo(itemId);
    }

    /** 获取商品浏览量统计 */
    public JsonNode getViewStats(String itemId) {
        return productEditApiService.getViewStats(itemId);
    }

    // ======================== 5. 商品发布表单（新增）========================

    /** 获取发布页预加载数据 */
    public JsonNode getPublishPreData() {
        return publishFormApiService.getPublishPreData();
    }

    /** 获取默认发货地址 */
    public JsonNode getDefaultLocation() {
        return publishFormApiService.getDefaultLocation();
    }

    /** 获取价格模板 */
    public JsonNode getPriceTemplate() {
        return publishFormApiService.getPriceTemplate();
    }

    /** 保存商品草稿 */
    public JsonNode saveDraft(String title, String description, String price,
                               String categoryId, String images,
                               String location, String shippingType) {
        return publishFormApiService.saveDraft(title, description, price, categoryId, images, location, shippingType);
    }

    /** 获取草稿列表 */
    public JsonNode getDraftList(String page, String pageSize) {
        return publishFormApiService.getDraftList(page, pageSize);
    }

    /** 删除草稿 */
    public JsonNode deleteDraft(String draftId) {
        return publishFormApiService.deleteDraft(draftId);
    }

    /** 加载草稿 */
    public JsonNode loadDraft(String draftId) {
        return publishFormApiService.loadDraft(draftId);
    }

    /** AI 智能推荐分类 */
    public JsonNode aiRecommendCategory(String title, String description) {
        return publishFormApiService.aiRecommendCategory(title, description);
    }

    /** 获取分类树 */
    public JsonNode getCategoryTree(String level) {
        return publishFormApiService.getCategoryTree(level);
    }

    /** 获取同类商品价格建议 */
    public JsonNode suggestPrice(String categoryId, String keyword) {
        return publishFormApiService.suggestPrice(categoryId, keyword);
    }

    /** 获取运费模板列表 */
    public JsonNode getShippingTemplates() {
        return publishFormApiService.getShippingTemplates();
    }

    /** 设置发货方式 */
    public JsonNode setShippingMethod(String method) {
        return publishFormApiService.setShippingMethod(method);
    }

    /** 创建商品（旧版兼容）*/
    public JsonNode createProduct(String title, String price, String description, String categoryId, String images) {
        return publishApiService.createProduct(title, price, description, categoryId, images);
    }

    /** 创建商品（JSON body 方式）*/
    public JsonNode createProductWithBody(String title, String price, String description) {
        return publishApiService.createProductWithBody(title, price, description);
    }

    // ======================== 6. 图片上传（新增）========================

    /**
     * 上传图片到闲鱼 CDN
     * @param imagePath 本地图片路径
     * @return 上传结果（含 CDN URL、宽高）
     */
    public XianyuMediaUploadApiService.UploadResult uploadImage(Path imagePath) {
        return mediaUploadApiService.uploadViaMtop(imagePath);
    }

    /**
     * 通过 Base64 上传图片
     * @param base64Image Base64 编码的图片
     * @param fileName 文件名
     * @param mimeType MIME 类型
     * @return 上传结果
     */
    public XianyuMediaUploadApiService.UploadResult uploadImageFromBase64(String base64Image, String fileName, String mimeType) {
        return mediaUploadApiService.uploadFromBase64(base64Image, fileName, mimeType);
    }

    /**
     * 获取上传结果中的 CDN URL（便捷方法）
     * @param result 上传结果
     * @return CDN URL，失败返回 null
     */
    public static String getCdnUrl(XianyuMediaUploadApiService.UploadResult result) {
        return (result != null && result.success) ? result.cdnUrl : null;
    }

    // ======================== 7. 消息收发 ========================

    public JsonNode getSessionList() { return messageApiService.getSessionList(); }
    public JsonNode sendMessage(String sessionId, String content, String receiverId) {
        try {
            return messageApiService.sendMessage(sessionId, content, receiverId);
        } catch (Exception e) {
            System.err.println("[Facade sendMessage] " + e.getMessage());
            return null;
        }
    }
    public JsonNode getMessageHistory(String sessionId, String page) {
        // 兼容旧 facade 调用：page 字符串转 Integer size，传 null 用默认 20
        Integer size = null;
        if (page != null && !page.isBlank()) {
            try { size = Integer.parseInt(page.trim()); } catch (NumberFormatException ignored) {}
        }
        try {
            return messageApiService.getMessageHistory(sessionId, size);
        } catch (Exception e) {
            System.err.println("[Facade getMessageHistory] " + e.getMessage());
            return null;
        }
    }

    // ======================== 8. 订单管理 ========================

    public JsonNode getOrderList(String tab, String page) { return orderApiService.getOrderList(tab, page); }
    public JsonNode getOrderDetail(String orderId) { return orderApiService.getOrderDetail(orderId); }
    public JsonNode delivery(String orderId, String trackingNo) { return orderApiService.delivery(orderId, trackingNo); }

    // ======================== 9. 交易辅助 ========================

    public JsonNode reviewOrder(String orderId, String rating, String content) {
        return tradeAuxApiService.reviewOrder(orderId, rating, content);
    }
    public JsonNode getReviewList(String buyerId, String page, String pageSize) {
        return tradeAuxApiService.getReviewList(buyerId, page, pageSize);
    }
    public JsonNode applyRefund(String orderId, String reason, String amount) {
        return tradeAuxApiService.applyRefund(orderId, reason, amount);
    }
    public JsonNode getRefundList(String disputeStatus, String page, String pageSize) {
        return tradeAuxApiService.getRefundList(disputeStatus, page, pageSize);
    }
    public JsonNode getSellerSummary() {
        return tradeAuxApiService.getSellerSummary();
    }
    public JsonNode getBrowseSummary() {
        return tradeAuxApiService.getBrowseSummary();
    }
    public JsonNode getRefundDetail(String refundId) {
        return tradeAuxApiService.getRefundDetail(refundId);
    }
    public JsonNode offerPrice(String itemId, String price) {
        return tradeAuxApiService.offerPrice(itemId, price);
    }
    public JsonNode getPriceOffers(String page, String pageSize) {
        return tradeAuxApiService.getPriceOffers(page, pageSize);
    }
    public JsonNode reportTarget(String targetType, String targetId, String reasonType, String reasonDetail) {
        return tradeAuxApiService.reportTarget(targetType, targetId, reasonType, reasonDetail);
    }
    public JsonNode getAddressList() { return tradeAuxApiService.getAddressList(); }
    public JsonNode addAddress(String receiver, String phone, String address) {
        return tradeAuxApiService.addAddress(receiver, phone, address);
    }
    public JsonNode deleteAddress(String addressId) {
        return tradeAuxApiService.deleteAddress(addressId);
    }

    // ======================== 10. 收藏关注 ========================

    public JsonNode getMyCollectList(String page, String pageSize) {
        return collectApiService.getMyCollectList(page, pageSize);
    }
    public JsonNode collectItem(String itemId) {
        return collectApiService.collectItem(itemId);
    }
    public JsonNode uncollectItem(String itemId) {
        return collectApiService.uncollectItem(itemId);
    }
    public JsonNode getMyFollowList(String page, String pageSize) {
        return collectApiService.getMyFollowList(page, pageSize);
    }
    public JsonNode followTarget(String targetId) {
        return collectApiService.followTarget(targetId);
    }
    public JsonNode unfollowTarget(String targetId) {
        return collectApiService.unfollowTarget(targetId);
    }
    public JsonNode getMyFootprint(String page, String pageSize) {
        return collectApiService.getMyFootprint(page, pageSize);
    }

    // ======================== 11. 钱包资金 ========================

    public JsonNode getBalance() { return walletApiService.getBalance(); }
    public JsonNode getBillList(String page, String pageSize) { return walletApiService.getBillList(page, pageSize); }
    public JsonNode withdraw(String amount) { return walletApiService.withdraw(amount); }
    public JsonNode getBankCards() { return walletApiService.getBankCards(); }

    // ======================== 12. 擦亮/虚拟发货/免拼发货/关闭订单（参考项目真验通） ========================

    /** 商品擦亮（提升曝光排名）— 真实接口 mtop.taobao.idle.item.polish v1.0 */
    public JsonNode polishItem(String itemId) { return productEditApiService.polishItem(itemId); }

    /** 虚拟发货（确认发货/无需物流）— 真实接口 mtop.taobao.idle.logistic.consign.dummy v1.0 */
    public JsonNode dummyDelivery(String orderId) { return tradeAuxApiService.dummyDelivery(orderId); }

    /** 免拼发货（团购免拼一键发货）— 真实接口 mtop.idle.groupon.activity.seller.freeshipping v1.0 */
    public JsonNode freeShipping(String orderId, String itemId, String buyerId) {
        return tradeAuxApiService.freeShipping(orderId, itemId, buyerId);
    }

    /** 卖家主动关闭订单 — 真实接口 mtop.taobao.idle.trade.merchant.close.by.seller v2.0 */
    public JsonNode closeOrderBySeller(String orderNo) { return tradeAuxApiService.closeOrderBySeller(orderNo); }

    // ======================== 13. 评价/退款（真接口名） ========================
    // 注意：reviewOrder/getReviewList/getRefundList/getSellerSummary/getBrowseSummary
    // 已在第 9 区交易辅助段定义（L364-379），这里不重复定义，避免编译撞。

    // ======================== 14. 验证码解题（captchaService） ========================

    /** 风控触发检测：判断接口响应是否被 punish 拦截 */
    public boolean isRiskControlTriggered(JsonNode response) { return captchaService.isRiskControlTriggered(response); }

    /** 从风控拦截响应里提取 punish 验证 URL */
    public String extractPunishUrl(JsonNode response) { return captchaService.extractPunishUrl(response); }

    /** 凭 cookie 重新请求 token 拿新鲜验证链接 */
    public XianyuCaptchaService.CaptchaRefetchResult requestFreshCaptchaUrl(String deviceId) {
        return captchaService.requestFreshCaptchaUrl(deviceId);
    }

    /** 风控触发时的统一处理入口 */
    public XianyuCaptchaService.CaptchaRefetchResult handleRiskControl(JsonNode response, String deviceId) {
        return captchaService.handleRiskControl(response, deviceId);
    }

    /** 判断 URL 是否仍在风控 punish 页 */
    public boolean isPunishUrl(String url) { return captchaService.isPunishUrl(url); }

    /** 计算滑块需滑动的距离（普通/刮刮乐） */
    public double calculateSlideDistance(double trackBoxWidth, double buttonBoxWidth, boolean isScratchCaptcha) {
        return captchaService.calculateSlideDistance(trackBoxWidth, buttonBoxWidth, isScratchCaptcha);
    }

    /** 判定滑块验证是否真通过 */
    public boolean isVerificationPassed(String currentUrl, String preX5sec, String currentX5sec, boolean sliderContainerDetached) {
        return captchaService.isVerificationPassed(currentUrl, preX5sec, currentX5sec, sliderContainerDetached);
    }

    /** 刮刮乐验证码特征检测 */
    public boolean isScratchCaptcha(String pageHtml) { return captchaService.isScratchCaptcha(pageHtml); }

    // ======================== 15. 登录续期（loginApiService） ========================

    /** 登录状态续期 — mtop.taobao.idlemessage.pc.loginuser.get v1.0 */
    public JsonNode checkLoginRenew() { return profileApiService.getLoginUserInfo(); }

    // ======================== 16. 通知/红花/黑名单（messageApiService） ========================

    /** 关闭平台通知 — mtop.taobao.idlemessage.pc.profile.notice.update v1.0 */
    public JsonNode closeNotice(String noticeId) { return messageApiService.closeNotice(noticeId); }

    /** 红花（点赞/送花）— mtop.taobao.idlemessage.red.flower v1.0 */
    public JsonNode sendRedFlower(String targetId, String targetType) {
        return messageApiService.sendRedFlower(targetId, targetType);
    }

    /** 查询黑名单 — mtop.taobao.idlemessage.pc.blacklist.query v1.0 */
    public JsonNode queryBlacklist() { return messageApiService.queryBlacklist(); }

    /** 添加黑名单 — mtop.taobao.idlemessage.pc.blacklist.add v2.0 */
    public JsonNode addBlacklist(String userId) { return messageApiService.addBlacklist(userId); }

    /** 移除黑名单 — mtop.taobao.idlemessage.pc.blacklist.remove v1.0 */
    public JsonNode removeBlacklist(String userId) { return messageApiService.removeBlacklist(userId); }

    // ======================== 17. 用户主页（profileApiService） ========================

    /** 获取用户主页头信息 — mtop.idle.web.user.page.head */
    public JsonNode getUserPageHead(boolean self) { return profileApiService.getUserPageHead(self); }

    /** 暴露 captchaService 实例（manager 层做浏览器解题时可能直访） */
    public XianyuCaptchaService getCaptchaService() { return captchaService; }
}
