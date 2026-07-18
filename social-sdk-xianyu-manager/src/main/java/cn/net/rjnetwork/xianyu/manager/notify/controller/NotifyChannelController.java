package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyChannelMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import cn.net.rjnetwork.xianyu.manager.notify.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知通道配置（邮件/Webhook）。config_json 入库前加密，返回前端时解密。
 */
@RestController
@RequestMapping("/api/notify/channels")
public class NotifyChannelController {

    private final NotifyChannelMapper channelMapper;
    private final CryptoUtil cryptoUtil;
    private final NotificationService notificationService;

    public NotifyChannelController(NotifyChannelMapper channelMapper, CryptoUtil cryptoUtil,
                                   NotificationService notificationService) {
        this.channelMapper = channelMapper;
        this.cryptoUtil = cryptoUtil;
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotifyChannel>> list(@RequestParam(required = false) String type) {
        LambdaQueryWrapper<NotifyChannel> qw = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) qw.eq(NotifyChannel::getType, type);
        qw.orderByDesc(NotifyChannel::getId);
        return ApiResponse.ok(decryptList(channelMapper.selectList(qw)));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotifyChannel> get(@PathVariable Long id) {
        NotifyChannel c = channelMapper.selectById(id);
        if (c != null) c.setConfigJson(cryptoUtil.decrypt(c.getConfigJson()));
        return ApiResponse.ok(c);
    }

    @PostMapping
    public ApiResponse<NotifyChannel> create(@RequestBody NotifyChannel channel) {
        channel.setConfigJson(cryptoUtil.encrypt(channel.getConfigJson()));
        channelMapper.insert(channel);
        return ApiResponse.ok(channel);
    }

    @PutMapping("/{id}")
    public ApiResponse<NotifyChannel> update(@PathVariable Long id, @RequestBody NotifyChannel channel) {
        channel.setId(id);
        if (channel.getConfigJson() != null) {
            channel.setConfigJson(cryptoUtil.encrypt(channel.getConfigJson()));
        }
        channelMapper.updateById(channel);
        return ApiResponse.ok(channel);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        channelMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        NotifyChannel c = new NotifyChannel();
        c.setId(id);
        c.setEnabled(enabled);
        channelMapper.updateById(c);
        return ApiResponse.ok(null);
    }

    /** 发送测试：直接用该通道发一条测试消息 */
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        NotifyChannel c = channelMapper.selectById(id);
        if (c == null) return ApiResponse.fail("通道不存在");
        c.setConfigJson(cryptoUtil.decrypt(c.getConfigJson()));
        notificationService.sendTest(c,
                String.valueOf(body.getOrDefault("title", "测试通知")),
                String.valueOf(body.getOrDefault("body", "这是一条来自 AI鱼多宝 的测试消息。")));
        return ApiResponse.ok(Map.of("sent", true));
    }

    private List<NotifyChannel> decryptList(List<NotifyChannel> list) {
        list.forEach(c -> c.setConfigJson(cryptoUtil.decrypt(c.getConfigJson())));
        return list;
    }
}
