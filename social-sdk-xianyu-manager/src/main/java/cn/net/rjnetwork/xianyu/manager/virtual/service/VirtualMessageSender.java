^package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;

/**
 * 虚拟商品发货消息发送器（真实场景接入闲鱼 SDK 发消息）
 */
public interface VirtualMessageSender {

    /**
     * 给买家发送发货内容
     * @param order 订单
     * @param content 发货内容（卡密/链接/文件）
     * @return 是否发送成功
     */
    boolean sendToBuyer(XianyuOrder order, String content);
}
