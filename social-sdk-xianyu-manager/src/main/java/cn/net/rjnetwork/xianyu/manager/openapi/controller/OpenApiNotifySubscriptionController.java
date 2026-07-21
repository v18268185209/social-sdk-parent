package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiNotifySubscriptionVO;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifySubscriptionMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifySubscription;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知订阅规则对外接口：全局目录，按场景/启用状态过滤。
 */
@RestController
@RequestMapping("/openapi/v1/notify/subscriptions")
public class OpenApiNotifySubscriptionController {

    private final NotifySubscriptionMapper subscriptionMapper;
    private final OpenAppService openAppService;

    public OpenApiNotifySubscriptionController(NotifySubscriptionMapper subscriptionMapper,
                                               OpenAppService openAppService) {
        this.subscriptionMapper = subscriptionMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiNotifySubscriptionVO>> list(@RequestParam(required = false) String scenario,
                                                                    @RequestParam(required = false) Boolean enabled) {
        LambdaQueryWrapper<NotifySubscription> qw = new LambdaQueryWrapper<NotifySubscription>()
                .orderByDesc(NotifySubscription::getUpdatedAt);
        if (scenario != null && !scenario.isBlank()) qw.eq(NotifySubscription::getScenario, scenario);
        if (enabled != null) qw.eq(NotifySubscription::getEnabled, enabled);

        List<OpenApiNotifySubscriptionVO> result = subscriptionMapper.selectList(qw).stream()
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiNotifySubscriptionVO> get(@PathVariable Long id) {
        NotifySubscription sub = subscriptionMapper.selectById(id);
        if (sub == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "订阅规则不存在");
        }
        return OpenApiResponse.ok(toVo(sub));
    }

    private OpenApiNotifySubscriptionVO toVo(NotifySubscription s) {
        OpenApiNotifySubscriptionVO vo = new OpenApiNotifySubscriptionVO();
        vo.setId(s.getId());
        vo.setScenario(s.getScenario());
        vo.setChannelId(s.getChannelId());
        vo.setRecipientScope(s.getRecipientScope());
        vo.setRecipients(s.getRecipients());
        vo.setAccountScope(s.getAccountScope());
        vo.setAccountIds(s.getAccountIds());
        vo.setEnabled(s.getEnabled());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }
}
