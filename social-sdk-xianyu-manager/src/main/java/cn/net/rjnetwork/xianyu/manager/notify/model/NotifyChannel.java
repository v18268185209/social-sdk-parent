package cn.net.rjnetwork.xianyu.manager.notify.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知通道：邮件(SMTP) / Webhook(企微·钉钉·飞书机器人)
 * config_json 为 AES 加密后的 JSON 字符串（敏感字段密文存储）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_channel")
public class NotifyChannel extends BaseEntity {

    private String type;     // EMAIL, WEBHOOK
    private String name;
    private Boolean enabled = true;
    private String configJson; // 加密存储
}
