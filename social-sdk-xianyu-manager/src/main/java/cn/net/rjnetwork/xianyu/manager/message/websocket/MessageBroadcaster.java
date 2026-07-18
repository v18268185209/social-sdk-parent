package cn.net.rjnetwork.xianyu.manager.message.websocket;

import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息广播器
 * 通过 SimpMessagingTemplate 向订阅者推送消息
 */
@Component
public class MessageBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(MessageBroadcaster.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpMessagingTemplate messagingTemplate;

    public MessageBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 向指定账号的频道推送消息
     */
    public void broadcast(Long accountId, XianyuMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            messagingTemplate.convertAndSend("/topic/account/" + accountId, payload);
            logger.debug("Broadcast message to account {}: {} events", accountId, message.getDirection());
        } catch (Exception e) {
            logger.error("Failed to broadcast message to account {}", accountId, e);
        }
    }

    /**
     * 向所有客户端广播
     */
    public void broadcastAll(String message) {
        try {
            messagingTemplate.convertAndSend("/topic/all", message);
        } catch (Exception e) {
            logger.error("Failed to broadcast to all clients", e);
        }
    }
}
