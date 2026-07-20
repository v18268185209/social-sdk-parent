package cn.net.rjnetwork.xianyu.manager.monitor.task;

import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorTaskRunner;
import cn.net.rjnetwork.xianyu.manager.monitor.service.MonitorTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控任务调度器 — 每分钟检查并执行到期任务
 */
@Component
public class MonitorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MonitorScheduler.class);

    private final MonitorTaskService taskService;
    private final MonitorTaskRunner runner;

    public MonitorScheduler(MonitorTaskService taskService, MonitorTaskRunner runner) {
        this.taskService = taskService;
        this.runner = runner;
    }

    /** 每分钟检查一次 */
    @Scheduled(cron = "0 * * * * ?")
    public void pollAndRun() {
        try {
            List<MonitorTask> dueTasks = taskService.getDueTasks(10);
            for (MonitorTask task : dueTasks) {
                try {
                    logger.info("执行监控任务: {} (关键词: {})", task.getId(), task.getKeyword());
                    runner.executeTask(task);
                } catch (Exception e) {
                    logger.error("任务 {} 执行异常: {}", task.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("监控调度异常: {}", e.getMessage());
        }
    }
}
