package cn.net.rjnetwork.xianyu.proxy.util;

import cn.net.rjnetwork.xianyu.proxy.config.ProxyInfo;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 通用 HTTP 客户端工具。基于 OkHttp，支持代理、超时、重试。
 */
public final class HttpUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);

    private static final OkHttpClient BASE_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .writeTimeout(Duration.ofSeconds(10))
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private HttpUtils() {}

    /**
     * 发送 GET 请求，返回响应体字符串。
     *
     * @param url 请求 URL
     * @return 响应体
     */
    public static String get(String url) {
        return get(url, null, null);
    }

    /**
     * 发送 GET 请求，带自定义 header。
     */
    public static String get(String url, java.util.Map<String, String> headers) {
        return get(url, headers, null);
    }

    /**
     * 发送 GET 请求，带代理。
     */
    public static String get(String url, java.util.Map<String, String> headers, ProxyInfo proxy) {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        return execute(builder.build(), proxy);
    }

    /**
     * 发送 POST JSON 请求。
     */
    public static String postJson(String url, String json) {
        return postJson(url, json, null, null);
    }

    /**
     * 发送 POST JSON 请求，带代理。
     */
    public static String postJson(String url, String json, java.util.Map<String, String> headers, ProxyInfo proxy) {
        RequestBody body = RequestBody.create(json != null ? json : "", MediaType.parse("application/json"));
        Request.Builder builder = new Request.Builder().url(url).post(body);
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        return execute(builder.build(), proxy);
    }

    /**
     * 发送 POST 表单请求。
     */
    public static String postForm(String url, java.util.Map<String, String> params) {
        return postForm(url, params, null, null);
    }

    /**
     * 发送 POST 表单请求，带代理。
     */
    public static String postForm(String url, java.util.Map<String, String> params, java.util.Map<String, String> headers, ProxyInfo proxy) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        if (params != null) {
            params.forEach(formBuilder::add);
        }
        Request.Builder builder = new Request.Builder().url(url).post(formBuilder.build());
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        return execute(builder.build(), proxy);
    }

    /**
     * 执行请求，自动处理代理和异常。
     */
    public static String execute(Request request, ProxyInfo proxy) {
        OkHttpClient client = buildClient(proxy);
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - start;
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                log.warn("[HTTP] {} {} -> {} ({}ms), body: {}", request.method(), request.url(), response.code(), elapsed, body.length() > 200 ? body.substring(0, 200) : body);
                throw new ProxyException("HTTP_" + response.code(), "HTTP 请求失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "";
            log.debug("[HTTP] {} {} -> {} ({}ms)", request.method(), request.url(), response.code(), elapsed);
            return body;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[HTTP] {} {} -> IO_ERROR ({}ms): {}", request.method(), request.url(), elapsed, e.getMessage());
            throw new ProxyException("HTTP_IO_ERROR", "HTTP 请求 IO 异常: " + e.getMessage(), e, null, true);
        }
    }

    /**
     * 构建带代理的 OkHttp 客户端。
     */
    public static OkHttpClient buildClient(ProxyInfo proxy) {
        if (proxy == null) {
            return BASE_CLIENT;
        }
        OkHttpClient.Builder builder = BASE_CLIENT.newBuilder();
        builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        if (proxy.getUsername() != null && !proxy.getUsername().isBlank()) {
            builder.proxyAuthenticator((route, response) -> {
                String credential = Credentials.basic(proxy.getUsername(), proxy.getPassword());
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            });
        }
        return builder.build();
    }

    /**
     * 获取基础客户端（无代理）。
     */
    public static OkHttpClient getBaseClient() {
        return BASE_CLIENT;
    }
}
