package cn.net.rjnetwork.xianyu.manager.notify.adapter;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;

/**
 * 短信通道适配器（SMS）。config_json 结构：
 * {
 *   "provider": "GENERIC" | "ALIYUN",
 *   "phones": "138...,139...",        // 默认手机号（订阅未指定接收人时回退）
 *   "signName": "签名",
 *   "templateCode": "SMS_xxx",
 *   "templateParam": "{\"code\":\"{body}\"}",  // 阿里云模板参数，{body} 会被正文替换
 *   // GENERIC：
 *   "url": "https://your-gateway/send",
 *   "bodyTemplate": "可选，{\"phones\":\"{phones}\",\"content\":\"{body}\"}",
 *   // ALIYUN：
 *   "accessKeyId": "...", "accessKeySecret": "...", "regionId": "cn-hangzhou"
 * }
 * 零新增依赖：GENERIC 走 JDK HttpClient POST；ALIYUN 走 RPC 签名 GET。
 */
@Component
public class SmsChannelAdapter implements ChannelAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String type() { return "SMS"; }

    @Override
    public void send(NotifyChannel channel, String title, String body, List<String> recipients) throws Exception {
        JsonNode cfg = mapper.readTree(channel.getConfigJson());
        String provider = text(cfg, "provider", "GENERIC").toUpperCase();

        // 接收人：订阅里指定的手机号，否则回退到通道默认 phones
        List<String> phones = (recipients != null && !recipients.isEmpty())
                ? recipients : splitPhones(text(cfg, "phones"));
        if (phones.isEmpty()) {
            throw new IllegalStateException("短信通道 " + channel.getName() + " 无接收手机号");
        }

        switch (provider) {
            case "ALIYUN" -> sendAliyun(cfg, phones, body);
            default -> sendGeneric(cfg, phones, title, body);
        }
    }

    /** 通用 HTTP 网关：POST JSON 到配置的 url */
    private void sendGeneric(JsonNode cfg, List<String> phones, String title, String body) throws Exception {
        String url = text(cfg, "url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("短信(GENERIC)通道 " + "未配置 url");
        }
        String signName = text(cfg, "signName", "");
        String templateCode = text(cfg, "templateCode", "");
        String templateParam = text(cfg, "templateParam", "");

        String payload;
        String bt = text(cfg, "bodyTemplate", "");
        if (!bt.isBlank()) {
            // 用户自定义负载模板，替换占位符
            payload = bt
                    .replace("{phones}", String.join(",", phones))
                    .replace("{body}", body)
                    .replace("{signName}", signName)
                    .replace("{templateCode}", templateCode)
                    .replace("{templateParam}", templateParam);
        } else {
            ObjectNode root = mapper.createObjectNode();
            root.put("action", "sendSms");
            if (!signName.isBlank()) root.put("signName", signName);
            if (!templateCode.isBlank()) root.put("templateCode", templateCode);
            if (!templateParam.isBlank()) root.put("templateParam", templateParam);
            ArrayNode arr = mapper.createArrayNode();
            phones.forEach(arr::add);
            root.set("phones", arr);
            root.put("title", title);
            root.put("content", body);
            payload = mapper.writeValueAsString(root);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("短信网关返回 HTTP " + code + "： " + resp.body());
        }
    }

    /** 阿里云短信：RPC 签名（HmacSHA1）+ GET 调用 dysmsapi */
    private void sendAliyun(JsonNode cfg, List<String> phones, String body) throws Exception {
        String accessKeyId = text(cfg, "accessKeyId");
        String accessKeySecret = text(cfg, "accessKeySecret");
        String signName = text(cfg, "signName");
        String templateCode = text(cfg, "templateCode");
        String regionId = text(cfg, "regionId", "cn-hangzhou");
        String templateParam = text(cfg, "templateParam", "{}");
        // 把正文替换进模板参数 JSON 的 {body} 占位
        if (templateParam.contains("{body}")) {
            String esc = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            templateParam = templateParam.replace("{body}", esc);
        }
        if (accessKeyId == null || accessKeySecret == null || signName == null || templateCode == null) {
            throw new IllegalStateException("阿里云短信通道缺少 accessKeyId/secret/signName/templateCode");
        }

        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", accessKeyId);
        params.put("Action", "SendSms");
        params.put("Format", "JSON");
        params.put("PhoneNumbers", String.join(",", phones));
        params.put("RegionId", regionId);
        params.put("SignName", signName);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("TemplateCode", templateCode);
        params.put("TemplateParam", templateParam);
        params.put("Timestamp", gmtNow());
        params.put("Version", "2017-05-25");

        String canonical = params.entrySet().stream()
                .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                .reduce((a, b) -> a + "&" + b).orElse("");
        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonical);
        String signature = signHmacSha1(stringToSign, accessKeySecret + "&");
        String url = "https://dysmsapi.aliyuncs.com/?Signature=" + percentEncode(signature) + "&" + canonical;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("阿里云短信返回 HTTP " + code + "： " + resp.body());
        }
        JsonNode r = mapper.readTree(resp.body());
        String codeField = r.has("Code") ? r.get("Code").asText() : "";
        if (!"OK".equals(codeField)) {
            String msg = r.has("Message") ? r.get("Message").asText() : resp.body();
            throw new IllegalStateException("阿里云短信发送失败： " + msg);
        }
    }

    private String gmtNow() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneId.of("GMT"))
                .format(java.time.Instant.now());
    }

    private String percentEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return s;
        }
    }

    private String signHmacSha1(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private List<String> splitPhones(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("[,;\\s]+")).filter(p -> !p.isBlank()).toList();
    }

    private String text(JsonNode n, String k) { return text(n, k, null); }

    private String text(JsonNode n, String k, String dflt) {
        JsonNode v = n.get(k);
        return v != null && !v.isNull() ? v.asText() : dflt;
    }
}
