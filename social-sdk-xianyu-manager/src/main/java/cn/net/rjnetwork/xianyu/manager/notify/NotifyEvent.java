package cn.net.rjnetwork.xianyu.manager.notify;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知事件。由业务 Service 通过 ApplicationEventPublisher 发布，
 * NotificationService 监听并编排分发。
 */
public class NotifyEvent {

    private final String scenario;          // NotifyScenario.name()
    private final Long accountId;
    private final String accountName;
    private final Map<String, Object> vars; // 模板变量
    private final LocalDateTime occurredAt;

    public NotifyEvent(String scenario, Long accountId, String accountName, Map<String, Object> vars) {
        this.scenario = scenario;
        this.accountId = accountId;
        this.accountName = accountName;
        this.vars = vars != null ? vars : Map.of();
        this.occurredAt = LocalDateTime.now();
    }

    public String getScenario() { return scenario; }
    public Long getAccountId() { return accountId; }
    public String getAccountName() { return accountName; }
    public Map<String, Object> getVars() { return vars; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
