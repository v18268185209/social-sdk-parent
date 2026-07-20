^package cn.net.rjnetwork.xianyu.manager.ai.controller;

import cn.net.rjnetwork.xianyu.manager.ai.service.AiChatService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 聊天测试接口
 * POST /api/ai/chat/test
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testChat(
            @RequestBody Map<String, Object> request) {
        Long modelId = Long.valueOf(request.get("modelId").toString());
        String systemPrompt = (String) request.getOrDefault("systemPrompt", "");
        String userMessage = (String) request.getOrDefault("userMessage", "");

        String reply = chatService.chat(modelId, systemPrompt, userMessage);
        return ApiResponse.ok(Map.of(
                "reply", reply,
                "modelId", modelId.toString()
        ));
    }
}
