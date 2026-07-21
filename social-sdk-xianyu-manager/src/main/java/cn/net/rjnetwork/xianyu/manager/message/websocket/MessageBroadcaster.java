package cn.net.rjnetwork.xianyu.manager.message.websocket;

import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 统一 WebSocket 推送广播器
 * 支持：IM 消息、通知、监控结果、买家画像变动、市场情报更新、账号状态变更、熔断器事件
 */
@Component
public class MessageBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(MessageBroadcaster.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpMessagingTemplate messagingTemplate;

    public MessageBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** IM 消息推送 */
    public void broadcast(Long accountId, XianyuMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            messagingTemplate.convertAndSend("/topic/im/account/" + accountId, payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast IM to account {}", accountId, e);
        }
    }

    /** 全量广播（IM 消息/通知/系统事件通用） */
    public void broadcastAll(String message) {
        try {
            messagingTemplate.convertAndSend("/topic/all", message);
        } catch (Exception e) {
            logger.error("Failed to broadcast to all clients", e);
        }
    }

    /** 推送通知事件 */
    public void broadcastNotification(String payload) {
        try {
            messagingTemplate.convertAndSend("/topic/notifications", payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast notification", e);
        }
    }

    /** 推送监控任务执行结果 */
    public void broadcastMonitorResult(Long taskId, String payload) {
        try {
            messagingTemplate.convertAndSend("/topic/monitor/" + taskId, payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast monitor result for task {}", taskId, e);
        }
    }

    /** 推送市场情报更新 */
    public void broadcastMarketUpdate(String keyword, String payload) {
        try {
            messagingTemplate.convertAndSend("/topic/market/" + keyword, payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast market update for keyword {}", keyword, e);
        }
    }

    /** 推送买家画像变动 */
    public void broadcastBuyerUpdate(String buyerId, String payload) {
        try {
            messagingTemplate.convertAndSend("/topic/buyer/" + buyerId, payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast buyer update for {}", buyerId, e);
        }
    }

    /** 推送账号状态变更 */
    public void broadcastAccountStatus(Long accountId, String payload) {
        try {
            messagingTemplate.convertAndSend("/topic/account/status/" + accountId, payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast account status for {}", accountId, e);
        }
    }

    /** 推送熔断器事件 */
    public void broadcastCircuitBreaker(String payload) {
        try {
            messagingTemplate.convertAndSend("/topic/circuit-breaker", payload);
        } catch (Exception e) {
            logger.error("Failed to broadcast circuit breaker event", e);
        }
    }
}
