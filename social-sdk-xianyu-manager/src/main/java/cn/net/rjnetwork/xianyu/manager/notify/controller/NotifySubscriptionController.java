^package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifySubscriptionMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifySubscription;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订阅规则：场景 -> 通道 + 接收范围。决定“什么事件发给谁”。
 */
@RestController
@RequestMapping("/api/notify/subscriptions")
public class NotifySubscriptionController {

    private final NotifySubscriptionMapper subscriptionMapper;

    public NotifySubscriptionController(NotifySubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }

    @GetMapping
    public ApiResponse<List<NotifySubscription>> list(@RequestParam(required = false) String scenario) {
        LambdaQueryWrapper<NotifySubscription> qw = new LambdaQueryWrapper<>();
        if (scenario != null && !scenario.isBlank()) qw.eq(NotifySubscription::getScenario, scenario);
        qw.orderByDesc(NotifySubscription::getId);
        return ApiResponse.ok(subscriptionMapper.selectList(qw));
    }

    @PostMapping
    public ApiResponse<NotifySubscription> create(@RequestBody NotifySubscription sub) {
        subscriptionMapper.insert(sub);
        return ApiResponse.ok(sub);
    }

    @PutMapping("/{id}")
    public ApiResponse<NotifySubscription> update(@PathVariable Long id, @RequestBody NotifySubscription sub) {
        sub.setId(id);
        subscriptionMapper.updateById(sub);
        return ApiResponse.ok(sub);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subscriptionMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        NotifySubscription s = new NotifySubscription();
        s.setId(id);
        s.setEnabled(enabled);
        subscriptionMapper.updateById(s);
        return ApiResponse.ok(null);
    }
}
