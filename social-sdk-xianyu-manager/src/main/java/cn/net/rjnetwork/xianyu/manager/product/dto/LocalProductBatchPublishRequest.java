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
    /** 失败时重试次数（0=不重试，1~3=最多重试 N 次） */
    private int retryTimes = 0;
    /** 重试间隔基数 ms（指数退避：第1次*1，第2次*2，第3次*4） */
    private long retryBackoffBaseMs = 3000;
    /** 失败时是否记录到 publish_log（独立日志表） */
    private boolean logFailDetail = true;
}
