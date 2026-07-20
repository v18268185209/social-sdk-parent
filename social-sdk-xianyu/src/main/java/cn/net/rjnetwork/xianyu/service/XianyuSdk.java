package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.model.XianyuCredentials;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼 SDK 门面类（纯 HTTP/MTOP 版本）
 *
 * <p>不再依赖 CDP/浏览器，所有能力通过 MTOP API 直接调用实现。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 1. 创建 SDK 实例
 * XianyuSdk sdk = new XianyuSdk();
 *
 * // 2. 获取账号
 * XianyuSdk.XianyuAccount acc = sdk.account("default");
 *
 * // 3. 二维码登录
 * XianyuApiFacade.QrLoginResult qr = acc.api().createQrLoginSession();
 * System.out.println(qr.qrCodeDataUrl);
 * XianyuApiFacade.QrLoginResult login = acc.api().waitForLogin(qr.sessionId);
 * acc.refreshApiFacadeWithCookie(login.cookieHeader);
 *
 * // 4. Cookie 登录（如果有现成 Cookie）
 * acc.api().cookieLogin("your_cookie_string");
 *
 * // 5. 使用 API
 * JsonNode info = acc.api().getUserInfo();
 * JsonNode products = acc.api().getMyProducts("1", "20");
 * }</pre>
 *
 * <p>线程安全；各账号 api 互不干扰。</p>
 */
public class XianyuSdk {

    /**
     * 多账号隔离：每个 accountKey 对应独立的 Cookie 和 API 实例
     */
    private final Map<String, XianyuAccount> accounts = new LinkedHashMap<>();

    /**
     * 默认账号键
     */
    private static final String DEFAULT_ACCOUNT = "default";

    // ==================== 构造 ====================

    public XianyuSdk() {
        // 默认创建空账号，首次使用时 lazy-init
    }

    /**
     * 预置 Cookie 创建 SDK（单账号）
     *
     * @param cookie Cookie 字符串
     */
    public XianyuSdk(String cookie) {
        XianyuAccount acc = new XianyuAccount(DEFAULT_ACCOUNT);
        if (cookie != null && !cookie.isEmpty()) {
            acc.api().updateCookie(cookie);
        }
        accounts.put(DEFAULT_ACCOUNT, acc);
    }

    /**
     * 使用凭证创建 SDK
     *
     * @param credentials 凭证（可含 Cookie）
     */
    public XianyuSdk(XianyuCredentials credentials) {
        if (credentials != null && credentials.getCookieHeader() != null) {
            XianyuAccount acc = new XianyuAccount(DEFAULT_ACCOUNT);
            acc.api().updateCookie(credentials.getCookieHeader());
            accounts.put(DEFAULT_ACCOUNT, acc);
        }
    }

    // ==================== 账号管理 ====================

    /**
     * 获取或创建账号
     *
     * @param accountKey 账号标识
     * @return 账号实例
     */
    public XianyuAccount account(String accountKey) {
        synchronized (accounts) {
            return accounts.computeIfAbsent(accountKey, k -> new XianyuAccount(k));
        }
    }

    /**
     * 获取默认账号
     */
    public XianyuAccount defaultAccount() {
        return account(DEFAULT_ACCOUNT);
    }

    /**
     * 删除账号（清除 Cookie 和 API 实例）
     */
    public void removeAccount(String accountKey) {
        synchronized (accounts) {
            accounts.remove(accountKey);
        }
    }

    /**
     * 获取所有账号键
     */
    public java.util.Set<String> accountKeys() {
        synchronized (accounts) {
            return accounts.keySet();
        }
    }

    /**
     * 设置默认账号的 Cookie
     */
    public void setDefaultCookie(String cookie) {
        account(DEFAULT_ACCOUNT).api().updateCookie(cookie);
    }

    /**
     * 获取默认账号的 Cookie
     */
    public String getDefaultCookie() {
        return account(DEFAULT_ACCOUNT).api().getCookie();
    }

    // ==================== 便捷方法 ====================

    /**
     * 检测默认账号登录状态
     */
    public boolean isLoggedIn() {
        return account(DEFAULT_ACCOUNT).api().checkLoginStatus().loggedIn;
    }

