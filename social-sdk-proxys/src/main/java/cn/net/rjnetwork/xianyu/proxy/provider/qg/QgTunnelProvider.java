package cn.net.rjnetwork.xianyu.proxy.provider.qg;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * QG 网络 隧道代理 Provider。
 *
 * <p>隧道代理的核心能力：QG 提供一个固定的入口 URL（如 tun-szbhry.qg.net:25889），
 * SDK 侧只需要记住这唯一一个 host:port，QG 云端自动把请求路由到不同出口 IP。
 * SDK 不需要关心出口 IP 的轮换。</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * proxy:
 *   providers:
 *     qg:
 *       enabled: true
 *       apiKey: xxxxxx
 *       authKey: your_user
 *       authPwd: your_pass
 *       tunnel:
 *         enabled: true
 *         host: tun-szbhry.qg.net
 *         port: 25889
 *         areaCode: "110100"
 *         keepAliveSec: 60
 * }</pre>
 */
public class QgTunnelProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(QgTunnelProvider.class);

    private final ProxyProperties.QgConfig qgConfig;

    public QgTunnelProvider(ProxyProperties.QgConfig qgConfig) {
        if (qgConfig == null) {
            throw new IllegalArgumentException("ProxyProperties.QgConfig 不能为 null");
        }
        this.qgConfig = qgConfig;
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) throws cn.net.rjnetwork.xianyu.proxy.core.ProxyException {
        ProxyProperties.QgSubConfig tunnel = qgConfig.getTunnel();
        if (tunnel == null || tunnel.getHost() == null || tunnel.getHost().isBlank()) {
            throw new cn.net.rjnetwork.xianyu.proxy.core.ProxyException(
                    "QG_TUNNEL_NOT_CONFIG", "隧道代理 host 未配置", null, ProviderType.QG, false);
        }

        // 支持通过 -T- 参数延长 IP 存活时间
        String computedAuthKey = qgConfig.getAuthKey();
        if (tunnel.getKeepAliveSec() > 0 && computedAuthKey != null) {
            if (!computedAuthKey.contains("-T-")) {
                computedAuthKey = computedAuthKey + "-T-" + tunnel.getKeepAliveSec();
            }
        }
        if (tunnel.getAreaCode() != null && !tunnel.getAreaCode().isBlank()
                && computedAuthKey != null && !computedAuthKey.contains("-A-")) {
            computedAuthKey = computedAuthKey + "-A-" + tunnel.getAreaCode();
        }

        log.info("[PROXY-QG-TUNNEL] 使用隧道代理, accountId={}, host:port={}:{}, authority={}",
                request.getAccountId(), tunnel.getHost(), tunnel.getPort(),
                (qgConfig.getAuthKey() != null && !qgConfig.getAuthKey().isBlank()
                        ? qgConfig.getAuthKey() + ":***@" + tunnel.getHost() + ":" + tunnel.getPort()
                        : tunnel.getHost() + ":" + tunnel.getPort()));

        return ProxyInfo.builder()
                .providerType(ProviderType.QG)
                .proxyType(ProxyType.RESIDENTIAL)
                .host(tunnel.getHost())
                .port(tunnel.getPort())
                .username(computedAuthKey)
                .password(qgConfig.getAuthPwd())
                .sessionType(SessionType.ROTATING)
                .city(tunnel.getAreaCode() != null ? tunnel.getAreaCode() : "全国")
                .acquiredAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusSeconds(
                        tunnel.getKeepAliveSec() > 0 ? tunnel.getKeepAliveSec() : 300))
                .captchaPassed(false)
                .consecutiveFailures(0)
                .build();
    }

    @Override
    public void release(ProxyInfo proxy) {
        log.debug("[PROXY-QG-TUNNEL] 释放隧道代理（无操作）");
    }

    @Override
    public String queryBalance() {
        return "隧道代理按请求计费，余额请到 QG 后台查询";
    }
}
