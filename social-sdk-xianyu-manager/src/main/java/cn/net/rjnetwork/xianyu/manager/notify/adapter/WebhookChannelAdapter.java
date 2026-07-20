^package cn.net.rjnetwork.xianyu.manager.notify.adapter;

import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Webhook 通道适配器，支持四类机器人：
 * - WECHAT_WORK（企业微信）：qyapi.weixin.qq.com/cgi-bin/webhook/send?key=
 * - DINGTALK（钉钉）：oapi.dingtalk.com/robot/send?access_token=
 * - FEISHU（飞书）：open.feishu.cn/open-apis/bot/v2/hook/
 * - GENERIC（通用）：POST 用户自定义 JSON 到指定 URL
 * 企微/钉钉/飞书均支持 secret 签名（HmacSHA256）。
 */
@Component
public class WebhookChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() { return "WEBHOOK"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        String webhookType = text(cfg, "webhookType", "GENERIC").toUpperCase();
        // 前端 Webhook 配置存的是 webhookUrl（见 notify/Index.vue emptyWebhookConfig），
        // 通用/SMS 场景可能用 url。两个都兼容，优先 webhookUrl。
        String url = text(cfg, "webhookUrl");
        if (url == null || url.isBlank()) url = text(cfg, "url");
        String secret = text(cfg, "secret");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Webhook 通道 " + channel.getName() + " 未配置 URL");
        }

        String payload;
        String finalUrl = url;
        switch (webhookType) {
            case "WECHAT_WORK" -> {
                ObjectNode root = mapper.createObjectNode();
                root.put("msgtype", "markdown");
                ObjectNode md = mapper.createObjectNode();
                md.put("content", "## " + title + "\n" + body);
                root.set("markdown", md);
                if (secret != null && !secret.isBlank()) {
                    long ts = System.currentTimeMillis();
                    root.put("timestamp", String.valueOf(ts));
                    root.put("sign", sign(ts + "\n" + secret, secret));
                }
                payload = mapper.writeValueAsString(root);
            }
            case "DINGTALK" -> {
                ObjectNode root = mapper.createObjectNode();
                root.put("msgtype", "markdown");
                ObjectNode md = mapper.createObjectNode();
                md.put("title", title);
                md.put("text", "### " + title + "\n" + body);
                root.set("markdown", md);
                if (secret != null && !secret.isBlank()) {
                    long ts = System.currentTimeMillis();
                    String sign = sign(ts + "\n" + secret, secret);
                    finalUrl = url + (url.contains("?") ? "&" : "?")
                            + "timestamp=" + ts + "&sign=" + URLEncoder.encode(sign, "UTF-8");
                }
                payload = mapper.writeValueAsString(root);
            }
            case "FEISHU" -> {
                ObjectNode root = mapper.createObjectNode();
                root.put("msg_type", "interactive");
                ObjectNode card = mapper.createObjectNode();
                ObjectNode header = mapper.createObjectNode();
                ObjectNode titleNode = mapper.createObjectNode();
                titleNode.put("tag", "plain_text");
                titleNode.put("content", title);
                header.set("title", titleNode);
                ArrayNode elements = mapper.createArrayNode();
                ObjectNode div = mapper.createObjectNode();
                div.put("tag", "div");
                ObjectNode textNode = mapper.createObjectNode();
                textNode.put("tag", "lark_md");
                textNode.put("content", body);
                div.set("text", textNode);
                elements.add(div);
                card.set("header", header);
                card.set("elements", elements);
                root.set("card", card);
                if (secret != null && !secret.isBlank()) {
                    long ts = System.currentTimeMillis() / 1000;
                    root.put("timestamp", String.valueOf(ts));
                    root.put("sign", sign(ts + "\n" + secret, secret));
                }
                payload = mapper.writeValueAsString(root);
            }
            default -> {
                ObjectNode root = mapper.createObjectNode();
                root.put("title", title);
                root.put("content", body);
                if (!recipients.isEmpty()) {
                    ArrayNode arr = mapper.createArrayNode();
                    recipients.forEach(arr::add);
                    root.set("recipients", arr);
                }
                if (vars != null && !vars.isEmpty()) {
                    root.set("vars", mapper.valueToTree(vars));
                }
                payload = mapper.writeValueAsString(root);
            }
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Webhook 返回 HTTP " + code + "： " + resp.body());
        }
    }

    /** HmacSHA256(base64) 签名，企微/钉钉/飞书通用算法 */
    private String sign(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    private String text(JsonNode n, String k) { return text(n, k, null); }

    private String text(JsonNode n, String k, String dflt) {
        JsonNode v = n.get(k);
        return v != null && !v.isNull() ? v.asText() : dflt;
    }
}
