^package cn.net.rjnetwork.xianyu.manager.notify.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyMessageMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站内通知收件箱：列表、未读计数、标记已读。
 */
@RestController
@RequestMapping("/api/notify/messages")
public class NotifyMessageController {

    private final NotifyMessageMapper messageMapper;

    public NotifyMessageController(NotifyMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @GetMapping
    public ApiResponse<Page<NotifyMessage>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<NotifyMessage> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(NotifyMessage::getCreatedAt);
        return ApiResponse.ok(messageMapper.selectPage(new Page<>(page, size), qw));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        long count = messageMapper.selectCount(
                new LambdaQueryWrapper<NotifyMessage>().eq(NotifyMessage::getIsRead, false));
        return ApiResponse.ok(Map.of("unread", count));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        NotifyMessage m = new NotifyMessage();
        m.setId(id);
        m.setIsRead(true);
        messageMapper.updateById(m);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        List<NotifyMessage> unread = messageMapper.selectList(
                new LambdaQueryWrapper<NotifyMessage>().eq(NotifyMessage::getIsRead, false));
        for (NotifyMessage m : unread) {
            m.setIsRead(true);
            messageMapper.updateById(m);
        }
        return ApiResponse.ok(null);
    }
}
