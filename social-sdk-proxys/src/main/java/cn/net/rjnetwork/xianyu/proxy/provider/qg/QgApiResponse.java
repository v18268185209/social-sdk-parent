package cn.net.rjnetwork.xianyu.proxy.provider.qg;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * QG 网络 API 响应结构。
 *
 * <p>域：share.proxy.qg.net (短效代理) / overseas.proxy.qg.net (全球HTTP)</p>
 *
 * <h3>示例响应</h3>
 * <pre>{@code
 * {
 *   "code": "SUCCESS",
 *   "data": [
 *     {
 *       "proxy_ip": "123.54.55.24",
 *       "server": "123.54.55.24:59419",
 *       "area": "河南省商丘市",
 *       "isp": "电信",
 *       "deadline": "2023-02-25 15:38:36"
 *     }
 *   ],
 *   "request_id": "83158ebe-..."
 * }
 * }</pre>
 *
 * <p>注意：只有 {@code server}（ip:port）才是代理地址要用的，{@code proxy_ip} 是出口 IP（展示用）。</p>
 */
@Data
public class QgApiResponse<T> {

    /** 请求状态码：SUCCESS 为成功 */
    private String code;

    /** 数据（提取 IP 时为数组，单个查询时为对象） */
    private T data;

    /** 唯一请求 ID（排查用） */
    @JsonProperty("request_id")
    private String requestId;

    /** 失败描述（部分错误会在消息里带原因） */
    private String msg;

    /** 是否成功 */
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(code);
    }
}
