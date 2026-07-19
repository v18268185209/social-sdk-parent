package cn.net.rjnetwork.xianyu.manager.message.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuMessageApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageMapper messageMapper;
    private final RuleService ruleService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMapper accountMapper;

    public MessageService(MessageMapper messageMapper, RuleService ruleService,
                          ApplicationEventPublisher eventPublisher, AccountMapper accountMapper) {
        this.messageMapper = messageMapper;
        this.ruleService = ruleService;
        this.eventPublisher = eventPublisher;
        this.accountMapper = accountMapper;
    }

    /** 返回会话摘要：sessionId、对方昵称、头像、最后消息时间、未读数（从缓存的 session 列表补） */
    public List<Map<String, Object>> listSessionSummaries(Long accountId) {
        // 先从本地库按 account 维度统计会话信息
        List<String> sessionIds = messageMapper.selectDistinctSessions(accountId);
        if (sessionIds == null || sessionIds.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> summaries = new ArrayList<>();
        for (String sid : sessionIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", sid);

            // 取该会话最新一条消息作为“最后消息”
            List<XianyuMessage> latest = messageMapper.selectBySession(accountId, sid, 1);
            if (!latest.isEmpty()) {
                XianyuMessage last = latest.get(0);
                row.put("lastContent", last.getContent());
                row.put("lastTime", last.getMessageTime() != null ? last.getMessageTime().toString() : null);
                row.put("direction", last.getDirection());
            }

            // senderName / senderId 取自同一会话里出现的对方发送者
            List<XianyuMessage> peers = messageMapper.selectBySession(accountId, sid, 5);
            String peer = null, peerId = null;
            for (XianyuMessage m : peers) {
                if ("INCOMING".equals(m.getDirection())) {
                    peerId = m.getSenderId();
                    peer = m.getSenderName();
                    break;
                }
            }
            if (peer == null || peer.isBlank()) peer = peerId != null ? peerId : "未知";
            row.put("counterpartyName", peer);
            row.put("counterpartyId", peerId);
            summaries.add(row);
        }
        return summaries;
    }

    public List<XianyuMessage> getHistory(Long accountId, String sessionId, int limit) {
        return messageMapper.selectBySession(accountId, sessionId, limit);
    }

    /** 如果本会话没有本地历史，主动通过 IM 长连接拉取并合并入库 */
    public void pullHistoryIfEmpty(Long accountId, String sessionId, int limit) {
        try {
            XianyuAccount acc = accountMapper.selectById(accountId);
            if (acc == null || acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) return;
            List<XianyuMessage> local = messageMapper.selectBySession(accountId, sessionId, 1);
            if (local != null && !local.isEmpty()) return;
            pullHistoryInternal(acc, sessionId, limit);
        } catch (Exception e) {
            log.warn("[MESSAGE] pullHistoryIfEmpty failed: accountId={}, sessionId={}", accountId, sessionId, e);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000)
    public void pullAllAccountsScheduled() {
        syncAllAccounts();
    }

    /** 一次性从 API 拉取所有账号的最新会话 + 历史，供调试按钮调用 */
    public void syncAllAccounts() {
        List<XianyuAccount> accounts;
        try {
            accounts = accountMapper.selectList(null);
        } catch (Exception e) {
            return;
        }
        for (XianyuAccount acc : accounts) {
            if (acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) continue;
            try {
                pullMessages(acc);
            } catch (Exception e) {
                log.warn("[MESSAGE] syncAllAccounts failed for account {}: {}", acc.getId(), e.getMessage());
            }
        }
    }

    /** 定时任务：每 30 秒拉取一次所有活跃账号的消息 */
    @PostConstruct
    public void init() {
        // 无需额外初始化
    }

    /**
     * 轮询方式拉取消息（兼容 session.sync + accs IM 两种方式）。
     * session.sync 只能拿到轻量会话快照，真正历史需要走 accs /r/MessageManager/listUserMessages。
     */
    public void pullMessages(XianyuAccount acc) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);

        // 1. 先拿 IM accessToken / 建立长连接，才能拉真实历史
        try {
            pullHistoryInternal(acc, null, 0);
        } catch (Exception e) {
            log.warn("[MESSAGE] IM history pull failed for account {}: {}", acc.getId(), e.getMessage());
        }

        // 2. 再用轻量 MTOP 接口补充会话元数据
        try {
            JsonNode sessionListData = msgApi.getSessionList();
            if (sessionListData == null || !sessionListData.has("data")) return;
            JsonNode sessions = sessionListData.path("data").path("sessions");
            if (!sessions.isArray() || sessions.size() == 0) return;

            List<String> userIds = new ArrayList<>();
            for (JsonNode session : sessions) {
                String cid = session.path("cid").asText();
                if (cid.isEmpty()) continue;
                String uid = session.path("userId").asText("");
                if (!uid.isEmpty() && !userIds.contains(uid)) userIds.add(uid);
            }
            if (!userIds.isEmpty()) {
                try {
                    msgApi.queryUsers(String.join(",", userIds));
                } catch (Exception e) {
                    log.warn("[MESSAGE] queryUsers failed: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[MESSAGE] session sync failed for account {}: {}", acc.getId(), e.getMessage());
        }
    }

    /** 通过 IM 长连接拉取指定会话或全量历史 */
    private void pullHistoryInternal(XianyuAccount acc, String targetCid, int limit) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);
        JsonNode historyData = msgApi.getMessageHistory(targetCid != null ? targetCid : "0", limit > 0 ? limit : 20);
        if (historyData == null || !historyData.has("data")) return;

        JsonNode data = historyData.path("data");
        JsonNode msgsNode = data.has("messages") ? data.path("messages") : data.has("module") ? data.path("module").path("messages") : null;
        if (msgsNode == null || !msgsNode.isArray()) return;

        for (JsonNode msg : msgsNode) {
            saveIncomingFromApi(acc.getId(), msg);
        }
    }

    private void saveIncomingFromApi(Long accountId, JsonNode msg) {
        String msgId = msg.path("msgId").asText("");
        if (msgId.isEmpty()) return;

        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<XianyuMessage>().eq(XianyuMessage::getMsgId, msgId));
        if (count != null && count > 0) return;

        String sessionId = msg.path("cid").asText(msg.path("conversationId").asText(""));
        XianyuMessage entity = new XianyuMessage();
        entity.setAccountId(accountId);
        entity.setSessionId(sessionId);
        entity.setMsgId(msgId);
        entity.setSenderId(msg.path("senderId").asText(""));
        entity.setSenderName(msg.path("senderNick").asText(""));
        entity.setDirection(msg.path("senderRole").asText("INCOMING").equals("SENDER") ? "OUTGOING" : "INCOMING");
        entity.setMsgType(msg.path("msgType").asText("TEXT"));
        entity.setAutoReply(Boolean.TRUE.equals(msg.path("isAutoReply").asBoolean()));
        entity.setMessageTime(LocalDateTime.now());

        JsonNode content = msg.path("content");
        if (content.has("text")) {
            entity.setContent(content.path("text").asText());
        } else if (content.has("body") && content.path("body").has("text")) {
            entity.setContent(content.path("body").path("text").asText());
        } else {
            entity.setContent(content.asText(""));
        }

        messageMapper.insert(entity);
        if ("INCOMING".equals(entity.getDirection())) {
            eventPublisher.publishEvent(new NotifyEvent(this, entity, "NEW_MESSAGE"));
        }
        log.info("[MESSAGE] pulled msg {} in session {} from account {}", msgId, sessionId, accountId);
    }

    public XianyuMessage sendMessage(MessageSendRequest request) throws Exception {
        XianyuAccount acc = accountMapper.selectById(request.getAccountId());
        if (acc == null || acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
            throw new IllegalArgumentException("Account not found or cookie expired");
        }

        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);

        // 通过 IM 长连接发送文本消息
        JsonNode sendResp = msgApi.sendMessage(request.getSessionId(), request.getContent());
        if (sendResp == null) {
            throw new IllegalStateException("sendMessage returned null");
        }

        // 构造本地落库记录
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
        // 仅对"收到的买家消息"发布通知（自动回复的 OUTGOING 不触发）
        if ("INCOMING".equals(msg.getDirection())) {
            Long accountId = msg.getAccountId();
            String accountName = accountName(accountId);
            eventPublisher.publishEvent(new NotifyEvent("NEW_MESSAGE", accountId, accountName,
                    Map.of("accountName", accountName,
                            "content", msg.getContent() != null ? msg.getContent() : "",
                            "sessionId", msg.getSessionId() != null ? msg.getSessionId() : "")));
        }
    }

    private String accountName(Long accountId) {
        cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount a =
                accountMapper.selectById(accountId);
        if (a == null) return String.valueOf(accountId);
        return a.getDisplayName() != null ? a.getDisplayName() : a.getAccountName();
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
