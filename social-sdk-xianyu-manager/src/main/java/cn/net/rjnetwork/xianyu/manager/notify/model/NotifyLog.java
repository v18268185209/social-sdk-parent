^package cn.net.rjnetwork.xianyu.manager.notify.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知投递日志（状态 / 重试 / 错误，可查）。
 */
@Data
@TableName("notify_log")
public class NotifyLog {

    private Long id;
    private String scenario;
    private Long channelId;
    private String channelType;
    private String recipient;
    private String status;     // SENT / FAILED
    private String payload;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
