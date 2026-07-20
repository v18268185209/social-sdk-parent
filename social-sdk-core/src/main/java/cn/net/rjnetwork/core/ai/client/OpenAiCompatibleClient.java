package cn.net.rjnetwork.core.ai.client;

import cn.net.rjnetwork.core.ai.model.AiRequest;
import cn.net.rjnetwork.core.ai.model.AiMessage;
import cn.net.rjnetwork.core.ai.model.AiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议的 AI 客户端实现
 * 适配任何 OpenAI 兼容的厂商（Agnes AI、OpenAI、DeepSeek、...）
 */
public class OpenAiCompatibleClient implements AiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiBaseUrl;   // https://apihub.agnes-ai.com/v1
    private final String apiKey;
    private final int connectTimeout;
    private final int readTimeout;

    public OpenAiCompatibleClient(String apiBaseUrl, String apiKey) {
        this(apiBaseUrl, apiKey, 5000, 30000);
    }

    public OpenAiCompatibleClient(String apiBaseUrl, String apiKey, int connectTimeout, int readTimeout) {
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        this.apiKey = apiKey;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public AiResponse chatCompletion(AiRequest request) {
        try {
            String body = buildRequestBody(request);
            String response = httpPost(apiBaseUrl + "/chat/completions", body);
            return parseResponse(response);
        } catch (IOException e) {
            throw new RuntimeException("AI request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatCompletionStream(AiRequest request, Consumer<AiResponse> onChunk) {
        try {
            request.setStream(true);
            String body = buildRequestBody(request);
            httpPostStream(apiBaseUrl + "/chat/completions", body, onChunk);
        } catch (IOException e) {
            throw new RuntimeException("AI stream request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 按 OpenAI 标准规范列出远端可用模型：GET {apiBaseUrl}/models
     * 返回 OpenAI 规范中的 data 列表（含 id、owned_by、object 等字段）
     */
    public List<Map<String, Object>> listModels() {
        try {
            String response = httpGet(apiBaseUrl + "/models");
            JsonNode node = MAPPER.readTree(response);
            List<Map<String, Object>> result = new ArrayList<>();
            if (node.has("data") && node.get("data").isArray()) {
                for (JsonNode m : node.get("data")) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", m.path("id").asText());
                    item.put("ownedBy", m.path("owned_by").asText(""));
                    item.put("object", m.path("object").asText(""));
                    result.add(item);
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list remote models: " + e.getMessage(), e);
        }
    }

    // ----- internals -----

    private String buildRequestBody(AiRequest request) throws IOException {
        // 构造 OpenAI 兼容 JSON，让 content 支持 string 或 array
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("model", request.getModel());

        // 处理 messages：支持纯 string content 和 contentBlocks
        List<Object> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (AiMessage msg : request.getMessages()) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("role", msg.getRole());
                if (msg.getContentBlocks() != null && !msg.getContentBlocks().isEmpty()) {
                    m.put("content", msg.getContentBlocks());
                } else {
                    m.put("content", msg.getContent());
                }
                if (msg.getToolCallId() != null) {
                    m.put("tool_call_id", msg.getToolCallId());
                }
                messages.add(m);
            }
        }
        root.put("messages", messages);

        if (request.getTemperature() != null) root.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) root.put("max_tokens", request.getMaxTokens());
        if (request.getTopP() != null) root.put("top_p", request.getTopP());
        if (request.getStream() != null && request.getStream()) root.put("stream", true);
        if (request.getTools() != null) root.put("tools", request.getTools());
        if (request.getToolChoice() != null) root.put("tool_choice", request.getToolChoice());

        // Thinking 模式（Agnes / 部分厂商兼容）
        if (request.getEnableThinking() != null && request.getEnableThinking()) {
            java.util.Map<String, Object> chatTemplateKwargs = new java.util.LinkedHashMap<>();
            chatTemplateKwargs.put("enable_thinking", true);
            root.put("chat_template_kwargs", chatTemplateKwargs);
        }
        if (request.getThinkingBudgetTokens() != null) {
            java.util.Map<String, Object> thinking = new java.util.LinkedHashMap<>();
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", request.getThinkingBudgetTokens());
            root.put("thinking", thinking);
        }

        return MAPPER.writeValueAsString(root);
    }

    private String httpPost(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 400) {
                String err = readStream(conn.getErrorStream());
                throw new IOException("AI API error (HTTP " + code + "): " + err);
            }
            return readStream(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code >= 400) {
                String err = readStream(conn.getErrorStream());
                throw new IOException("AI API error (HTTP " + code + "): " + err);
            }
            return readStream(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private void httpPostStream(String urlStr, String body, Consumer<AiResponse> onChunk) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 400) {
                String err = readStream(conn.getErrorStream());
                throw new IOException("AI API error (HTTP " + code + "): " + err);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith("data: ")) continue;
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode node = MAPPER.readTree(data);
                        AiResponse chunk = parseResponseNode(node);
                        onChunk.accept(chunk);
                    } catch (Exception e) {
                        System.err.println("[AI stream] parse chunk failed: " + e.getMessage());
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private AiResponse parseResponse(String json) throws IOException {
        JsonNode node = MAPPER.readTree(json);
        return parseResponseNode(node);
    }

    private AiResponse parseResponseNode(JsonNode node) throws IOException {
        AiResponse resp = new AiResponse();
        if (node.has("id")) resp.setId(node.get("id").asText());
        if (node.has("model")) resp.setModel(node.get("model").asText());

        if (node.has("choices") && node.get("choices").isArray()) {
            List<AiResponse.Choice> choices = new ArrayList<>();
            for (JsonNode c : node.get("choices")) {
                AiResponse.Choice choice = new AiResponse.Choice();
                choice.setIndex(c.path("index").asInt(0));
                choice.setFinishReason(c.path("finish_reason").asText());
                if (c.has("message")) {
                    AiMessage msg = new AiMessage();
                    msg.setRole(c.path("message").path("role").asText("assistant"));
                    msg.setContent(c.path("message").path("content").asText());
                    choice.setMessage(msg);
                }
                choices.add(choice);
            }
            resp.setChoices(choices);
        }

        if (node.has("usage")) {
            JsonNode u = node.get("usage");
            AiResponse.Usage usage = new AiResponse.Usage();
            usage.setPromptTokens(u.path("prompt_tokens").asInt(0));
            usage.setCompletionTokens(u.path("completion_tokens").asInt(0));
            usage.setTotalTokens(u.path("total_tokens").asInt(0));
            resp.setUsage(usage);
        }

        return resp;
    }

    private String readStream(java.io.InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
