package cn.net.rjnetwork.xianyu.manager.account.task;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 账号健康检查定时任务
 */
@Component
public class AccountHealthTask {

    private static final Logger logger = LoggerFactory.getLogger(AccountHealthTask.class);

    private final AccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AccountHealthTask(AccountMapper accountMapper, ApplicationEventPublisher eventPublisher) {
        this.accountMapper = accountMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 检测账号健康状态，由 ScheduledTasks 统一调度。
     */
    public void checkAccountHealth() {
        List<XianyuAccount> accounts = accountMapper.selectList(
                new LambdaQueryWrapper<XianyuAccount>()
                        .in(XianyuAccount::getStatus, "ACTIVE", "OFFLINE")
        );

        for (XianyuAccount account : accounts) {
            String cookie = account.getCookieHeader();
            // 未配置 Cookie：账号无法连接，视为离线
            if (cookie == null || cookie.isBlank()) {
                if (!"OFFLINE".equals(account.getStatus())) {
                    account.setStatus("OFFLINE");
                    account.setLastError("未配置 Cookie，无法连接");
                    accountMapper.updateById(account);
                    publishOffline(account);
                }
                continue;
            }

            try {
                // 通过 SDK 真实校验登录态，区分 Cookie 失效与连接失败
                XianyuLoginApiService loginApi = new XianyuLoginApiService(cookie);
                XianyuLoginApiService.LoginStatusResult r = loginApi.checkLoginStatus(cookie);

                if (r != null && r.loggedIn) {
                    // 健康：恢复在线
                    account.setStatus("ACTIVE");
                    account.setLastLoginAt(LocalDateTime.now());
                    account.setLastError(null);
                    accountMapper.updateById(account);
                    continue;
                }

                // 校验未通过：根据 message 区分鉴权失效与连接失败
                String msg = r == null ? "" : (r.message == null ? "" : r.message);
                boolean loginExpired = msg.toLowerCase().contains("login")
                        || msg.toLowerCase().contains("token")
                        || msg.toLowerCase().contains("empty cookie");

                if (loginExpired) {
                    if (!"COOKIE_EXPIRED".equals(account.getStatus())) {
                        account.setStatus("COOKIE_EXPIRED");
                        account.setLastError(msg);
                        accountMapper.updateById(account);
                        publishCookieExpired(account);
                    }
                } else {
                    // 连接/网络失败 -> 离线
                    if (!"OFFLINE".equals(account.getStatus())) {
                        account.setStatus("OFFLINE");
                        account.setLastError(msg);
                        accountMapper.updateById(account);
                        publishOffline(account);
                    }
                }
            } catch (Exception e) {
                logger.error("Account health check failed for {}: {}", account.getAccountName(), e.getMessage());
                if (!"OFFLINE".equals(account.getStatus())) {
                    account.setStatus("OFFLINE");
                    account.setLastError(e.getMessage());
                    accountMapper.updateById(account);
                    publishOffline(account);
                }
            }
        }
    }

    private void publishCookieExpired(XianyuAccount account) {
        String name = account.getDisplayName() != null ? account.getDisplayName() : account.getAccountName();
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_COOKIE_EXPIRED", account.getId(), name,
                Map.of("accountName", name)));
    }

    private void publishOffline(XianyuAccount account) {
        String name = account.getDisplayName() != null ? account.getDisplayName() : account.getAccountName();
        eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_OFFLINE", account.getId(), name,
                Map.of("accountName", name)));
    }
}
