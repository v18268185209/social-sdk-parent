package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多账号环境隔离的会话池。
 *
 * <p>每个账号（由 {@code accountKey} 标识，通常是闲鱼昵称或 cookie tracknick 值）
 * 绑定一个独立的 {@link XianyuCdpSessionManager}，各自维护独立的 CdpClient + target，
 * 互不干扰。上层 SDK 门面通过 {@link #sessionOf(String)} 拿到对应账号的会话，
 * 再驱动该账号专属的 {@link XianyuCdpBot}/{@link XianyuPublishBot}/{@link XianyuTradeBot}。</p>
 *
 * <p>闲鱼登录态来自远程 Chrome 的 Cookie（tracknick/unb），同一 Chrome profile 下所有
 * 标签页共享 Cookie。因此「多账号」在此实现的语义是：同一 Chrome 实例下通过不同
 * 标签页承载不同账号的登录态切换——切换账号时关闭旧标签页、清 Cookie、重新扫码登录新账号，
 * 由 {@link XianyuAccountManager} 编排；本池只负责按 key 缓存会话实例与生命周期。</p>
 *
 * <p>本类无 Spring 注解，由上层（demo / starter）按需注入或实例化。线程安全。</p>
 */
public class XianyuCdpSessionPool {

    private final String endpoint;
    private final String xianyuUrl;
    private final int maxAccounts;

    private final Object lock = new Object();
    private final LinkedHashMap<String, XianyuCdpSessionManager> pool = new LinkedHashMap<>();

    /**
     * @param endpoint CDP 端点（如 {@code http://117.147.111.183:30004}）
     * @param xianyuUrl 闲鱼首页 URL
     * @param maxAccounts 最大账号数（LRU 上限，超出会淘汰最久未用的账号会话）
     */
    public XianyuCdpSessionPool(String endpoint, String xianyuUrl, int maxAccounts) {
        this.endpoint = (endpoint == null ? "http://192.168.1.127:9333" : endpoint.trim().replaceAll("/$", ""));
        this.xianyuUrl = (xianyuUrl == null ? "https://www.goofish.com" : xianyuUrl);
        this.maxAccounts = Math.max(1, maxAccounts);
    }

    /** 获取或创建指定账号的会话管理器。不存在则新建并放入池。 */
    public XianyuCdpSessionManager sessionOf(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalArgumentException("accountKey 不能为空");
        }
        synchronized (lock) {
            XianyuCdpSessionManager m = pool.get(accountKey);
            if (m != null) {
                // LRU：访问后移到末尾（最近使用）
                pool.remove(accountKey);
                pool.put(accountKey, m);
                return m;
            }
            m = new XianyuCdpSessionManager(endpoint, xianyuUrl);
            pool.put(accountKey, m);
            // 淘汰超出上限的最久未用账号
            while (pool.size() > maxAccounts) {
                String oldest = pool.keySet().iterator().next();
                XianyuCdpSessionManager evicted = pool.remove(oldest);
                try { evicted.destroy(); } catch (Exception ignore) {}
            }
            return m;
        }
    }

    /** 是否已存在该账号的会话。 */
    public boolean contains(String accountKey) {
        synchronized (lock) {
            return pool.containsKey(accountKey);
        }
    }

    /** 列出当前池中所有账号 key 与其连接状态。 */
    public Map<String, Boolean> listAccounts() {
        synchronized (lock) {
            Map<String, Boolean> out = new LinkedHashMap<>();
            for (Map.Entry<String, XianyuCdpSessionManager> e : pool.entrySet()) {
                out.put(e.getKey(), e.getValue().isConnected());
            }
            return out;
        }
    }

    /** 主动释放某账号会话（关闭标签页 + CdpClient）。 */
    public void release(String accountKey) {
        synchronized (lock) {
            XianyuCdpSessionManager m = pool.remove(accountKey);
            if (m != null) {
                try { m.destroy(); } catch (Exception ignore) {}
            }
        }
    }

    /** 释放所有账号会话（容器关闭时调用）。 */
    public void destroyAll() {
        synchronized (lock) {
            for (XianyuCdpSessionManager m : pool.values()) {
                try { m.destroy(); } catch (Exception ignore) {}
            }
            pool.clear();
        }
    }

    /** 获取某账号当前已建立的 CdpClient；未连接则建立。 */
    public CdpClient clientOf(String accountKey) throws Exception {
        return sessionOf(accountKey).getClient();
    }

    String getEndpoint() { return endpoint; }
    String getXianyuUrl() { return xianyuUrl; }
}