    /**
     * 获取默认账号的用户信息
     */
    public JsonNode getUserInfo() {
        return account(DEFAULT_ACCOUNT).api().getUserInfo();
    }

    /**
     * 搜索商品
     */
    public JsonNode searchProducts(String keyword, String page, String pageSize) {
        return account(DEFAULT_ACCOUNT).api().searchProducts(keyword, page, pageSize);
    }

    /**
     * 获取我的商品
     */
    public JsonNode getMyProducts(String page, String pageSize) {
        return account(DEFAULT_ACCOUNT).api().getMyProducts(page, pageSize);
    }

    /**
     * 获取订单列表
     */
    public JsonNode getOrderList(String tab, String page) {
        return account(DEFAULT_ACCOUNT).api().getOrderList(tab, page);
    }

    /**
     * 自动发货
     */
    public JsonNode delivery(String orderId, String trackingNo) {
        return account(DEFAULT_ACCOUNT).api().delivery(orderId, trackingNo);
    }

    /**
     * 发送消息（CDP 校验 2026-07-21：actualReceivers 需 self + peer 两个 userId）
     */
    public JsonNode sendMessage(String sessionId, String content, String selfUserId, String peerUserId) throws Exception {
        return account(DEFAULT_ACCOUNT).api().sendMessage(sessionId, content, selfUserId, peerUserId);
    }

    /**
     * 创建商品
     */
    public JsonNode createProduct(String title, String price, String description) {
        return account(DEFAULT_ACCOUNT).api().createProduct(title, price, description, null, null);
    }

    /**
     * 上架商品
     */
    public JsonNode shelfOn(String itemId) {
        return account(DEFAULT_ACCOUNT).api().shelfOn(itemId);
    }

    /**
     * 下架商品
     */
    public JsonNode shelfOff(String itemId) {
        return account(DEFAULT_ACCOUNT).api().shelfOff(itemId);
    }

    /**
     * 编辑商品
     */
    public JsonNode editProduct(String itemId, String title, String description, String price) {
        return account(DEFAULT_ACCOUNT).api().editProduct(itemId, title, description, price, null, null, null);
    }

    /**
     * 获取草稿列表
     */
    public JsonNode getDraftList(String page, String pageSize) {
        return account(DEFAULT_ACCOUNT).api().getDraftList(page, pageSize);
    }

    /**
     * 保存草稿
     */
    public JsonNode saveDraft(String title, String description, String price, String categoryId) {
        return account(DEFAULT_ACCOUNT).api().saveDraft(title, description, price, categoryId, null, null, "0");
    }

    /**
     * 获取余额
     */
    public JsonNode getBalance() {
        return account(DEFAULT_ACCOUNT).api().getBalance();
    }

    /**
     * 获取收藏列表
     */
    public JsonNode getMyCollectList(String page, String pageSize) {
        return account(DEFAULT_ACCOUNT).api().getMyCollectList(page, pageSize);
    }

    /**
     * 收藏商品
     */
    public JsonNode collectItem(String itemId) {
        return account(DEFAULT_ACCOUNT).api().collectItem(itemId);
    }

    /**
     * 取消收藏
     */
    public JsonNode uncollectItem(String itemId) {
        return account(DEFAULT_ACCOUNT).api().uncollectItem(itemId);
    }

    // ==================== 内部类：账号 ====================

    /**
     * 账号实例 — 每个账号独立管理 Cookie 和 API 门面
     */
    public static class XianyuAccount {
        private final String key;
        private volatile XianyuApiFacade apiFacade;

        XianyuAccount(String key) {
            this.key = key;
            this.apiFacade = new XianyuApiFacade("");
        }

        /** 账号标识 */
        public String getKey() { return key; }

        /** 获取 API 门面（懒加载，首次调用时初始化）*/
        public XianyuApiFacade api() {
            return apiFacade;
        }

        /** 用新 Cookie 刷新 API 门面 */
        public void refreshApiFacadeWithCookie(String newCookie) {
            apiFacade.updateCookie(newCookie);
        }

        /** 获取当前 Cookie */
        public String getCookie() {
            return apiFacade.getCookie();
        }

        /** 设置 Cookie */
        public void setCookie(String cookie) {
            apiFacade.updateCookie(cookie);
        }
    }
}
