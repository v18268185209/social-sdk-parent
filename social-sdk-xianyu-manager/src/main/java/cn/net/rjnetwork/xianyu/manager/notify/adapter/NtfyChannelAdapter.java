package cn.net.rjnetwork.xianyu.manager.notify.adapter;

import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ntfy.sh 通道适配器。
 * config_json: { "server": "https://ntfy.sh", "topic": "xianyu", "title": "闲鱼" }
 * 可选: tags, priority
 */
@Component
public class NtfyChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() { return "NTFY"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        String server = text(cfg, "server", "https://ntfy.sh").replaceAll("/+$", "");
        String topic = text(cfg, "topic");

        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("ntfy 通道未配置 topic");
        }

        String url = server + "/" + java.net.URLEncoder.encode(topic, "UTF-8");
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Title", title)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        String tags = text(cfg, "tags", "");
        if (!tags.isBlank()) builder.header("Tags", tags);
        String priority = text(cfg, "priority", "");
        if (!priority.isBlank()) builder.header("Priority", priority);

        HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("ntfy 返回 HTTP " + code + ": " + resp.body());
        }
    }

    private String text(JsonNode n, String k) { return text(n, k, null); }
    private String text(JsonNode n, String k, String dflt) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && !v.isNull() ? v.asText() : dflt;
    }
}
