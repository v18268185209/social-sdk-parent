package cn.net.rjnetwork.xianyu.manager.message.websocket;

import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import cn.net.rjnetwork.xianyu.manager.buyer.service.BuyerProfileService;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息处理器
 */
@Component
public class MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    private final MessageService messageService;
    private final MessageBroadcaster broadcaster;
    private final RuleService ruleService;
    private final BuyerProfileService buyerProfileService;
    private final Map<Long, Object> accountLocks = new ConcurrentHashMap<>();

    public MessageHandler(MessageService messageService, MessageBroadcaster broadcaster, RuleService ruleService, BuyerProfileService buyerProfileService) {
        this.messageService = messageService;
        this.broadcaster = broadcaster;
        this.ruleService = ruleService;
        this.buyerProfileService = buyerProfileService;
    }

    /**
     * 处理收到的消息
     */
    public void handleIncomingMessage(XianyuMessage message) {
        Long accountId = message.getAccountId();
        Object lock = accountLocks.computeIfAbsent(accountId, k -> new Object());

        synchronized (lock) {
            // 1. 持久化消息
            messageService.saveIncomingMessage(message);

            // 2. 更新买家画像（消息到达自动聚合）
            try {
                if ("INCOMING".equals(message.getDirection()) && message.getSenderId() != null && !message.getSenderId().isBlank()) {
                    buyerProfileService.onIncomingMessage(message.getSenderId(), message.getSenderName() != null ? message.getSenderName() : "买家" + message.getSenderId());
                }
            } catch (Exception e) {
                logger.warn("更新买家画像失败: {}", e.getMessage());
            }

            // 3. 自动回复
            String reply = messageService.autoReplyIfNeeded(accountId, message);
            if (reply != null) {
                broadcaster.broadcast(accountId, message);
            }

            // 4. 广播新消息
            broadcaster.broadcast(accountId, message);
        }
    }

    /**
     * 处理发送消息
     */
    public void handleSendMessage(JsonNode jsonNode, ObjectMapper mapper) {
        try {
            Long accountId = mapper.treeToValue(jsonNode.get("accountId"), Long.class);
            String sessionId = jsonNode.get("sessionId").asText();
            String content = jsonNode.get("content").asText();

            messageService.sendMessage(new cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest() {{
                setAccountId(accountId);
                setSessionId(sessionId);
                setContent(content);
            }});
        } catch (Exception e) {
            logger.error("Failed to handle send message", e);
        }
    }
}
