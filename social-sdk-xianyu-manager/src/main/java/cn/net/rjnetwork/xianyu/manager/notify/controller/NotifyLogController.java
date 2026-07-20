package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyLogMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知投递日志查询。
 */
@RestController
@RequestMapping("/api/notify/logs")
public class NotifyLogController {

    private final NotifyLogMapper logMapper;

    public NotifyLogController(NotifyLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @GetMapping
    public ApiResponse<Page<NotifyLog>> list(
            @RequestParam(required = false) String scenario,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<NotifyLog> qw = new LambdaQueryWrapper<>();
        if (scenario != null && !scenario.isBlank()) qw.eq(NotifyLog::getScenario, scenario);
        qw.orderByDesc(NotifyLog::getCreatedAt);
        return ApiResponse.ok(logMapper.selectPage(new Page<>(page, size), qw));
    }

    @GetMapping("/recent")
    public ApiResponse<List<NotifyLog>> recent(@RequestParam(defaultValue = "20") int limit) {
        LambdaQueryWrapper<NotifyLog> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(NotifyLog::getCreatedAt).last("LIMIT " + limit);
        return ApiResponse.ok(logMapper.selectList(qw));
    }
}
