package cn.net.rjnetwork.xianyu.manager.account.service;

import cn.net.rjnetwork.xianyu.manager.account.dto.AccountLoginRequest;
import cn.net.rjnetwork.xianyu.manager.account.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 闲鱼账号 Service
 */
@Service
public class AccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 登录账号
     */
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
     * 更新账号状态
     */
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

    /**
     * 获取所有账号
     */
    public List<XianyuAccount> listAll() {
        return accountMapper.selectList(null);
    }

    /**
     * 根据名称获取账号
     */
    public Optional<XianyuAccount> findByName(String accountName) {
        LambdaQueryWrapper<XianyuAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuAccount::getAccountName, accountName);
        XianyuAccount account = accountMapper.selectOne(wrapper);
        return Optional.ofNullable(account);
    }
}
