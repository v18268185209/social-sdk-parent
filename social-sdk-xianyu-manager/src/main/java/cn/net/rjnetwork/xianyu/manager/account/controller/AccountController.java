package cn.net.rjnetwork.xianyu.manager.account.controller;

import cn.net.rjnetwork.xianyu.chrome.model.ChromeProfile;
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
import java.util.Optional;

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
        XianyuAccount account = accountService.login(request);
        // 登录成功后自动启动 Chrome 容器
        accountService.launchChromeContainer(account);
        return ApiResponse.ok(account);
    }

    @PostMapping("/qr-login")
    public ApiResponse<QrLoginResponse> qrLogin(@RequestBody QrLoginRequest request) {
        return ApiResponse.ok(accountService.createQrLoginSession(request));
    }

    @GetMapping("/qr-login/status")
    public ApiResponse<QrLoginResponse> qrLoginStatus(@RequestParam("sessionId") String sessionId) {
        return ApiResponse.ok(accountService.pollQrLoginStatus(sessionId));
    }

    @GetMapping("/{id}/profile")
    public ApiResponse<AccountProfileService.ProfileResult> getProfile(@PathVariable Long id) {
        AccountProfileService.ProfileResult result = profileService.fetchRealTimeProfile(id);
        if (!result.success) {
            return ApiResponse.fail("FETCH_FAILED", result.message);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/profile/sync")
    public ApiResponse<AccountProfileService.ProfileResult> syncProfile(@PathVariable Long id) {
        AccountProfileService.ProfileResult result = profileService.syncProfile(id);
        if (!result.success) {
            return ApiResponse.fail("SYNC_FAILED", result.message);
        }
        return ApiResponse.ok(result);
    }

    // ===== Chrome 容器管理 API =====

    /**
     * 启动指定账号的 Chrome 容器
     */
    @PostMapping("/{id}/chrome/launch")
    @Audit("启动 Chrome 容器")
    public ApiResponse<ChromeProfile> launchChrome(@PathVariable Long id) {
        XianyuAccount account = accountService.getById(id);
        if (account == null) {
            return ApiResponse.fail("NOT_FOUND", "账号不存在");
        }
        boolean launched = accountService.launchChromeContainer(account);
        if (!launched) {
            return ApiResponse.fail("CHROME_NOT_AVAILABLE", "Chrome 容器管理器不可用");
        }
        Optional<ChromeProfile> profile = accountService.getChromeProfile(id);
        return profile.map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("LAUNCH_FAILED", "Chrome 容器启动失败"));
    }

    /**
     * 关闭指定账号的 Chrome 容器
     */
    @PostMapping("/{id}/chrome/stop")
    @Audit("关闭 Chrome 容器")
    public ApiResponse<Boolean> stopChrome(@PathVariable Long id) {
        boolean stopped = accountService.stopChromeContainer(id);
        return ApiResponse.ok(stopped);
    }

    /**
     * 获取指定账号的 Chrome 容器状态
     */
    @GetMapping("/{id}/chrome/status")
    public ApiResponse<ChromeProfile> getChromeStatus(@PathVariable Long id) {
        Optional<ChromeProfile> profile = accountService.getChromeProfile(id);
        return profile.map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("NOT_FOUND", "该账号无活跃的 Chrome 容器"));
    }

    /**
     * 判断 Chrome 容器是否存活
     */
    @GetMapping("/{id}/chrome/alive")
    public ApiResponse<Boolean> isChromeAlive(@PathVariable Long id) {
        return ApiResponse.ok(accountService.isChromeAlive(id));
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
