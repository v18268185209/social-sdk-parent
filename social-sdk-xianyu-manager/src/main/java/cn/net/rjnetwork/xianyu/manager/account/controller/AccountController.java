package cn.net.rjnetwork.xianyu.manager.account.controller;

import cn.net.rjnetwork.xianyu.manager.account.dto.AccountLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginResponse;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountProfileService;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountProfileService profileService;

    public AccountController(AccountService accountService, AccountProfileService profileService) {
        this.accountService = accountService;
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<List<XianyuAccount>> list() {
        return ApiResponse.ok(accountService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<XianyuAccount> getById(@PathVariable Long id) {
        XianyuAccount account = accountService.getById(id);
        if (account == null) return ApiResponse.fail("NOT_FOUND", "Account not found");
        return ApiResponse.ok(account);
    }

    @PostMapping("/login")
    public ApiResponse<XianyuAccount> login(@RequestBody AccountLoginRequest request) {
        return ApiResponse.ok(accountService.login(request));
    }

    /**
     * 创建二维码登录会话
     */
    @PostMapping("/qr-login")
    public ApiResponse<QrLoginResponse> qrLogin(@RequestBody QrLoginRequest request) {
        return ApiResponse.ok(accountService.createQrLoginSession(request));
    }

    /**
     * 轮询二维码登录状态
     */
    @GetMapping("/qr-login/status")
    public ApiResponse<QrLoginResponse> qrLoginStatus(@RequestParam("sessionId") String sessionId) {
        return ApiResponse.ok(accountService.pollQrLoginStatus(sessionId));
    }

    /**
     * 获取账号实时个人信息（每次调用都实时请求闲鱼 API）
     */
    @GetMapping("/{id}/profile")
    public ApiResponse<AccountProfileService.ProfileResult> getProfile(@PathVariable Long id) {
        AccountProfileService.ProfileResult result = profileService.fetchRealTimeProfile(id);
        if (!result.success) {
            return ApiResponse.fail("FETCH_FAILED", result.message);
        }
        return ApiResponse.ok(result);
    }

    /**
     * 同步个人信息到数据库（获取实时数据并保存）
     */
    @PostMapping("/{id}/profile/sync")
    public ApiResponse<AccountProfileService.ProfileResult> syncProfile(@PathVariable Long id) {
        AccountProfileService.ProfileResult result = profileService.syncProfile(id);
        if (!result.success) {
            return ApiResponse.fail("SYNC_FAILED", result.message);
        }
        return ApiResponse.ok(result);
    }

    @PutMapping("/{id}/status")
    @Audit("更新账号状态")
    public ApiResponse<XianyuAccount> updateStatus(@PathVariable Long id, @RequestBody AccountStatusUpdateRequest request) {
        return ApiResponse.ok(accountService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Audit("删除账号")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        accountService.removeById(id);
        return ApiResponse.ok(null);
    }
}
