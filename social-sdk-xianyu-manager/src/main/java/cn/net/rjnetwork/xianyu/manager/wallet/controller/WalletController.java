package cn.net.rjnetwork.xianyu.manager.wallet.controller;

import cn.net.rjnetwork.xianyu.api.XianyuWalletApiService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWallet;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
import cn.net.rjnetwork.xianyu.manager.wallet.service.WalletService;
import cn.net.rjnetwork.xianyu.manager.wallet.service.WalletSyncService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final WalletSyncService walletSyncService;

    public WalletController(WalletService walletService, WalletSyncService walletSyncService) {
        this.walletService = walletService;
        this.walletSyncService = walletSyncService;
    }

    @GetMapping("/{accountId}")
    public ApiResponse<XianyuWallet> get(@PathVariable Long accountId) {
        return ApiResponse.ok(walletService.getOrCreate(accountId));
    }

    @GetMapping("/{accountId}/transactions")
    public ApiResponse<Page<XianyuWalletTransaction>> transactions(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(walletService.listTransactions(accountId, page, size, type));
    }

    @GetMapping("/{accountId}/recent")
    public ApiResponse<List<XianyuWalletTransaction>> recent(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(walletService.listRecentTransactions(accountId, limit));
    }

    /** 从闲鱼 API 拉取真实钱包数据并落库 */
    @PostMapping("/{accountId}/sync")
    public ApiResponse<WalletSyncService.SyncResult> sync(@PathVariable Long accountId) {
        WalletSyncService.SyncResult result = walletSyncService.syncWallet(accountId);
        if (!result.success) {
            return ApiResponse.fail("SYNC_FAILED", result.message);
        }
        return ApiResponse.ok(result);
    }

    /** 调试：返回余额/账单/绑定账户三个 API 的原始返回，用于确认真实结构 */
    @GetMapping("/{accountId}/debug")
    public ApiResponse<Map<String, Object>> debug(@PathVariable Long accountId) {
        return ApiResponse.ok(walletSyncService.debugRawResponse(accountId));
    }

    /** 探测：用任意 (api, version) 直接打 MTOP，返回原始响应（用于验证候选接口名，无需重新编译） */
    @PostMapping("/{accountId}/probe")
    public ApiResponse<JsonNode> probe(@PathVariable Long accountId,
                                       @RequestParam String api,
                                       @RequestParam(required = false, defaultValue = "1.0") String version) {
        JsonNode resp = walletSyncService.probe(accountId, api, version);
        if (resp == null) return ApiResponse.fail("PROBE_FAILED", "探测失败（账号不存在或无 Cookie）");
        return ApiResponse.ok(resp);
    }

    /** 查看当前生效的钱包接口名（来自 application.yml，可被真实名覆盖） */
    @GetMapping("/api-names")
    public ApiResponse<java.util.Map<String, String>> apiNames() {
        java.util.Map<String, String> names = new java.util.HashMap<>();
        names.put("balance", XianyuWalletApiService.API_BALANCE);
        names.put("bill", XianyuWalletApiService.API_BILL);
        names.put("bankcard", XianyuWalletApiService.API_BANKCARD);
        names.put("withdraw", XianyuWalletApiService.API_WITHDRAW);
        return ApiResponse.ok(names);
    }
}
