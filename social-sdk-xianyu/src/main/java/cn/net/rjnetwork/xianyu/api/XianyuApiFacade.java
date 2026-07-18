package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼 API 门面类
 * 整合所有 API 服务，提供统一的 API 调用入口。
 *
 * <p>覆盖能力范围（按优先级排序）：</p>
 * <ol>
 *   <li>登录 — 二维码登录 / Cookie 登录 / 登录态检测</li>
 *   <li>个人信息 — 昵称、主页、信用等</li>
 *   <li>商品管理 — 列表、上下架、详情、搜索</li>
 *   <li>商品发布 — 草稿、发布</li>
 *   <li>消息收发 — 会话列表、发消息、收消息</li>
 *   <li>订单管理 — 我买到的、我卖出的、详情、自动发货</li>
 * </ol>
 */
public class XianyuApiFacade {

    private final XianyuMtopApiClient apiClient;
    private final XianyuLoginApiService loginApiService;
    private final XianyuProfileApiService profileApiService;
    private final XianyuProductApiService productApiService;
    private final XianyuPublishApiService publishApiService;
    private final XianyuMessageApiService messageApiService;
    private final XianyuOrderApiService orderApiService;

    public XianyuApiFacade(String cookie) {
        this.apiClient = new XianyuMtopApiClient(cookie);
        this.loginApiService = new XianyuLoginApiService(cookie);
        this.profileApiService = new XianyuProfileApiService(apiClient);
        this.productApiService = new XianyuProductApiService(apiClient);
        this.publishApiService = new XianyuPublishApiService(apiClient);
        this.messageApiService = new XianyuMessageApiService(apiClient);
        this.orderApiService = new XianyuOrderApiService(apiClient);
    }

    // ==================== Cookie 管理 ====================

    /** 获取当前 Cookie */
    public String getCookie() {
        return apiClient.getCookie();
    }

    /** 更新 Cookie（登录成功后调用）*/
    public void updateCookie(String newCookie) {
        apiClient.updateCookie(newCookie);
        // 同时更新登录服务的 Cookie
    }

    // ======================== 1. 登录 ========================

    /**
     * 创建二维码登录会话
     * @return 包含 base64 二维码图片的登录结果
     */
    public XianyuLoginApiService.QrLoginResult createQrLoginSession() {
        return loginApiService.createQrLoginSession();
    }

    /**
     * 轮询二维码状态
     * @param sessionId 会话 ID
     * @return 二维码状态结果
     */
    public XianyuLoginApiService.QrLoginResult pollQrStatus(String sessionId) {
        return loginApiService.pollQrStatus(sessionId);
    }

    /**
     * 阻塞等待二维码登录完成
     * @param sessionId 会话 ID
     * @param timeoutSeconds 超时秒数（默认 300 秒 = 5 分钟）
     * @return 登录结果（含 Cookie）
     */
    public XianyuLoginApiService.QrLoginResult waitForLogin(String sessionId, long timeoutSeconds) {
        return loginApiService.waitForLogin(sessionId, timeoutSeconds);
    }

    /**
     * 阻塞等待二维码登录完成（默认 5 分钟超时）
     */
    public XianyuLoginApiService.QrLoginResult waitForLogin(String sessionId) {
        return loginApiService.waitForLogin(sessionId, 300);
    }

    /**
     * 取消二维码登录会话
     */
    public boolean cancelQrLogin(String sessionId) {
        return loginApiService.cancelQrLogin(sessionId);
    }

    /**
     * Cookie 登录 — 传入已获取的 Cookie 直接登录
     * @param cookieHeader Cookie 字符串
     * @return 登录结果
     */
    public XianyuLoginApiService.LoginResult cookieLogin(String cookieHeader) {
        return loginApiService.cookieLogin(cookieHeader);
    }

    /**
     * 检测当前会话是否已登录
     * @return 登录状态检测结果
     */
    public XianyuLoginApiService.LoginStatusResult checkLoginStatus() {
        return loginApiService.checkLoginStatus();
    }

    /**
     * 检测指定 Cookie 的登录状态
     */
    public XianyuLoginApiService.LoginStatusResult checkLoginStatus(String cookieHeader) {
        return loginApiService.checkLoginStatus(cookieHeader);
    }

    /**
     * 清理过期的二维码登录会话
     */
    public void cleanupQrSessions() {
        loginApiService.cleanupExpiredSessions();
    }

    // ======================== 2. 个人信息 ========================

    /** 获取当前登录用户信息 */
    public JsonNode getUserInfo() {
        return profileApiService.getUserInfo();
    }

    /** 获取登录用户 IM 信息 */
    public JsonNode getLoginUserInfo() {
        return profileApiService.getLoginUserInfo();
    }

    /** 获取用户主页数据 */
    public JsonNode getUserHomePage(String userId) {
        return profileApiService.getUserHomePage(userId);
    }

    /** 获取用户页面导航信息 */
    public JsonNode getUserPageNav() {
        return profileApiService.getUserPageNav();
    }

    /** 获取用户信用分 */
    public JsonNode getUserCredit(String userId) {
        return profileApiService.getUserCredit(userId);
    }

    // ======================== 3. 商品管理 ========================

    /** 搜索商品 */
    public JsonNode searchProducts(String keyword, String page, String pageSize) {
        return productApiService.searchProducts(keyword, page, pageSize);
    }

    /** 获取我的商品列表 */
    public JsonNode getMyProducts(String page, String pageSize) {
        return productApiService.getMyProducts(page, pageSize);
    }

    /** 获取商品详情 */
    public JsonNode getProductDetail(String itemId) {
        return productApiService.getProductDetail(itemId);
    }

    /** 商品上架/下架 */
    public JsonNode updateProductStatus(String itemId, String status) {
        return productApiService.updateProductStatus(itemId, status);
    }

    /** 首页 Feed 流 */
    public JsonNode getHomeFeed() {
        return productApiService.getHomeFeed();
    }

    /** 搜索激活 */
    public JsonNode activateSearch(String keyword) {
        return productApiService.activateSearch(keyword);
    }

    /** 搜索遮罩 */
    public JsonNode getSearchShade() {
        return productApiService.getSearchShade();
    }

    // ======================== 4. 商品发布 ========================

    /** 创建商品 */
    public JsonNode createProduct(String title, String price, String description, String categoryId, String images) {
        return publishApiService.createProduct(title, price, description, categoryId, images);
    }

    /** 创建商品（JSON body 方式）*/
    public JsonNode createProductWithBody(String title, String price, String description) {
        return publishApiService.createProductWithBody(title, price, description);
    }

    // ======================== 5. 消息收发 ========================

    /** 获取会话列表 */
    public JsonNode getSessionList() {
        return messageApiService.getSessionList();
    }

    /** 发送消息 */
    public JsonNode sendMessage(String sessionId, String content, String receiverId) {
        return messageApiService.sendMessage(sessionId, content, receiverId);
    }

    /** 获取消息历史 */
    public JsonNode getMessageHistory(String sessionId, String page) {
        return messageApiService.getMessageHistory(sessionId, page);
    }

    // ======================== 6. 订单管理 ========================

    /** 获取订单列表 */
    public JsonNode getOrderList(String tab, String page) {
        return orderApiService.getOrderList(tab, page);
    }

    /** 获取订单详情 */
    public JsonNode getOrderDetail(String orderId) {
        return orderApiService.getOrderDetail(orderId);
    }

    /** 自动发货 */
    public JsonNode delivery(String orderId, String trackingNo) {
        return orderApiService.delivery(orderId, trackingNo);
    }
}
