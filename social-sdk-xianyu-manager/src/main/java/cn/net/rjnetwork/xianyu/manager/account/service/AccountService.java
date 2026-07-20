package cn.net.rjnetwork.xianyu.manager.account.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProfileApiService;
import cn.net.rjnetwork.xianyu.chrome.core.ChromeProfileManager;
import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginResponse;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final Map<String, XianyuLoginApiService> qrLoginServices = new ConcurrentHashMap<>();
    /** 缓存每个二维码会话对应的创建请求（含 accountName / remark）*/
    private final Map<String, QrLoginRequest> qrLoginRequests = new ConcurrentHashMap<>();

    /** Chrome 容器管理器（可选，非 Chrome 环境为 null） */
    private ChromeProfileManager chromeProfileManager;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** Spring 自动注入 ChromeProfileManager（如果容器中存在）。 */
    @Autowired(required = false)
    public void setChromeProfileManager(ChromeProfileManager chromeProfileManager) {
        this.chromeProfileManager = chromeProfileManager;
    }

    @Transactional
    public XianyuAccount login(AccountLoginRequest request) {
        String cookie = request.getCookieHeader();
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("Cookie is required");
        }

        XianyuAccount account = new XianyuAccount();
        account.setAccountName(request.getAccountName());
        account.setCookieHeader(cookie);
        account.setStatus("ACTIVE");
        account.setRemark(request.getRemark());
        account.setLastLoginAt(LocalDateTime.now());
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        accountMapper.insert(account);
        return account;
    }

    /**
     * 创建二维码登录会话
     */
    public QrLoginResponse createQrLoginSession(QrLoginRequest request) {
        String accountName = request.getAccountName();
        if (accountName == null || accountName.isBlank()) {
            QrLoginResponse resp = new QrLoginResponse();
            resp.setSuccess(false);
            resp.setMessage("Account name is required");
            return resp;
        }

        // 创建临时的 SDK 登录服务（使用任意 cookie 即可，仅用于初始化 httpClient）
        XianyuLoginApiService loginService = new XianyuLoginApiService("");
        XianyuLoginApiService.QrLoginResult sdkResult = loginService.createQrLoginSession();

        QrLoginResponse resp = new QrLoginResponse();
        resp.setSuccess(sdkResult.success);
        resp.setSessionId(sdkResult.sessionId);
        resp.setStatus(sdkResult.status);
        resp.setQrCodeDataUrl(sdkResult.qrCodeDataUrl);
        resp.setMessage(sdkResult.message);

        // 修复：只要有 sessionId 和 qrCodeDataUrl 就说明创建成功，不管 status 是 WAITING 还是其他
        if (sdkResult.sessionId != null && sdkResult.qrCodeDataUrl != null) {
            qrLoginServices.put(sdkResult.sessionId, loginService);
            qrLoginRequests.put(sdkResult.sessionId, request);
            System.err.println("[ACCOUNT-SERVICE] Session stored in map: " + sdkResult.sessionId);
        } else {
            System.err.println("[ACCOUNT-SERVICE] Failed to store session: sessionId=" + sdkResult.sessionId + ", qrCodeDataUrl=" + sdkResult.qrCodeDataUrl);
        }

        return resp;
    }

    /**
     * 轮询二维码登录状态
     */
    public QrLoginResponse pollQrLoginStatus(String sessionId) {
        System.err.println("[ACCOUNT-SERVICE] pollQrLoginStatus called, sessionId=" + sessionId);
        System.err.println("[ACCOUNT-SERVICE] Current sessions in map: " + qrLoginServices.keySet());
        System.err.println("[ACCOUNT-SERVICE] Map size: " + qrLoginServices.size());

        XianyuLoginApiService loginService = qrLoginServices.get(sessionId);
        if (loginService == null) {
            System.err.println("[ACCOUNT-SERVICE] Session NOT FOUND for sessionId: " + sessionId);
            QrLoginResponse resp = new QrLoginResponse();
            resp.setSuccess(false);
            resp.setStatus("NOT_FOUND");
            resp.setMessage("QR login session not found");
            return resp;
        }

        System.err.println("[ACCOUNT-SERVICE] Session FOUND, calling SDK pollQrStatus...");
        XianyuLoginApiService.QrLoginResult sdkResult = loginService.pollQrStatus(sessionId);

        QrLoginResponse resp = new QrLoginResponse();
        resp.setSuccess(sdkResult.success);
        resp.setSessionId(sdkResult.sessionId);
        resp.setStatus(sdkResult.status);
        resp.setQrCodeDataUrl(sdkResult.qrCodeDataUrl);
        resp.setMessage(sdkResult.message);

        // 登录成功：保存 Cookie 到数据库
        if ("SUCCESS".equals(sdkResult.status) && sdkResult.cookieHeader != null) {
            QrLoginRequest createReq = qrLoginRequests.get(sessionId);
            XianyuAccount account = saveAccountFromCookie(sdkResult.cookieHeader, sdkResult.unb, createReq);
            if (account != null) {
                resp.setAccount(convertToAccountInfo(account));
            }
            // 清理会话
            qrLoginServices.remove(sessionId);
            qrLoginRequests.remove(sessionId);
        } else if ("EXPIRED".equals(sdkResult.status) || "CANCELLED".equals(sdkResult.status)
                || "ERROR".equals(sdkResult.status)) {
            qrLoginServices.remove(sessionId);
            qrLoginRequests.remove(sessionId);
        }

        return resp;
    }

    /**
     * 根据 Cookie 保存账号
     */
    private XianyuAccount saveAccountFromCookie(String cookieHeader, String unb, QrLoginRequest createReq) {
        // 通过 SDK 获取用户信息
        XianyuLoginApiService tempService = new XianyuLoginApiService(cookieHeader);
        XianyuLoginApiService.LoginStatusResult statusResult = tempService.checkLoginStatus(cookieHeader);

        XianyuAccount account = new XianyuAccount();
        // account_name 在数据库中是 NOT NULL，必须赋值
        String accountName = createReq != null && createReq.getAccountName() != null && !createReq.getAccountName().isBlank()
                ? createReq.getAccountName()
                : (statusResult.nickname != null && !statusResult.nickname.isBlank()
                    ? statusResult.nickname
                    : (unb != null ? "unb_" + unb : "xianyu_" + System.currentTimeMillis()));
        account.setAccountName(accountName);
        account.setUserId(statusResult.userId);
        account.setDisplayName(statusResult.nickname);
        account.setCookieHeader(cookieHeader);
        account.setStatus("ACTIVE");
        account.setRemark(createReq != null ? createReq.getRemark() : null);
        account.setLastLoginAt(LocalDateTime.now());
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        // 获取更详细的个人信息
        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(cookieHeader);
            XianyuProfileApiService profileApi = new XianyuProfileApiService(mtopClient);

            JsonNode navData = profileApi.getUserPageNav();
            if (navData != null && navData.has("data")) {
                JsonNode data = navData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        if (account.getDisplayName() == null) {
                            account.setDisplayName(getText(base, "displayName"));
                        }
                        account.setAvatar(getText(base, "avatar"));
                        account.setPurchaseCount(getInt(base, "purchaseCount"));
                        account.setSoldCount(getInt(base, "soldCount"));
                        account.setFollowers(getInt(base, "followers"));
                        account.setFollowing(getInt(base, "following"));
                        account.setCollectionCount(getInt(base, "collectionCount"));
                    }
                }
            }

            JsonNode headData = profileApi.getUserPageHead(true);
            if (headData != null && headData.has("data")) {
                JsonNode data = headData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        account.setIntroduction(getText(base, "introduction"));
                        account.setIpLocation(getText(base, "ipLocation"));
                    }
                    if (module.has("shop")) {
                        JsonNode shop = module.get("shop");
                        account.setShopLevel(getText(shop, "level"));
                        account.setCreditScore(getInt(shop, "score"));
                        account.setReviewNum(getInt(shop, "reviewNum"));
                    }
                    if (module.has("tabs")) {
                        JsonNode tabs = module.get("tabs");
                        if (tabs.has("item")) {
                            account.setOnSaleCount(getInt(tabs.get("item"), "number"));
                        }
                    }
                    if (module.has("social")) {
                        JsonNode social = module.get("social");
                        if (account.getFollowers() == null) {
                            account.setFollowers(getInt(social, "followers"));
                        }
                        if (account.getFollowing() == null) {
                            account.setFollowing(getInt(social, "following"));
                        }
                    }
                }
            }

            account.setProfileSyncedAt(LocalDateTime.now());
        } catch (Exception e) {
            // 获取 profile 失败不影响登录，仅记录错误
            System.err.println("[ACCOUNT-SERVICE] Failed to fetch profile after login: " + e.getMessage());
        }

        accountMapper.insert(account);

        // Chrome 容器：为账号启动独占 Chrome 容器
        launchChromeContainer(account);

        return account;
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asText();
    }

    private Integer getInt(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asInt();
    }

    private QrLoginResponse.AccountInfo convertToAccountInfo(XianyuAccount account) {
        QrLoginResponse.AccountInfo info = new QrLoginResponse.AccountInfo();
        info.setId(account.getId());
        info.setAccountName(account.getAccountName());
        info.setUserId(account.getUserId());
        info.setDisplayName(account.getDisplayName());
        info.setStatus(account.getStatus());
        return info;
    }

    /**
     * 清理过期的二维码会话（定时任务调用）
     */
    public void cleanupExpiredQrSessions() {
        Iterator<Map.Entry<String, XianyuLoginApiService>> it = qrLoginServices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, XianyuLoginApiService> entry = it.next();
            try {
                entry.getValue().cleanupExpiredSessions();
            } catch (Exception ignore) {
                it.remove();
            }
        }
    }

    @Transactional
    public XianyuAccount updateStatus(Long id, AccountStatusUpdateRequest request) {
        XianyuAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }

        account.setStatus(request.getStatus());
        account.setRemark(request.getRemark());
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(account);

        return account;
    }

    public List<XianyuAccount> listAll() {
        return accountMapper.selectList(null);
    }

    public Optional<XianyuAccount> findByName(String accountName) {
        LambdaQueryWrapper<XianyuAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuAccount::getAccountName, accountName);
        XianyuAccount account = accountMapper.selectOne(wrapper);
        return Optional.ofNullable(account);
    }

    public XianyuAccount getById(Long id) {
        return accountMapper.selectById(id);
    }

    @Transactional
    public void removeById(Long id) {
        // 关闭 Chrome 容器（如果存在）
        stopChromeContainer(id);
        accountMapper.deleteById(id);
    }

    // ==================== Chrome 容器生命周期集成 ====================

    /**
     * 为账号启动独立的 Chrome 容器。
     * ChromeProfileManager 不可用时（如非 Chrome 环境）静默跳过。
     */
    public boolean launchChromeContainer(XianyuAccount account) {
        if (chromeProfileManager == null) {
            return false;
        }
        try {
            ChromeProfile profile = chromeProfileManager.launchAccount(account.getId(), account.getAccountName());

            // 将容器信息回填到数据库
            account.setChromeProfilePath(profile.getProfileDir());
            account.setCdpPort(profile.getCdpPort());
            account.setProxyUrl(profile.getProxyUrl());
            account.setChromeSeed(profile.getSeed());
            account.setChromeStatus(profile.getStatus().name());
            account.setChromeCrashCount(0);
            account.setChromeLaunchedAt(profile.getLaunchedAt());
            account.setUpdatedAt(LocalDateTime.now());
            accountMapper.updateById(account);
            return true;
        } catch (Exception e) {
            System.err.println("[ACCOUNT-SERVICE] 启动 Chrome 容器失败, accountId=" + id + ", err=" + e.getMessage());
            // 不阻断登录，仅记录错误
            return false;
        }
    }

    /**
     * 关闭账号的 Chrome 容器。
     */
    public boolean stopChromeContainer(long accountId) {
        if (chromeProfileManager == null) {
            return false;
        }
        try {
            chromeProfileManager.stopAccount(accountId);
            return true;
        } catch (Exception e) {
            System.err.println("[ACCOUNT-SERVICE] 关闭 Chrome 容器失败, accountId=" + accountId + ", err=" + e.getMessage());
            return false;
        }
    }

    /**
     * 获取账号的 Chrome 容器状态。
     */
    public Optional<ChromeProfile> getChromeProfile(long accountId) {
        if (chromeProfileManager == null) {
            return Optional.empty();
        }
        return chromeProfileManager.getProfile(accountId);
    }

    /**
     * 判断账号是否有存活的 Chrome 容器。
     */
    public boolean isChromeAlive(long accountId) {
        if (chromeProfileManager == null) {
            return false;
        }
        return chromeProfileManager.isAlive(accountId);
    }

    /**
     * 获取 ChromeProfileManager 实例（用于外部直接调用）。
     */
    public ChromeProfileManager getChromeProfileManager() {
        return chromeProfileManager;
    }
}
