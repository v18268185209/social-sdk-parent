^package cn.net.rjnetwork.xianyu.manager.notify.adapter;

import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;

import java.util.List;
import java.util.Map;

/**
 * 通道适配器。每个实现对应一种通道类型（EMAIL / WEBHOOK / SMS）。
 * 由 NotificationService 调用，负责把渲染后的标题/正文通过具体通道发出。
 */
public interface ChannelAdapter {

    /** 通道类型，对应 NotifyChannel.type */
    String type();

    /**
     * 发送通知
     * @param channel     已解密 config_json 的通道（configJson 为明文 JSON）
     * @param title       渲染后的标题
     * @param body        渲染后的正文（markdown 优先）
     * @param recipients  接收人列表（EMAIL 为邮箱地址；SMS 为手机号；WEBHOOK 通常忽略）
     * @param vars        触发事件的模板变量（如 accountName/amount/orderId…），供通道做结构化映射
     */
    void send(NotifyChannel channel, String title, String body, List<String> recipients, Map<String, Object> vars) throws Exception;
}
