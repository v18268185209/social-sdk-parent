^package cn.net.rjnetwork.xianyu.manager.notify.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发送重试队列。发送失败或触发限频时入队，由 RetryService 定时退避重发。
 */
@Data
@TableName("notify_retry")
public class NotifyRetry {

    private Long id;
    private String scenario;
    private Long channelId;
    private String channelType;
    private String recipient;
    private String title;
    private String body;
    private String varsJson;            // 触发事件的模板变量 JSON（用于重试时结构化重发）
    private Integer retryCount = 0;     // 已重试次数
    private Integer maxRetry = 5;       // 最大重试次数
    private LocalDateTime nextRetryAt;  // 下次重试时间
    private String status;              // PENDING / SENDING / DONE / GIVEN_UP
    private String lastError;
    private LocalDateTime createdAt;
}
