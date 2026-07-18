package cn.net.rjnetwork.xianyu.manager.account.controller;

import cn.net.rjnetwork.xianyu.manager.account.dto.AccountLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.QrLoginResponse;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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

    @PutMapping("/{id}/status")
    public ApiResponse<XianyuAccount> updateStatus(@PathVariable Long id, @RequestBody AccountStatusUpdateRequest request) {
        return ApiResponse.ok(accountService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        accountService.removeById(id);
        return ApiResponse.ok(null);
    }
}
