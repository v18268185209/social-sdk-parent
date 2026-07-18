package cn.net.rjnetwork.core.ai.client;

import cn.net.rjnetwork.core.ai.model.AiRequest;
import cn.net.rjnetwork.core.ai.model.AiResponse;

import java.util.function.Consumer;

/**
 * AI 客户端接口（OpenAI 兼容协议）
 * 所有厂商实现（Agnes / OpenAI / DeepSeek / ...）均走此接口
 */
public interface AiClient {

    /**
     * 文本对话（同步）
     * @param request 请求体
     * @return 响应体
     */
    AiResponse chatCompletion(AiRequest request);

    /**
     * 文本对话（流式，逐 token 回调）
     * @param request 请求体（stream=true）
     * @param onChunk 每收到一个 chunk 回调
     */
    void chatCompletionStream(AiRequest request, Consumer<AiResponse> onChunk);

    /**
     * 图文对话（同步）
     * 等价于 messages[].content = [{type:"text",...},{type:"image_url",...}]
     */
    default AiResponse chatWithImage(AiRequest request) {
        return chatCompletion(request);
    }
}
