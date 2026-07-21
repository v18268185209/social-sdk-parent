package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiMonitorResultVO;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiMonitorTaskVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorResultMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.mapper.MonitorTaskMapper;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorResult;
import cn.net.rjnetwork.xianyu.manager.monitor.model.MonitorTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 监控任务域对外接口：列表按绑定白名单过滤，详情做账号作用域校验。
 * 任务列表不含敏感配置（cronExpression、aiPrompt）。
 */
@RestController
@RequestMapping("/openapi/v1/monitor")
public class OpenApiMonitorController {

    private final MonitorTaskMapper taskMapper;
    private final MonitorResultMapper resultMapper;
    private final OpenAppService openAppService;

    public OpenApiMonitorController(MonitorTaskMapper taskMapper,
                                    MonitorResultMapper resultMapper,
                                    OpenAppService openAppService) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.openAppService = openAppService;
    }

    // ---------- 监控任务 ----------
    @GetMapping("/tasks")
    public OpenApiResponse<List<OpenApiMonitorTaskVO>> listTasks(@RequestParam(required = false) Long accountId,
                                                                 @RequestParam(required = false) String status) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        Set<Long> bound = openAppService.getBoundAccountIds(app);
        LambdaQueryWrapper<MonitorTask> qw = new LambdaQueryWrapper<MonitorTask>()
                .orderByDesc(MonitorTask::getCreatedAt);
        if (status != null && !status.isBlank()) qw.eq(MonitorTask::getStatus, status);

        List<OpenApiMonitorTaskVO> result = taskMapper.selectList(qw).stream()
                .filter(t -> bound.isEmpty() || bound.contains(t.getAccountId()))
                .filter(t -> accountId == null || accountId.equals(t.getAccountId()))
                .map(this::toTaskVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/tasks/{id}")
    public OpenApiResponse<OpenApiMonitorTaskVO> getTask(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        MonitorTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "监控任务不存在");
        }
        openAppService.assertAccountAccessible(app, task.getAccountId());
        return OpenApiResponse.ok(toTaskVo(task));
    }

    // ---------- 监控结果 ----------
    @GetMapping("/results")
    public OpenApiResponse<List<OpenApiMonitorResultVO>> listResults(@RequestParam(required = false) Long accountId,
                                                                      @RequestParam Long taskId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        MonitorTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "监控任务不存在");
        }
        openAppService.assertAccountAccessible(app, task.getAccountId());

        LambdaQueryWrapper<MonitorResult> qw = new LambdaQueryWrapper<MonitorResult>()
                .eq(MonitorResult::getTaskId, taskId)
                .orderByDesc(MonitorResult::getCreatedAt);

        List<OpenApiMonitorResultVO> result = resultMapper.selectList(qw).stream()
                .map(this::toResultVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    private OpenApiMonitorTaskVO toTaskVo(MonitorTask t) {
        OpenApiMonitorTaskVO vo = new OpenApiMonitorTaskVO();
        vo.setId(t.getId());
        vo.setAccountId(t.getAccountId());
        vo.setName(t.getName());
        vo.setTaskType(t.getTaskType());
        vo.setStatus(t.getStatus());
        vo.setKeyword(t.getKeyword());
        vo.setCategoryId(t.getCategoryId());
        vo.setMinPrice(t.getMinPrice());
        vo.setMaxPrice(t.getMaxPrice());
        vo.setItemCondition(t.getItemCondition());
        vo.setLocationProvince(t.getLocationProvince());
        vo.setLocationCity(t.getLocationCity());
        vo.setLocationDistrict(t.getLocationDistrict());
        vo.setFreeShipping(t.getFreeShipping());
        vo.setMaxAgeHours(t.getMaxAgeHours());
        vo.setAiEnabled(t.getAiEnabled());
        vo.setAiModelId(t.getAiModelId());
        vo.setNextRunAt(t.getNextRunAt());
        vo.setLastRunAt(t.getLastRunAt());
        vo.setLastResultSummary(t.getLastResultSummary());
        vo.setRunCount(t.getRunCount());
        vo.setConsecutiveFailures(t.getConsecutiveFailures());
        vo.setCircuitOpen(t.getCircuitOpen());
        vo.setNotifyOnMatch(t.getNotifyOnMatch());
        vo.setNotifyChannelId(t.getNotifyChannelId());
        vo.setCreatedAt(t.getCreatedAt());
        vo.setUpdatedAt(t.getUpdatedAt());
        return vo;
    }

    private OpenApiMonitorResultVO toResultVo(MonitorResult r) {
        OpenApiMonitorResultVO vo = new OpenApiMonitorResultVO();
        vo.setId(r.getId());
        vo.setTaskId(r.getTaskId());
        vo.setItemId(r.getItemId());
        vo.setItemTitle(r.getItemTitle());
        vo.setPrice(r.getPrice());
        vo.setImageUrl(r.getImageUrl());
        vo.setSellerNickname(r.getSellerNickname());
        vo.setSellerCreditScore(r.getSellerCreditScore());
        vo.setItemUrl(r.getItemUrl());
        vo.setAiScore(r.getAiScore());
        vo.setAiReason(r.getAiReason());
        vo.setMatchedKeywords(r.getMatchedKeywords());
        vo.setNotified(r.getNotified());
        vo.setSnapshotId(r.getSnapshotId());
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }
}
