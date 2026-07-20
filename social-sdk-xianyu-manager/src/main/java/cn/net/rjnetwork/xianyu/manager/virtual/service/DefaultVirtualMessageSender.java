^package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * VirtualMessageSender 的默认实现（占位）。
 * TODO: 接入真实闲鱼 IM 发送（通过 XianyuApiFacade.sendMessage 按 accountId + 买家 session 推送发货内容）。
 * 当前仅记录日志并返回成功，用于保证应用可启动；虚拟发货主流程不依赖真实发送结果。
 */
@Component
public class DefaultVirtualMessageSender implements VirtualMessageSender {

    private static final Logger log = LoggerFactory.getLogger(DefaultVirtualMessageSender.class);

    @Override
    public boolean sendToBuyer(XianyuOrder order, String content) {
        log.info("[VirtualShip] 向买家发送发货内容 orderId={} accountId={} content={}",
                order.getOrderId(), order.getAccountId(), content);
        return true;
    }
}
