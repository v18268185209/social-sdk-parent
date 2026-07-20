^package cn.net.rjnetwork.xianyu.manager.message.websocket;

import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
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
    private final Map<Long, Object> accountLocks = new ConcurrentHashMap<>();

    public MessageHandler(MessageService messageService, MessageBroadcaster broadcaster, RuleService ruleService) {
        this.messageService = messageService;
        this.broadcaster = broadcaster;
        this.ruleService = ruleService;
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

            // 2. 自动回复
            String reply = messageService.autoReplyIfNeeded(accountId, message);
            if (reply != null) {
                broadcaster.broadcast(accountId, message);
            }

            // 3. 广播新消息
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
