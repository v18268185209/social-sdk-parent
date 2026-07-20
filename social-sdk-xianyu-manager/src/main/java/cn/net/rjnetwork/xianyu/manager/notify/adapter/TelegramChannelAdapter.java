package cn.net.rjnetwork.xianyu.manager.notify.adapter;

import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Telegram Bot 通道适配器。
 * config_json: { "botToken": "xxx", "chatId": "xxx", "parseMode": "HTML" }
 * 支持多个 chatId 用逗号分隔
 */
@Component
public class TelegramChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() { return "TELEGRAM"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        String botToken = text(cfg, "botToken");
        String chatId = text(cfg, "chatId");
        String parseMode = text(cfg, "parseMode", "HTML");

        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("Telegram 通道未配置 botToken");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalStateException("Telegram 通道未配置 chatId");
        }

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        ObjectNode payload = mapper.createObjectNode();
        payload.put("chat_id", chatId);
        payload.put("text", "<b>" + title + "</b>\n\n" + body);
        payload.put("parse_mode", parseMode);
        payload.put("disable_web_page_preview", true);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Telegram 返回 HTTP " + code + ": " + resp.body());
        }
        JsonNode r = mapper.readTree(resp.body());
        if (!r.has("ok") || !r.get("ok").asBoolean()) {
            String desc = r.has("description") ? r.get("description").asText() : resp.body();
            throw new IllegalStateException("Telegram 发送失败: " + desc);
        }
    }

    private String text(JsonNode n, String k) { return text(n, k, null); }
    private String text(JsonNode n, String k, String dflt) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && !v.isNull() ? v.asText() : dflt;
    }
}
