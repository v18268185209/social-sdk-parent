package cn.net.rjnetwork.xianyu.manager.message.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(@RequestParam Long accountId) {
        return ApiResponse.ok(messageService.listSessionSummaries(accountId));
    }

    @GetMapping("/history")
    public ApiResponse<List<XianyuMessage>> history(
            @RequestParam Long accountId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        messageService.pullHistoryIfEmpty(accountId, sessionId, limit);
        return ApiResponse.ok(messageService.getHistory(accountId, sessionId, limit));
    }

    @PostMapping("/send")
    public ApiResponse<XianyuMessage> send(@RequestBody MessageSendRequest request) throws Exception {
        XianyuMessage sent = messageService.sendMessage(request);
        return ApiResponse.ok(sent);
    }

    @PostMapping("/syncNow")
    public ApiResponse<String> syncNow() {
        messageService.syncAllAccounts();
        return ApiResponse.ok("OK");
    }
}
