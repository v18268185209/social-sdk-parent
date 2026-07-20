package cn.net.rjnetwork.xianyu.manager.task;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.task.AccountHealthTask;
import cn.net.rjnetwork.xianyu.manager.message.service.ImMessageWatcherService;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorService;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductSyncService;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductSyncService.SyncResult;
import cn.net.rjnetwork.xianyu.manager.virtual.service.VirtualShipService;
import cn.net.rjnetwork.xianyu.manager.order.service.OrderSyncService;
import cn.net.rjnetwork.xianyu.manager.order.service.OrderSyncService.SyncResult as OrderSyncResult;
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
    private final VirtualShipService virtualShipService;
    private final OrderSyncService orderSyncService;

    public ScheduledTasks(AccountMapper accountMapper, ProductSyncService productSyncService,
                          MonitorService monitorService, AccountHealthTask healthTask,
                          ImMessageWatcherService watcherService,
                          VirtualShipService virtualShipService,
                          OrderSyncService orderSyncService) {
        this.accountMapper = accountMapper;
        this.productSyncService = productSyncService;
        this.monitorService = monitorService;
        this.healthTask = healthTask;
        this.watcherService = watcherService;
        this.virtualShipService = virtualShipService;
        this.orderSyncService = orderSyncService;
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

    // ======================== 虚拟发货定时链路 ========================

    /** 每分钟扫描待发货的虚拟订单，调 VirtualShipService.scanAndShip 自动发货 */
    @Scheduled(cron = "0 * * * * *")
    public void autoScanVirtualShip() {
        try {
            virtualShipService.scanAndShip();
        } catch (Exception e) {
            log.warn("[Schedule] virtual scanAndShip failed: {}", e.getMessage());
        }
    }

    /** 每 5 分钟重试 FAILED 的发货任务（最多 retry_count 上限由 service 控制） */
    @Scheduled(cron = "0 0/5 * * * *")
    public void retryFailedShipTasks() {
        try {
            virtualShipService.retryFailedShipTasks();
        } catch (Exception e) {
            log.warn("[Schedule] virtual retryFailed failed: {}", e.getMessage());
        }
    }

    /** 每天凌晨 3 点扫描超期未确认收货的订单，自动确认（auto_confirm_days 由配置控制） */
    @Scheduled(cron = "0 0 3 * * *")
    public void autoConfirmReceipt() {
        try {
            virtualShipService.autoConfirmReceipt();
            log.info("[Schedule] virtual autoConfirmReceipt done");
        } catch (Exception e) {
            log.warn("[Schedule] virtual autoConfirmReceipt failed: {}", e.getMessage());
        }
    }

    // ======================== 订单同步定时链路 ========================

    /** 每 2 分钟拉一次闲鱼订单（BOUGHT+SOLD），同步入库并触发 NEW_ORDER 通知 */
    @Scheduled(cron = "0 0/2 * * * *")
    public void autoSyncOrders() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        for (XianyuAccount acc : accounts) {
            try {
                OrderSyncResult r = orderSyncService.syncOrders(acc.getId());
                if (r.success) {
                    log.info("[Schedule] sync orders account {}: bought={}, sold={}",
                            acc.getId(), r.boughtCount, r.soldCount);
                } else {
                    log.warn("[Schedule] sync orders account {} failed: {}", acc.getId(), r.message);
                }
            } catch (Exception e) {
                log.warn("[Schedule] sync orders account {} error: {}", acc.getId(), e.getMessage());
            }
        }
    }

    @EventListener
    public void onNotifyEvent(NotifyEvent event) {
        if ("NEW_MESSAGE".equals(event.getType())) {
            log.info("[Notify] new message: {}", event.getPayload());
        }
    }
}
