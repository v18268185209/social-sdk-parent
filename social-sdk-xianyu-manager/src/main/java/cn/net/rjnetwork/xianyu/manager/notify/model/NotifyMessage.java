^package cn.net.rjnetwork.xianyu.manager.notify.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知收件箱（与外部通道并存的站内实时通知）。
 */
@Data
@TableName("notify_message")
public class NotifyMessage {

    private Long id;
    private Long accountId;   // 关联的闲鱼账号（可空）
    private String scenario;
    private String title;
    private String content;
    private Boolean isRead = false;
    private LocalDateTime createdAt;
}
