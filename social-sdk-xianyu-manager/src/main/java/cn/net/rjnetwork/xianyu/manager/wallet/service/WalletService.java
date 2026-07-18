package cn.net.rjnetwork.xianyu.manager.wallet.service;

import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletTransactionMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWallet;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    private final WalletMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;

    public WalletService(WalletMapper walletMapper, WalletTransactionMapper transactionMapper) {
        this.walletMapper = walletMapper;
        this.transactionMapper = transactionMapper;
    }

    public XianyuWallet getOrCreate(Long accountId) {
        LambdaQueryWrapper<XianyuWallet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuWallet::getAccountId, accountId);
        XianyuWallet wallet = walletMapper.selectOne(wrapper);
        if (wallet == null) {
            wallet = new XianyuWallet();
            wallet.setAccountId(accountId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenAmount(BigDecimal.ZERO);
            wallet.setCreatedAt(LocalDateTime.now());
            wallet.setUpdatedAt(LocalDateTime.now());
            walletMapper.insert(wallet);
        }
        return wallet;
    }

    public Page<XianyuWalletTransaction> listTransactions(Long accountId, int pageNum, int pageSize, String type) {
        Page<XianyuWalletTransaction> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuWalletTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuWalletTransaction::getAccountId, accountId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(XianyuWalletTransaction::getType, type);
        }
        wrapper.orderByDesc(XianyuWalletTransaction::getTransactionTime);
        transactionMapper.selectPage(page, wrapper);
        return page;
    }

    public List<XianyuWalletTransaction> listRecentTransactions(Long accountId, int limit) {
        LambdaQueryWrapper<XianyuWalletTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuWalletTransaction::getAccountId, accountId)
               .orderByDesc(XianyuWalletTransaction::getTransactionTime)
               .last("LIMIT " + limit);
        return transactionMapper.selectList(wrapper);
    }

    public void recordTransaction(XianyuWalletTransaction transaction) {
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }
}
