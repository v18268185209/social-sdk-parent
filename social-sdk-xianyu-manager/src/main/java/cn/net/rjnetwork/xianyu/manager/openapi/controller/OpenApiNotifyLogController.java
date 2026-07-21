package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiNotifyLogVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyLogMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 通知投递日志对外接口：按账号/场景/状态/时间范围过滤。
 */
@RestController
@RequestMapping("/openapi/v1/notify/logs")
public class OpenApiNotifyLogController {

    private final NotifyLogMapper logMapper;
    private final OpenAppService openAppService;

    public OpenApiNotifyLogController(NotifyLogMapper logMapper, OpenAppService openAppService) {
        this.logMapper = logMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiNotifyLogVO>> list(@RequestParam(required = false) Long accountId,
                                                          @RequestParam(required = false) String scenario,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) LocalDateTime from,
                                                          @RequestParam(required = false) LocalDateTime to) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);

        LambdaQueryWrapper<NotifyLog> qw = new LambdaQueryWrapper<NotifyLog>()
                .orderByDesc(NotifyLog::getCreatedAt);
        if (scenario != null && !scenario.isBlank()) qw.eq(NotifyLog::getScenario, scenario);
        if (status != null && !status.isBlank()) qw.eq(NotifyLog::getStatus, status);
        if (from != null) qw.ge(NotifyLog::getCreatedAt, from);
        if (to != null) qw.le(NotifyLog::getCreatedAt, to);

        List<OpenApiNotifyLogVO> result = logMapper.selectList(qw).stream()
                .filter(log -> bound.isEmpty() || bound.contains(log.getChannelId()))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    private OpenApiNotifyLogVO toVo(NotifyLog log) {
        OpenApiNotifyLogVO vo = new OpenApiNotifyLogVO();
        vo.setId(log.getId());
        vo.setScenario(log.getScenario());
        vo.setChannelId(log.getChannelId());
        vo.setChannelType(log.getChannelType());
        vo.setRecipient(log.getRecipient());
        vo.setStatus(log.getStatus());
        vo.setError(log.getError());
        vo.setCreatedAt(log.getCreatedAt());
        vo.setSentAt(log.getSentAt());
        return vo;
    }
}
