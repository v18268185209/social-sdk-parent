^package cn.net.rjnetwork.xianyu.manager.reply.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.reply.model.XianyuAutoReplyLog;
import cn.net.rjnetwork.xianyu.manager.reply.service.AutoReplyLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reply-logs")
public class AutoReplyLogController {

    private final AutoReplyLogService logService;

    public AutoReplyLogController(AutoReplyLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ApiResponse<Page<XianyuAutoReplyLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String replyType,
            @RequestParam(required = false) Boolean matched) {
        return ApiResponse.ok(logService.listPage(page, size, accountId, replyType, matched));
    }
}
