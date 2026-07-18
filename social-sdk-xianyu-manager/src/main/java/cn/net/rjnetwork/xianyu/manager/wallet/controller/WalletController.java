package cn.net.rjnetwork.xianyu.manager.wallet.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWallet;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
import cn.net.rjnetwork.xianyu.manager.wallet.service.WalletService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
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
}
