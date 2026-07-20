^package cn.net.rjnetwork.xianyu.manager.message.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketHandler.class);

    private final MessageHandler messageHandler;

    public MessageWebSocketHandler(@Lazy MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        logger.debug("Received WS message: {}", payload);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(payload);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "";

            switch (type) {
                case "send_message":
                    messageHandler.handleSendMessage(jsonNode, mapper);
                    break;
                default:
                    logger.warn("Unknown WS message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Failed to handle WS message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error: {}", exception.getMessage());
    }
}
