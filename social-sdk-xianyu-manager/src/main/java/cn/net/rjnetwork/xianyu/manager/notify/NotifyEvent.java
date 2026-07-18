package cn.net.rjnetwork.xianyu.manager.notify;

import org.springframework.context.ApplicationEvent;

public class NotifyEvent extends ApplicationEvent {

    private final Object payload;
    private final String type;

    public NotifyEvent(Object source, Object payload, String type) {
        super(source);
        this.payload = payload;
        this.type = type;
    }

    public Object getPayload() { return payload; }
    public String getType() { return type; }
}
