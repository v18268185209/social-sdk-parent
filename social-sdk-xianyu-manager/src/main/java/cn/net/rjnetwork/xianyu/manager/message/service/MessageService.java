package cn.net.rjnetwork.xianyu.manager.message.service;

import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageMapper messageMapper;
    private final RuleService ruleService;

    public MessageService(MessageMapper messageMapper, RuleService ruleService) {
        this.messageMapper = messageMapper;
        this.ruleService = ruleService;
    }

    public List<String> listSessions(Long accountId) {
        return messageMapper.selectDistinctSessions(accountId);
    }

    public List<XianyuMessage> getHistory(Long accountId, String sessionId, int limit) {
        return messageMapper.selectBySession(accountId, sessionId, limit);
    }

    public XianyuMessage sendMessage(MessageSendRequest request) {
        XianyuMessage msg = new XianyuMessage();
        msg.setAccountId(request.getAccountId());
        msg.setSessionId(request.getSessionId());
        msg.setContent(request.getContent());
        msg.setMsgType("TEXT");
        msg.setDirection("OUTGOING");
        msg.setAutoReply(false);
        msg.setMessageTime(LocalDateTime.now());
        msg.setCreatedAt(LocalDateTime.now());
        msg.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg;
    }

    public void saveIncomingMessage(XianyuMessage msg) {
        msg.setCreatedAt(LocalDateTime.now());
        msg.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 自动回复：检查消息是否命中规则，如果命中则保存回复并返回回复内容
     */
    public String autoReplyIfNeeded(Long accountId, XianyuMessage incomingMessage) {
        String reply = ruleService.autoReply(accountId, incomingMessage.getContent());
        if (reply != null) {
            XianyuMessage replyMsg = new XianyuMessage();
            replyMsg.setAccountId(accountId);
            replyMsg.setSessionId(incomingMessage.getSessionId());
            replyMsg.setSenderId("system");
            replyMsg.setSenderName("Auto Bot");
            replyMsg.setContent(reply);
            replyMsg.setMsgType("TEXT");
            replyMsg.setDirection("OUTGOING");
            replyMsg.setAutoReply(true);
            replyMsg.setMessageTime(LocalDateTime.now());
            saveIncomingMessage(replyMsg);
        }
        return reply;
    }

    public XianyuMessage getById(Long id) {
        return messageMapper.selectById(id);
    }
}
