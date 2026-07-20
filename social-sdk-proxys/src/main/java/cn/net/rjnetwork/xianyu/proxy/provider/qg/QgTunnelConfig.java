package cn.net.rjnetwork.xianyu.proxy.provider.qg;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QG 网络 — 隧道代理 配置项。对应 application.yml 中 {@code proxy.providers.qg.tunnel:}。
 *
 * <p>隧道代理是 QG 提供的云端自动切 IP 服务：你只需要告诉它代理入口 URL，云端自动把请求路由到不同出口 IP。
 * 支持 3 种鉴权方式：白名单 / 账号密码 / 账密 + 参数绑定地区。</p>
 *
 * <h3>application.yml 示例</h3>
 * <pre>{@code
 * proxy:
 *   providers:
 *     qg:
 *       enabled: true
 *       apiKey: xxxxxx               # 产品唯一标识 key（隧道代理 / 短效代理共用）
 *       authKey: your_api_user        # 账密鉴权的用户名（可为空，用白名单时可空）
 *       authPwd: your_api_pass        # 账密鉴权的密码
 *       plan: short_lived             # short_lived / tunnel / long_term，决定调用哪个 QG 域名
 *       tunnel:
 *         enabled: true
 *         host: tun-szbhry.qg.net     # 隧道服务器地址
 *         port: 25889
 *         areaCode: "110100,310100"   # 可选：指定地区编码（-areaCode=-A-110100）
 *         keepAliveSec: 60            # 可选：保持同一 IP 的秒数（-T-60），账号密码模式可用
 *         protocol: http              # http 或 socks5（客户端用）
 * }</pre>
 *
 * <h3>隧道代理的核心能力</h3>
 * <ul>
 *   <li>云端自动切 IP：每个请求自动路由到不同出口 IP，无需手动提取</li>
 *   <li>-A- 参数指定省份：{@code ?area=110100} 或拼在账密里</li>
 *   <li>-T- 参数保持 IP 不变：多个请求相同 session 时用（如登录流程）</li>
 *   <li>白名单鉴权：主机外网 IP 加入白名单即可，无需账密</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "proxy.providers.qg.tunnel")
public class QgTunnelConfig {

    /** 是否启用隧道代理 */
    private boolean enabled = false;

    /** 隧道服务器地址（域名或 IP） */
    private String host;

    /** 隧道服务器端口 */
    private int port;

    /** 账密鉴权 — 用户名 */
    private String authKey;

    /** 账密鉴权 — 密码 */
    private String authPwd;

    /**
     * 指定提取 IP 的地区编码（多个用逗号分隔，最多 5 个）。
     * 示例：{@code "110100"} = 北京，{@code "310100"} = 上海。
     */
    private String areaCode;

    /**
     * 保持同一出口 IP 的时长（秒），隧道代理专用。
     * 用于需要多请求保持同一 session 的场景（如登录 + 后续请求）。
     */
    private int keepAliveSec;

    /** 代理协议：http 或 socks5（客户端构建 HttpClient 时用） */
    private String protocol = "http";

    /**
     * 判断是否配置了账密鉴权（authKey + authPwd 都非空）。
     */
    public boolean hasAuth() {
        return authKey != null && !authKey.isBlank() && authPwd != null && !authPwd.isBlank();
    }

    /**
     * 获取完整代理 URI（不含协议头），如 {@code "user:pass@host:port"} 或 {@code "host:port"}。
     */
    public String toAuthority() {
        if (hasAuth()) {
            return authKey + ":" + authPwd + "@" + host + ":" + port;
        }
        return host + ":" + port;
    }
}
