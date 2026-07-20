package cn.net.rjnetwork.xianyu.manager.ai.controller;

import cn.net.rjnetwork.xianyu.manager.ai.service.AiChatService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 能力演示接口（端到端 demo）
 * 场景：商品描述优化、关键词提取、标题生成
 */
@RestController
@RequestMapping("/api/ai/demo")
public class AiDemoController {

    private final AiChatService chatService;

    public AiDemoController(AiChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 商品描述优化 demo
     * POST /api/ai/demo/optimize-description
     * {
     *   "modelId": 1,
     *   "productTitle": "iPhone 15 Pro",
     *   "rawDescription": "九成新，无划痕，原装电池 92%",
     *   "tone": "professional"  // professional / casual / luxury
     * }
     */
    @PostMapping("/optimize-description")
    public ApiResponse<Map<String, String>> optimizeDescription(
            @RequestBody Map<String, Object> request) {
        Long modelId = Long.valueOf(request.get("modelId").toString());
        String productTitle = (String) request.getOrDefault("productTitle", "");
        String rawDescription = (String) request.getOrDefault("rawDescription", "");
        String tone = (String) request.getOrDefault("tone", "professional");

        String systemPrompt = """
                你是一个闲鱼商品描述优化专家。请根据用户提供的商品信息，生成一段吸引人的商品描述。
                要求：
                1. 突出商品卖点（成色、配件、使用情况）
                2. 语言真实可信，避免过度营销
                3. 包含售后/交易说明（如包邮、验货、退换）
                4. 控制在 150-200 字
                5. 语气风格：%s
                """.formatted(tone);

        String userMessage = "商品标题：%s\n原始描述：%s".formatted(productTitle, rawDescription);

        String optimized = chatService.chat(modelId, systemPrompt, userMessage);
        return ApiResponse.ok(Map.of(
                "productTitle", productTitle,
                "rawDescription", rawDescription,
                "optimizedDescription", optimized
        ));
    }

    /**
     * 商品标题生成 demo
     * POST /api/ai/demo/generate-title
     * {
     *   "modelId": 1,
     *   "keywords": ["iPhone", "15 Pro", "256G", "原色钛金属"],
     *   "category": "数码"
     * }
     */
    @PostMapping("/generate-title")
    public ApiResponse<Map<String, String>> generateTitle(
            @RequestBody Map<String, Object> request) {
        Long modelId = Long.valueOf(request.get("modelId").toString());
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) request.getOrDefault("keywords", List.of());
        String category = (String) request.getOrDefault("category", "");

        String systemPrompt = """
                你是一个闲鱼商品标题优化专家。请根据关键词生成一个吸引人的商品标题。
                要求：
                1. 包含核心关键词
                2. 突出卖点（成色、稀缺性、价格优势）
                3. 控制在 30 字以内（闲鱼限制）
                4. 不要加【】或特殊符号
                """;

        String userMessage = "分类：%s\n关键词：%s".formatted(category, String.join("、", keywords));

        String title = chatService.chat(modelId, systemPrompt, userMessage);
        return ApiResponse.ok(Map.of(
                "keywords", String.join("、", keywords),
                "generatedTitle", title
        ));
    }

    /**
     * 关键词提取 demo
     * POST /api/ai/demo/extract-keywords
     * {
     *   "modelId": 1,
     *   "text": "九成新 iPhone 15 Pro 256G 原色钛金属 包邮 支持验机"
     * }
     */
    @PostMapping("/extract-keywords")
    public ApiResponse<Map<String, Object>> extractKeywords(
            @RequestBody Map<String, Object> request) {
        Long modelId = Long.valueOf(request.get("modelId").toString());
        String text = (String) request.getOrDefault("text", "");

        String systemPrompt = """
                你是一个关键词提取专家。请从用户提供的文本中提取关键词。
                要求：
                1. 提取 3-5 个核心关键词
                2. 按重要性排序
                3. 只返回关键词列表，用逗号分隔
                """;

        String keywordsText = chatService.chat(modelId, systemPrompt, text);
        List<String> keywords = List.of(keywordsText.split("[,，、]")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return ApiResponse.ok(Map.of(
                "text", text,
                "keywords", keywords
        ));
    }

    /**
     * AI 聊天测试
     * POST /api/ai/chat/test
     * {
     *   "modelId": 1,
     *   "systemPrompt": "...",
     *   "userMessage": "你好"
     * }
     */
    @PostMapping("/chat/test")
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
