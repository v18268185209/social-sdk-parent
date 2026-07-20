package cn.net.rjnetwork.xianyu.manager.monitor.service;

import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorResultMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorTaskMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorResult;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MonitorResultService {

    private final MonitorResultMapper resultMapper;
    private final MonitorTaskMapper taskMapper;

    public MonitorResultService(MonitorResultMapper resultMapper, MonitorTaskMapper taskMapper) {
        this.resultMapper = resultMapper;
        this.taskMapper = taskMapper;
    }

    public List<MonitorResult> getRecent(Long taskId, int limit) {
        return resultMapper.selectRecent(taskId, limit);
    }

    public Map<String, Object> getStats(Long taskId) {
        LambdaQueryWrapper<MonitorResult> w = new LambdaQueryWrapper<MonitorResult>()
                .eq(MonitorResult::getDeleted, 0);
        if (taskId != null) {
            w.eq(MonitorResult::getTaskId, taskId);
        }
        List<MonitorResult> results = resultMapper.selectList(w);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalResults", results.size());

        // 按任务分组
        Map<Long, Integer> byTask = new HashMap<>();
        double avgAiScore = 0;
        int scored = 0;
        for (MonitorResult r : results) {
            byTask.merge(r.getTaskId(), 1, Integer::sum);
            if (r.getAiScore() != null) {
                avgAiScore += r.getAiScore();
                scored++;
            }
        }
        stats.put("byTask", byTask);
        stats.put("avgAiScore", scored > 0 ? avgAiScore / scored : 0);
        stats.put("scoredCount", scored);

        // 对应任务数
        List<MonitorTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<MonitorTask>().eq(MonitorTask::getDeleted, 0));
        stats.put("totalTasks", tasks.size());
        long activeTasks = tasks.stream().filter(t -> "ACTIVE".equals(t.getStatus())).count();
        stats.put("activeTasks", activeTasks);

        return stats;
    }
}
