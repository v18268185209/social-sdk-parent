^package cn.net.rjnetwork.xianyu.manager.ai.service;

import cn.net.rjnetwork.core.ai.client.AiClient;
import cn.net.rjnetwork.core.ai.client.OpenAiCompatibleClient;
import cn.net.rjnetwork.core.ai.model.AiMessage;
import cn.net.rjnetwork.core.ai.model.AiRequest;
import cn.net.rjnetwork.core.ai.model.AiResponse;
import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiModelMapper;
import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiProviderMapper;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiModel;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 聊天服务（对接厂商 + 模型）
 * 提供文本/图文/工具调用统一入口，内部缓存 AiClient 避免重复创建
 */
@Service
public class AiChatService {

    private final AiProviderMapper providerMapper;
    private final AiModelMapper modelMapper;

    /** 缓存 AiClient（按 providerId 缓存） */
    private final Map<Long, AiClient> clientCache = new ConcurrentHashMap<>();

    public AiChatService(AiProviderMapper providerMapper, AiModelMapper modelMapper) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
    }

    /**
     * 文本对话（同步）
     * @param modelId 模型 ID（ai_model.id）
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return AI 回复内容（纯文本）
     */
    public String chat(Long modelId, String systemPrompt, String userMessage) {
        AiClient client = getClient(modelId);
        AiModel model = getModel(modelId);

        AiRequest request = AiRequest.of(model.getModelName(), List.of(
                AiMessage.system(systemPrompt),
                AiMessage.user(userMessage)
        ));

        // 应用模型的默认参数
        if (model.getDefaultTemperature() != null) request.setTemperature(model.getDefaultTemperature());
        if (model.getDefaultMaxTokens() != null) request.setMaxTokens(model.getDefaultMaxTokens());

        AiResponse response = client.chatCompletion(request);
        return response.firstContent();
    }

    /**
     * 图文对话（同步，支持 image_url 输入）
     */
    public String chatWithImage(Long modelId, String systemPrompt, String userMessage, List<String> imageUrls) {
        AiClient client = getClient(modelId);
        AiModel model = getModel(modelId);

        // 构造 contentBlocks
        List<AiMessage.ContentBlock> blocks = new java.util.ArrayList<>();
        blocks.add(AiMessage.ContentBlock.text(userMessage));
        for (String url : imageUrls) {
            blocks.add(AiMessage.ContentBlock.imageUrl(url));
        }

        AiMessage userMsg = new AiMessage("user", null);
        userMsg.setContentBlocks(blocks);

        AiRequest request = AiRequest.of(model.getModelName(), List.of(
                AiMessage.system(systemPrompt),
                userMsg
        ));

        if (model.getDefaultTemperature() != null) request.setTemperature(model.getDefaultTemperature());
        if (model.getDefaultMaxTokens() != null) request.setMaxTokens(model.getDefaultMaxTokens());

        AiResponse response = client.chatCompletion(request);
        return response.firstContent();
    }

    // ----- internals -----

    private AiClient getClient(Long modelId) {
        AiModel model = getModel(modelId);
        Long providerId = model.getProviderId();
        return clientCache.computeIfAbsent(providerId, id -> {
            AiProvider provider = providerMapper.selectById(id);
            if (provider == null) {
                throw new IllegalStateException("AI provider not found: " + id);
            }
            return new OpenAiCompatibleClient(provider.getApiBaseUrl(), provider.getApiKey());
        });
    }

    private AiModel getModel(Long modelId) {
        AiModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("AI model not found: " + modelId);
        }
        return model;
    }

    /** 清除客户端缓存（厂商 key 更换时调用） */
    public void clearClientCache() {
        clientCache.clear();
    }
}
