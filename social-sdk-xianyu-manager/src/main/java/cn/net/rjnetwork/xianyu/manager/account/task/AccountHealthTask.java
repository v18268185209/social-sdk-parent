package cn.net.rjnetwork.xianyu.manager.account.task;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号健康检查定时任务
 */
@Component
public class AccountHealthTask {

    private static final Logger logger = LoggerFactory.getLogger(AccountHealthTask.class);

    private final AccountMapper accountMapper;

    public AccountHealthTask(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 每5分钟检测一次账号健康状态
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void checkAccountHealth() {
        List<XianyuAccount> accounts = accountMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuAccount>()
                        .eq(XianyuAccount::getStatus, "ACTIVE")
        );

        for (XianyuAccount account : accounts) {
            try {
                // TODO: 调用 XianyuSdk 检测 Cookie 有效性
                // 这里简化处理，实际应通过 SDK 调用 API 验证
                account.setLastLoginAt(LocalDateTime.now());
                accountMapper.updateById(account);
            } catch (Exception e) {
                logger.error("Account health check failed for {}: {}", account.getAccountName(), e.getMessage());
                account.setLastError(e.getMessage());
                account.setStatus("COOKIE_EXPIRED");
                accountMapper.updateById(account);
            }
        }
    }
}
