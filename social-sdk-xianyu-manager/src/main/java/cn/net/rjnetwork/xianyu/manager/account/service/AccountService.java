package cn.net.rjnetwork.xianyu.manager.account.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginResponse;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.model.XianyuCredentials;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
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

        accountMapper.insert(account);
        return account;
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
        accountMapper.deleteById(id);
    }
}
