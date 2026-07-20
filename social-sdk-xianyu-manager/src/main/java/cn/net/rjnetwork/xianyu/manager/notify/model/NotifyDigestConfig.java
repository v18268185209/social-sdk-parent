^package cn.net.rjnetwork.xianyu.manager.notify.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 每日摘要配置（单例，固定 id=1）。每天定时聚合站内通知，按场景汇总后经配置通道发送。
 */
@Data
@TableName("notify_digest_config")
public class NotifyDigestConfig {

    private Long id = 1L;
    private Boolean enabled = false;
    private Long channelId;          // 发送通道（EMAIL/SMS）
    private String recipients;       // 逗号分隔的接收人（邮箱/手机号）
    private Integer hour = 9;        // 触发小时
    private Integer minute = 0;     // 触发分钟
    private String scenarios;        // JSON 数组，限定纳入摘要的场景；空=全部
    private Boolean includeInApp = true; // 是否在站内收件箱留痕
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
