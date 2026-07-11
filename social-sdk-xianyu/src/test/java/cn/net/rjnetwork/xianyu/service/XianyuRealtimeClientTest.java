package cn.net.rjnetwork.xianyu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.net.rjnetwork.xianyu.config.XianyuConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XianyuRealtimeClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private XianyuRealtimeClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void shouldBuildTextMessageEnvelopeWithoutDuplicatingGoofishSuffix() throws Exception {
        client = newClient("seller@goofish");

        Map<String, Object> envelope = client.buildTextMessageEnvelope("123@goofish", "buyer", "hello \"闲鱼\"");
        JsonNode root = objectMapper.readTree(client.toJson(envelope));
        JsonNode message = root.path("body").get(0);
        JsonNode receiverScope = root.path("body").get(1);
        String encoded = message.path("content").path("custom").path("data").asText();
        JsonNode decoded = objectMapper.readTree(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));

        assertEquals("/r/MessageSend/sendByReceiverScope", root.path("lwp").asText());
        assertEquals("123@goofish", message.path("cid").asText());
        assertEquals(101, message.path("content").path("contentType").asInt());
        assertEquals("buyer@goofish", receiverScope.path("actualReceivers").get(0).asText());
        assertEquals("seller@goofish", receiverScope.path("actualReceivers").get(1).asText());
        assertEquals(1, decoded.path("contentType").asInt());
        assertEquals("hello \"闲鱼\"", decoded.path("text").path("text").asText());
    }

    @Test
    void shouldBuildImageMessageEnvelopeWithSafeDimensions() throws Exception {
        client = newClient("seller");

        Map<String, Object> envelope = client.buildImageMessageEnvelope("456", "buyer@goofish", "https://img.example/a.jpg", 0, -1);
        JsonNode root = objectMapper.readTree(client.toJson(envelope));
        JsonNode message = root.path("body").get(0);
        String encoded = message.path("content").path("custom").path("data").asText();
        JsonNode decoded = objectMapper.readTree(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
        JsonNode pic = decoded.path("image").path("pics").get(0);

        assertEquals("456@goofish", message.path("cid").asText());
        assertEquals("buyer@goofish", root.path("body").get(1).path("actualReceivers").get(0).asText());
        assertEquals("seller@goofish", root.path("body").get(1).path("actualReceivers").get(1).asText());
        assertEquals(2, decoded.path("contentType").asInt());
        assertEquals("https://img.example/a.jpg", pic.path("url").asText());
        assertEquals(1, pic.path("width").asInt());
        assertEquals(1, pic.path("height").asInt());
    }

    @Test
    void shouldNormalizeIdsAndCookies() {
        client = newClient("seller");
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("_m_h5_tk", "token_123456");
        cookies.put("cookie2", "abc");

        assertEquals("buyer@goofish", client.toGoofishId("buyer"));
        assertEquals("buyer@goofish", client.toGoofishId("buyer@goofish"));
        assertEquals("buyer", client.normalizeChatId("buyer@goofish"));
        assertEquals("token", client.extractTokenFromCookie(cookies.get("_m_h5_tk")));
        assertEquals("_m_h5_tk=token_123456; cookie2=abc", client.buildCookieHeader(cookies));
    }

    @Test
    void shouldDecodeBase64JsonSyncMessage() {
        client = newClient("seller");
        String rawJson = "{\"1\":{\"2\":\"chat@goofish\",\"5\":123456,\"10\":{\"senderUserId\":\"buyer\",\"text\":{\"text\":\"hi\"}}}}";
        String encoded = Base64.getEncoder().encodeToString(rawJson.getBytes(StandardCharsets.UTF_8));

        JsonNode decoded = client.decodeSyncMessageNode(encoded);

        assertNotNull(decoded);
        assertEquals("chat@goofish", decoded.path("1").path("2").asText());
        assertEquals("hi", decoded.path("1").path("10").path("text").path("text").asText());
    }

    private XianyuRealtimeClient newClient(String myUserId) {
        return new XianyuRealtimeClient(new XianyuConfig(), Map.of("_m_h5_tk", "token_123"), myUserId, message -> {
        });
    }
}
