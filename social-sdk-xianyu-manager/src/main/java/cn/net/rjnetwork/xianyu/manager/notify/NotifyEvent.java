^package cn.net.rjnetwork.xianyu.manager.notify;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知事件。两种构造：
 * 1) 富事件（业务发布者使用）：(scenario, accountId, accountName, vars)
 * 2) 兼容旧事件（如消息监听器）：(source, payload)
 * scenario 同时作为 type 暴露，便于既有监听器按 type 判断。
 */
public class NotifyEvent extends ApplicationEvent {

    private final String scenario;
    private final Long accountId;
    private final String accountName;
    private final Map<String, Object> vars;
    private final Object payload;
    private final LocalDateTime occurredAt;

    /** 富事件构造（账号/订单/钱包/消息等场景发布者使用） */
    public NotifyEvent(String scenario, Long accountId, String accountName, Map<String, Object> vars) {
        super(scenario);
        this.scenario = scenario;
        this.accountId = accountId;
        this.accountName = accountName;
        this.vars = vars != null ? vars : new HashMap<>();
        this.payload = null;
        this.occurredAt = LocalDateTime.now();
    }

    /** 兼容构造（仅携带 payload，无场景） */
    public NotifyEvent(Object source, Object payload) {
        super(source);
        this.scenario = null;
        this.accountId = null;
        this.accountName = null;
        this.vars = new HashMap<>();
        this.payload = payload;
        this.occurredAt = LocalDateTime.now();
    }

    /** 兼容构造（source, payload, type） */
    public NotifyEvent(Object source, Object payload, String type) {
        super(source);
        this.scenario = type;
        this.accountId = null;
        this.accountName = null;
        this.vars = new HashMap<>();
        this.payload = payload;
        this.occurredAt = LocalDateTime.now();
    }

    public String getScenario() { return scenario; }
    public Long getAccountId() { return accountId; }
    public String getAccountName() { return accountName; }
    public Map<String, Object> getVars() { return vars; }
    public Object getPayload() { return payload; }
    /** 兼容监听器按 type 判断（与 scenario 同义） */
    public String getType() { return scenario; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
