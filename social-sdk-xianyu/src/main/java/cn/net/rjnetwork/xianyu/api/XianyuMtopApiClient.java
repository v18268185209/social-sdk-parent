package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 闲鱼 MTOP API 客户端
 * 通过 HTTP 直接调用 MTOP API，不需要浏览器
 */
public class XianyuMtopApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .proxy(ProxySelector.of(null))
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private String cookie;
    private final Map<String, String> headers;

    public XianyuMtopApiClient(String cookie) {
        this.cookie = cookie;
        this.headers = new HashMap<>();
        this.headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
        this.headers.put("Accept", "application/json");
        this.headers.put("Content-Type", "application/x-www-form-urlencoded");
        this.headers.put("Cookie", cookie);
    }

    /**
     * 发送 GET 请求
     */
    public JsonNode get(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpRequest request = builder.GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            System.err.println("[MTOP GET Error] " + e.getMessage());
            return null;
        }
    }

    /**
     * 发送 POST 请求
     */
    public JsonNode post(String url, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            System.err.println("[MTOP POST Error] " + e.getMessage());
            return null;
        }
    }

    /**
     * 发送 POST 请求（JSON body）
     */
    public JsonNode postJson(String url, String jsonBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return MAPPER.readTree(response.body());
        } catch (Exception e) {
            System.err.println("[MTOP POST JSON Error] " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前 Cookie
     */
    public String getCookie() {
        return cookie;
    }

    /**
     * 更新 Cookie
     */
    public void updateCookie(String newCookie) {
        this.cookie = newCookie;
        this.headers.put("Cookie", newCookie);
    }
}
