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
 * Bark 推送通道适配器（iOS/macOS）。
 * config_json: { "server": "https://api.day.app", "deviceKey": "xxx", "group": "闲鱼" }
 * 可选: sound, url, icon
 */
@Component
public class BarkChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() { return "BARK"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        String server = text(cfg, "server", "https://api.day.app").replaceAll("/+$", "");
        String deviceKey = text(cfg, "deviceKey");
        String group = text(cfg, "group", "XianYu");
        String sound = text(cfg, "sound", "");
        String url = text(cfg, "url", "");
        String icon = text(cfg, "icon", "");

        if (deviceKey == null || deviceKey.isBlank()) {
            throw new IllegalStateException("Bark 通道未配置 deviceKey");
        }

        String endpoint = server + "/" + deviceKey;
        StringBuilder sb = new StringBuilder(endpoint);
        sb.append("?title=").append(java.net.URLEncoder.encode(title, "UTF-8"));
        sb.append("&body=").append(java.net.URLEncoder.encode(body, "UTF-8"));
        sb.append("&group=").append(group);
        if (!sound.isBlank()) sb.append("&sound=").append(sound);
        if (!url.isBlank()) sb.append("&url=").append(java.net.URLEncoder.encode(url, "UTF-8"));
        if (!icon.isBlank()) sb.append("&icon=").append(java.net.URLEncoder.encode(icon, "UTF-8"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(sb.toString()))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString("", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Bark 返回 HTTP " + code + ": " + resp.body());
        }
    }

    private String text(JsonNode n, String k) { return text(n, k, null); }
    private String text(JsonNode n, String k, String dflt) {
        JsonNode v = n == null ? null : n.get(k);
        return v != null && !v.isNull() ? v.asText() : dflt;
    }
}
