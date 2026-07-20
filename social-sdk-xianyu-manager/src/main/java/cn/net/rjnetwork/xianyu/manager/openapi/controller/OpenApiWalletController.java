^package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiWalletTransactionVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiWalletVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletTransactionMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWallet;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 钱包域对外接口：钱包按账号维度；列表按绑定白名单过滤，详情/流水做账号作用域校验。
 */
@RestController
@RequestMapping("/openapi/v1/wallets")
public class OpenApiWalletController {

    private final WalletMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final OpenAppService openAppService;

    public OpenApiWalletController(WalletMapper walletMapper,
                                   WalletTransactionMapper walletTransactionMapper,
                                   OpenAppService openAppService) {
        this.walletMapper = walletMapper;
        this.walletTransactionMapper = walletTransactionMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiWalletVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiWalletVO> result = walletMapper.selectList(new LambdaQueryWrapper<XianyuWallet>()).stream()
                .filter(w -> bound.isEmpty() || bound.contains(w.getAccountId()))
                .filter(w -> accountId == null || w.getAccountId().equals(accountId))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{accountId}")
    public OpenApiResponse<OpenApiWalletVO> getByAccount(@PathVariable Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        XianyuWallet wallet = walletMapper.selectOne(
                new LambdaQueryWrapper<XianyuWallet>().eq(XianyuWallet::getAccountId, accountId));
        if (wallet == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "钱包不存在");
        }
        return OpenApiResponse.ok(toVo(wallet));
    }

    @GetMapping("/{accountId}/transactions")
    public OpenApiResponse<List<OpenApiWalletTransactionVO>> transactions(@PathVariable Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        List<OpenApiWalletTransactionVO> result = walletTransactionMapper.selectList(
                        new LambdaQueryWrapper<XianyuWalletTransaction>().eq(XianyuWalletTransaction::getAccountId, accountId))
                .stream()
                .map(this::toTxVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    private OpenApiWalletVO toVo(XianyuWallet w) {
        OpenApiWalletVO vo = new OpenApiWalletVO();
        vo.setId(w.getId());
        vo.setAccountId(w.getAccountId());
        vo.setBalance(w.getBalance());
        vo.setFrozenAmount(w.getFrozenAmount());
        vo.setAvailableBalance(w.getAvailableBalance());
        vo.setTotalAssets(w.getTotalAssets());
        vo.setWithdrawableAmount(w.getWithdrawableAmount());
        vo.setAlipayAccount(w.getAlipayAccount());
        vo.setAlipayRealName(w.getAlipayRealName());
        vo.setBankCard(w.getBankCard());
        vo.setCreatedAt(w.getCreatedAt());
        vo.setUpdatedAt(w.getUpdatedAt());
        return vo;
    }

    private OpenApiWalletTransactionVO toTxVo(XianyuWalletTransaction t) {
        OpenApiWalletTransactionVO vo = new OpenApiWalletTransactionVO();
        vo.setId(t.getId());
        vo.setAccountId(t.getAccountId());
        vo.setTransactionId(t.getTransactionId());
        vo.setType(t.getType());
        vo.setBizType(t.getBizType());
        vo.setAmount(t.getAmount());
        vo.setBalanceAfter(t.getBalanceAfter());
        vo.setDescription(t.getDescription());
        vo.setStatus(t.getStatus());
        vo.setTradeNo(t.getTradeNo());
        vo.setTransactionTime(t.getTransactionTime());
        vo.setCreatedAt(t.getCreatedAt());
        return vo;
    }
}
