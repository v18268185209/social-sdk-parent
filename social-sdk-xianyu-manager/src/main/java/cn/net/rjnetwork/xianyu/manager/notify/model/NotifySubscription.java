package cn.net.rjnetwork.xianyu.manager.notify.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订阅规则：场景 -> 通道 + 接收范围。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_subscription")
public class NotifySubscription extends BaseEntity {

    private String scenario;       // 见 NotifyScenario
    private Long channelId;
    private String recipientScope = "ALL"; // ALL / CUSTOM
    private String recipients;     // CUSTOM 时：收件人（逗号分隔或 JSON 数组）
    private String accountScope = "ALL";   // ALL / CUSTOM
    private String accountIds;     // CUSTOM 时：账号 ID 列表（JSON 数组）
    private Boolean enabled = true;
}
