package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 管理与用户远程 Chrome（CDP 端点）之间的「闲鱼专属」会话。
 *
 * <p>通过 {@link CdpClient} 直连 CDP 端点，在远程浏览器中创建独立的闲鱼标签页。
 * 该标签页与用户浏览器共用同一套 Cookie，因此只要用户在浏览器里登录了闲鱼，
 * 此标签页也处于登录态。所有 {@link XianyuCdpBot} 用例基于此会话执行。</p>
 *
 * <p>本类无 Spring 注解，由上层（demo / starter）按需注入或实例化。</p>
 */
public class XianyuCdpSessionManager {

    private final String endpoint;
    private final String xianyuUrl;

    private final Object lock = new Object();
    private volatile CdpClient client;
    private volatile String targetId;
    private volatile boolean connected;

    public XianyuCdpSessionManager(String endpoint, String xianyuUrl) {
        this.endpoint = (endpoint == null ? "http://192.168.1.127:9333" : endpoint.trim().replaceAll("/$", ""));
        this.xianyuUrl = (xianyuUrl == null ? "https://www.goofish.com" : xianyuUrl);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getXianyuUrl() {
        return xianyuUrl;
    }

    /** 建立（或复用）到远程 Chrome 的 CDP 会话。 */
    public CdpClient connect() throws Exception {
        synchronized (lock) {
            if (connected && client != null) {
                return client;
            }
            CdpClient c = CdpClient.attachRemote(endpoint);
            String tid = c.createTarget(xianyuUrl);
            String sid = c.attachTarget(tid);
            c.setSessionId(sid);
            c.activateTarget(tid);
            this.client = c;
            this.targetId = tid;
            this.connected = true;
            return c;
        }
    }

    /** 获取客户端；若未连接则自动连接。 */
    public CdpClient getClient() throws Exception {
        if (!connected || client == null) {
            return connect();
        }
        try {
            HttpClient http = httpClient();
            String url = endpoint.replaceAll("/$", "") + "/json/list";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(5)).build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode list = new ObjectMapper().readTree(body);
            boolean alive = false;
            for (JsonNode t : list) {
                if (targetId != null && targetId.equals(t.path("id").asText(""))) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                reconnect();
            }
        } catch (Exception ignore) {
            // 检测失败不阻塞，留给后续 evaluate 报错时再重试
        }
        return client;
    }

    public boolean isConnected() {
        return connected && client != null;
    }

    /**
     * 关闭旧 target 并新建一个（同一 browser 连接）。
     * 避免远程 Chrome 后台 target 越久越被节流冻结（evaluate 卡死 60s）。
     */
    public void freshTarget() throws Exception {
        synchronized (lock) {
            if (client == null) {
                connect();
                return;
            }
            if (targetId != null) {
                try {
                    HttpClient http = httpClient();
                    String url = endpoint.replaceAll("/$", "") + "/json/close/" + targetId;
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET()
                            .timeout(Duration.ofSeconds(3)).build();
                    http.send(req, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignore) {
                    // 后台标签卡住也继续
                }
            }
            String tid = client.createTarget(xianyuUrl);
            String sid = client.attachTarget(tid);
            client.setSessionId(sid);
            client.activateTarget(tid);
            this.targetId = tid;
        }
    }

    public void reconnect() throws Exception {
        synchronized (lock) {
            closeQuietly();
            connect();
        }
    }

    /** 释放连接（上层在容器关闭时调用）。 */
    public void destroy() {
        closeQuietly();
    }

    private void closeQuietly() {
        connected = false;
        if (client != null) {
            if (targetId != null) {
                try {
                    client.closeTarget(targetId).get(3, TimeUnit.SECONDS);
                } catch (Exception ignore) {
                    // 后台标签卡住也继续
                }
            }
            try {
                client.close();
            } catch (Exception ignore) {
                // 忽略
            }
        }
        client = null;
        targetId = null;
    }

    /**
     * 发现远端浏览器中已有的 goofish/taobao 标签页，返回已 attach + activate 的 CdpClient。
     * 若未找到则新建一个闲鱼标签页。用于扫码登录场景。
     */
    public CdpClient acquireLoginTarget() throws Exception {
        CdpClient c = CdpClient.attachRemote(endpoint);
        try {
            String listUrl = endpoint.replaceAll("/$", "") + "/json/list";
            HttpRequest req = HttpRequest.newBuilder(URI.create(listUrl)).GET()
                    .timeout(Duration.ofSeconds(5)).build();
            String body = httpClient().send(req, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode list = new ObjectMapper().readTree(body);
            String targetId = null;
            String fallbackTargetId = null;
            for (JsonNode t : list) {
                if (!"page".equals(t.get("type").asText())) continue;
                String url = t.path("url").asText("");
                if (url.startsWith("chrome://") || url.isEmpty()) continue;
                if (fallbackTargetId == null) fallbackTargetId = t.get("id").asText();
                if (url.contains("goofish.com") || url.contains("passport.goofish.com")
                        || url.contains("taobao.com") || url.contains("alibaba.com")) {
                    targetId = t.get("id").asText();
                    break;
                }
            }
            if (targetId == null) {
                targetId = c.createTarget(xianyuUrl);
            }
            String sid = c.attachTarget(targetId);
            c.setSessionId(sid);
            c.activateTarget(targetId);
            return c;
        } catch (Exception e) {
            try { c.close(); } catch (Exception ignore) {}
            throw e;
        }
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();
    }
}
