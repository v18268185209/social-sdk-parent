package cn.net.rjnetwork.xianyu.proxy.provider.qg;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单个 QG 代理 IP 条目（{@link QgApiResponse} 返回列表的单个元素）。
 *
 * <p>{@code server}（ip:port）才是要填进代理配置里的地址；
 * {@code proxy_ip} 是出口 IP 的真实地址（仅展示用）。</p>
 *
 * <p>{@code deadline} 是 IP 的存活截止时间，调用方必须在此时间之前完成请求。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QgProxyItem {

    /** 真实出口 IP（展示用，不要填到代理 host 里） */
    @JsonProperty("proxy_ip")
    private String proxyIp;

    /** 代理地址（ip:port），这才是代理配置要用的 */
    private String server;

    /** IP 所在地区（如 "河南省商丘市" / "上海"） */
    private String area;

    /** 运营商（如 "电信" / "联通" / "移动"） */
    private String isp;

    /** IP 存活截止时间 */
    @JsonProperty("deadline")
    private String deadlineRaw;

    /**
     * 解析 {@code deadline} 字符串为 LocalDateTime，解析失败返回 null。
     */
    public LocalDateTime getDeadline() {
        if (deadlineRaw == null || deadlineRaw.isBlank()) return null;
        try {
            return LocalDateTime.parse(deadlineRaw.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 server 字段解析 host。
     */
    public String getHost() {
        if (server == null || server.isBlank()) return null;
        int colon = server.lastIndexOf(':');
        return colon > 0 ? server.substring(0, colon) : server;
    }

    /**
     * 从 server 字段解析 port。
     */
    public int getPort() {
        if (server == null || server.isBlank()) return 0;
        int colon = server.lastIndexOf(':');
        if (colon < 0 || colon == server.length() - 1) return 0;
        try {
            return Integer.parseInt(server.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
