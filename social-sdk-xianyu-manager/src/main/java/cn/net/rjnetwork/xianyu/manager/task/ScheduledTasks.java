package cn.net.rjnetwork.xianyu.manager.task;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.task.AccountHealthTask;
import cn.net.rjnetwork.xianyu.manager.message.service.ImMessageWatcherService;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductSyncService;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductSyncService.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final AccountMapper accountMapper;
    private final ProductSyncService productSyncService;
    private final MonitorService monitorService;
    private final AccountHealthTask healthTask;
    private final ImMessageWatcherService watcherService;

    public ScheduledTasks(AccountMapper accountMapper, ProductSyncService productSyncService,
                          MonitorService monitorService, AccountHealthTask healthTask,
                          ImMessageWatcherService watcherService) {
        this.accountMapper = accountMapper;
        this.productSyncService = productSyncService;
        this.monitorService = monitorService;
        this.healthTask = healthTask;
        this.watcherService = watcherService;
    }

    @Scheduled(cron = "0 0/30 * * * *")
    public void autoSyncProducts() {
        log.info("[Schedule] auto-sync products start");
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        for (XianyuAccount acc : accounts) {
            try {
                SyncResult r = productSyncService.sync(acc.getId());
                log.info("[Schedule] sync account {}: {}", acc.getId(), r.success ? r.count : r.message);
            } catch (Exception e) {
                log.warn("[Schedule] sync failed account {}: {}", acc.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0/5 * * * *")
    public void runHealthCheck() {
        healthTask.checkAccountHealth();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void hourlyStats() {
        try {
            monitorService.invalidateCache();
            log.info("[Schedule] stats cache refreshed");
        } catch (Exception e) {
            log.warn("[Schedule] stats failed: {}", e.getMessage());
        }
    }

    @EventListener
    public void onNotifyEvent(NotifyEvent event) {
        if ("NEW_MESSAGE".equals(event.getType())) {
            log.info("[Notify] new message: {}", event.getPayload());
        }
    }
}
