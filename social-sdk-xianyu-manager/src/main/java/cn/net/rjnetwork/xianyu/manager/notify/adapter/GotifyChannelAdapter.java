package cn.net.rjnetwork.xianyu.manager.notify.adapter;

import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gotify 通道适配器。
 * config_json: { "server": "https://gotify.example.com", "token": "xxx", "priority": "5" }
 * token 为 client token 或 application token
 */
@Component
public class GotifyChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() { return "GOTIFY"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        String server = text(cfg, "server").replaceAll("/+$", "");
        String token = text(cfg, "token");
        String priority = text(cfg, "priority", "5");

        if (server == null || server.isBlank()) {
            throw new IllegalStateException("Gotify 通道未配置 server");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Gotify 通道未配置 token");
        }

        String url = server + "/message?token=" + java.net.URLEncoder.encode(token, "UTF-8");
        ObjectNode payload = mapper.createObjectNode();
        payload.put("title", title);
        payload.put("message", body);
        payload.put("priority", Integer.parseInt(priority));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Gotify 返回 HTTP " + code + ": " + resp.body());
        }
    }

    private String text(JsonNode n, String k) { return text(n, k, null); }
    private String text(JsonNode n, String k, String dflt) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && !v.isNull() ? v.asText() : dflt;
    }
}
