package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;
import java.util.List;

@Data
public class LocalProductBatchPublishRequest {
    private List<Long> ids;
    /** 指定发布账号，不填则按各商品自己的 accountId */
    private Long accountId;
    /** 是否允许部分成功：true=成功的删、失败的留；false=全部成功才算 */
    private boolean partialSuccess = true;
    /** 最大并发数（保护闲鱼风控） */
    private int maxConcurrency = 3;
    /** 账号间发布间隔 ms（降低风控风险） */
    private long delayMs = 2000;
    /** 失败时重试次数 */
    private int retryTimes = 0;
}
