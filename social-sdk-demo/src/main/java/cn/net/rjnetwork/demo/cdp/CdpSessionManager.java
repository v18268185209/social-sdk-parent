package cn.net.rjnetwork.demo.cdp;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 管理与用户 Chrome（CDP 端点）之间的「闲鱼专属」会话。
 *
 * <p>通过 {@link CdpClient} 直连 CDP 端点（默认 http://192.168.1.127:9333），
 * 在远程浏览器中创建一个独立的闲鱼标签页（target）。该标签页与用户浏览器共用
 * 同一套 Cookie，因此只要用户在浏览器里登录了闲鱼，这个标签页也就处于登录态。</p>
 *
 * <p>所有闲鱼操作用例 {@link XianyuCdpBot} 都基于此会话执行，本类只负责连接的
 * 建立、复用与释放。</p>
 */
@Service
public class CdpSessionManager {

    private final String endpoint;
    private final String xianyuUrl;

    private final Object lock = new Object();
    private volatile CdpClient client;
    private volatile String targetId;
    private volatile boolean connected;

    public CdpSessionManager(
            @Value("${cdp.endpoint:http://192.168.1.127:9333}") String endpoint,
            @Value("${cdp.xianyu-url:https://www.goofish.com}") String xianyuUrl) {
        this.endpoint = (endpoint == null ? "http://192.168.1.127:9333" : endpoint.trim().replaceAll("/$", ""));
        this.xianyuUrl = (xianyuUrl == null ? "https://www.goofish.com" : xianyuUrl);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getXianyuUrl() {
        return xianyuUrl;
    }

    /** 建立（或复用）到远程 Chrome 的 CDP 会话，返回可用于驱动的客户端。 */
    public CdpClient connect() throws Exception {
        synchronized (lock) {
            if (connected && client != null) {
                return client;
            }
            CdpClient c = CdpClient.attachRemote(endpoint);
            String tid = c.createTarget(xianyuUrl);
            String sid = c.attachTarget(tid);
            c.setSessionId(sid);
            c.activateTarget(tid);  // 拉到前台，避免 Chrome 在后台节流冻结
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
        return client;
    }

    public boolean isConnected() {
        return connected && client != null;
    }

    /**
     * 关闭旧 target 并新建一个（同一 browser 连接）。
     * 目的：远程 Chrome 后台 target 越久越被节流冻结（evaluate 卡死 60s），
     * 新建 target 短暂在前台活跃，保证 navigate + evaluate + 提取二维码快速完成。
     */
    public void freshTarget() throws Exception {
        synchronized (lock) {
            if (client == null) {
                connect();
                return;
            }
            // 关闭旧 target 并新建一个（同一 browser 连接）。
            // remote Chrome 后台 target 越久越被节流冻结（evaluate 卡死 60s），
            // 关闭旧 target + 新建 target 短暂在前台活跃，保证秒出码。
            if (targetId != null) {
                // 用 HTTP API 关 target（比 CDP Target.closeTarget 更可靠，不会卡死）
                try {
                    java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                            .proxy(java.net.ProxySelector.of(null)).build();
                    String url = endpoint.replaceAll("/$", "") + "/json/close/" + targetId;
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url)).GET()
                            .timeout(java.time.Duration.ofSeconds(3)).build();
                    http.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignore) {
                    // 后台标签卡住也继续
                }
            }
            // 新建 target 并导航
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

    @PreDestroy
    public void destroy() {
        closeQuietly();
    }

    private void closeQuietly() {
        connected = false;
        if (client != null) {
            if (targetId != null) {
                try {
                    // 加 3s 超时：后台标签的 closeTarget 可能卡死，不能无限等
                    client.closeTarget(targetId).get(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignore) {
                    // 后台标签卡住也继续，不阻塞
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
}
