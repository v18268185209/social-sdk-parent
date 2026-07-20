package cn.net.rjnetwork.xianyu.manager.monitor.service;

import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorTaskMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控任务管理 Service
 */
@Service
public class MonitorTaskService {

    private final MonitorTaskMapper mapper;

    public MonitorTaskService(MonitorTaskMapper mapper) {
        this.mapper = mapper;
    }

    public List<MonitorTask> list(int page, int size, Long accountId) {
        LambdaQueryWrapper<MonitorTask> w = new LambdaQueryWrapper<MonitorTask>()
                .eq(MonitorTask::getDeleted, 0)
                .orderByDesc(MonitorTask::getUpdatedAt);
        if (accountId != null) {
            w.eq(MonitorTask::getAccountId, accountId);
        }
        w.last("LIMIT " + size + " OFFSET " + ((long) page * size));
        return mapper.selectList(w);
    }

    public List<MonitorTask> listAllActive() {
        return mapper.selectList(new LambdaQueryWrapper<MonitorTask>()
                .eq(MonitorTask::getStatus, "ACTIVE")
                .eq(MonitorTask::getDeleted, 0));
    }

    public MonitorTask get(Long id) {
        return mapper.selectById(id);
    }

    public void save(MonitorTask task) {
        if (task.getId() == null) {
            task.setStatus("ACTIVE");
            task.setRunCount(0);
            task.setConsecutiveFailures(0);
            task.setCircuitOpen(false);
            task.setDeleted(0);
            task.setNotifyOnMatch(true);
            if (task.getIntervalMinutes() == null) task.setIntervalMinutes(30);
            task.setNextRunAt(LocalDateTime.now());
            mapper.insert(task);
        } else {
            mapper.updateById(task);
        }
    }

    public void pause(Long id) {
        MonitorTask task = mapper.selectById(id);
        if (task != null) {
            task.setStatus("PAUSED");
            mapper.updateById(task);
        }
    }

    public void resume(Long id) {
        MonitorTask task = mapper.selectById(id);
        if (task != null) {
            task.setStatus("ACTIVE");
            task.setNextRunAt(LocalDateTime.now());
            mapper.updateById(task);
        }
    }

    public void delete(Long id) {
        MonitorTask task = mapper.selectById(id);
        if (task != null) {
            task.setDeleted(1);
            mapper.updateById(task);
        }
    }

    public List<MonitorTask> getDueTasks(int limit) {
        return mapper.selectDueTasks(LocalDateTime.now(), limit);
    }
}
