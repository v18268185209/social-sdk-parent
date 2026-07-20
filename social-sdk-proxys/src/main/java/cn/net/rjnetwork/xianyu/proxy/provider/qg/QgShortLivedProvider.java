package cn.net.rjnetwork.xianyu.proxy.provider.qg;

import cn.net.rjnetwork.xianyu.proxy.config.*;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyAcquireRequest;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyException;
import cn.net.rjnetwork.xianyu.proxy.core.ProxyProvider;
import cn.net.rjnetwork.xianyu.proxy.util.HttpUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * QG 网络 短效代理 Provider（按量提取 / 弹性提取 / 均匀提取 / 通道提取）。
 *
 * <p>每次 acquire 调 QG API（share.proxy.qg.net/get）提取一个新鲜 IP，
 * SDK 拿到真实 ip:port 和存活截止时间，业务方在 deadline 之前用，过期自动废弃。</p>
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
 *       shortLived:
 *         enabled: true
 *         plan: extract_by_count
 *         areaCode: ""
 *         isp: 0
 *         distinct: true
 *         num: 1
 *         keepAliveSec: 60
 *         maxLatencyMs: 3000
 * }</pre>
 */
public class QgShortLivedProvider implements ProxyProvider {

    private static final Logger log = LoggerFactory.getLogger(QgShortLivedProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BALANCE_API = "https://share.proxy.qg.net/balance";

    private final String apiKey;
    private final ProxyProperties.QgShortLivedSubConfig shortLivedConfig;

    public QgShortLivedProvider(String apiKey, ProxyProperties.QgShortLivedSubConfig shortLivedConfig) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("QG apiKey 不能为 null");
        }
        if (shortLivedConfig == null) {
            throw new IllegalArgumentException("QgShortLivedSubConfig 不能为 null");
        }
        this.apiKey = apiKey;
        this.shortLivedConfig = shortLivedConfig;
    }

    @Override
    public ProxyInfo acquire(ProxyAcquireRequest request) throws ProxyException {
        String url = shortLivedConfig.buildExtractUrl(apiKey);
        log.info("[PROXY-QG-SHORT] 提取 IP, accountId={}", request.getAccountId());

        try {
            String resp = HttpUtils.get(url);
            if (resp == null || resp.isBlank()) {
                throw new ProxyException("QG_SHORT_HTTP_ERROR",
                        "QG 短效代理 API 响应为空", null, ProviderType.QG, true);
            }

            QgApiResponse<List<QgProxyItem>> parsed = MAPPER.readValue(resp,
                    new TypeReference<QgApiResponse<List<QgProxyItem>>>() {});

            if (!parsed.isSuccess()) {
                throw new ProxyException(
                        "QG_SHORT_API_ERROR",
                        "QG 短效代理 API 返回错误: " + parsed.getCode() + " " + (parsed.getMsg() != null ? parsed.getMsg() : ""),
                        null, ProviderType.QG, true);
            }

            List<QgProxyItem> items = parsed.getData();
            if (items == null || items.isEmpty()) {
                throw new ProxyException("QG_SHORT_NO_IP",
                        "QG 短效代理未获取到可用 IP", null, ProviderType.QG, true);
            }

            QgProxyItem item = items.get(0);
            String host = item.getHost();
            int port = item.getPort();
            if (host == null || host.isBlank() || port <= 0) {
                throw new ProxyException("QG_SHORT_BAD_IP",
                        "QG 短效代理返回 IP 格式异常: " + item.getServer(),
                        null, ProviderType.QG, true);
            }

            LocalDateTime deadline = item.getDeadline();

            log.info("[PROXY-QG-SHORT] 提取 IP 成功, accountId={}, host:port={}, deadline={}, area={}",
                    request.getAccountId(), host + ":" + port, deadline, item.getArea());

            return ProxyInfo.builder()
                    .providerType(ProviderType.QG)
                    .proxyType(ProxyType.RESIDENTIAL)
                    .host(host)
                    .port(port)
                    .username(null)
                    .password(null)
                    .exitIp(item.getProxyIp())
                    .city(item.getArea() != null ? item.getArea() : "")
                    .isp(item.getIsp())
                    .sessionType(SessionType.ROTATING)
                    .boundAccountId(request.getAccountId())
                    .acquiredAt(LocalDateTime.now())
                    .expireAt(deadline)
                    .captchaPassed(false)
                    .consecutiveFailures(0)
                    .metadata("{\"isp\":\"" + (item.getIsp() != null ? item.getIsp() : "") + "\"}")
                    .build();

        } catch (ProxyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PROXY-QG-SHORT] 提取 IP 异常, accountId={}", request.getAccountId(), e);
            throw new ProxyException("QG_SHORT_EXCEPTION",
                    "QG 短效代理异常: " + e.getMessage(),
                    e, ProviderType.QG, true);
        }
    }

    @Override
    public void release(ProxyInfo proxy) {
        log.debug("[PROXY-QG-SHORT] 释放短效代理（自然过期）, ip={}:{}", proxy.getHost(), proxy.getPort());
    }

    @Override
    public String queryBalance() {
        try {
            String url = BALANCE_API + "?key=" + apiKey;
            String resp = HttpUtils.get(url);
            if (resp == null) return "FAIL: 响应为空";

            QgApiResponse<?> parsed = MAPPER.readValue(resp, QgApiResponse.class);
            if (parsed.isSuccess()) {
                return parsed.getData() != null ? parsed.getData().toString() : "OK";
            }
            return "FAIL: " + parsed.getCode();
        } catch (Exception e) {
            return "FAIL: " + e.getMessage();
        }
    }
}
