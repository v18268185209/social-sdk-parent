package cn.net.rjnetwork.xianyu.manager.message.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuMessageApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImMessageWatcherService {

    private static final Logger log = LoggerFactory.getLogger(ImMessageWatcherService.class);

    private final AccountMapper accountMapper;
    private final MessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, Boolean> seenMsgIds = new ConcurrentHashMap<>();

    public ImMessageWatcherService(AccountMapper accountMapper, MessageMapper messageMapper, ApplicationEventPublisher eventPublisher) {
        this.accountMapper = accountMapper;
        this.messageMapper = messageMapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        log.info("[IM] watcher service initialized");
    }

    @Scheduled(fixedDelay = 8000)
    public void watchAllAccounts() {
        List<XianyuAccount> accounts;
        try { accounts = accountMapper.selectList(null); } catch (Exception e) { return; }
        for (XianyuAccount acc : accounts) {
            if (acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) continue;
            try {
                pullMessages(acc);
            } catch (Exception e) {
                log.warn("[IM] pull failed account {}: {}", acc.getId(), e.getMessage());
            }
        }
    }

    private void pullMessages(XianyuAccount acc) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);

        JsonNode sessionListData = msgApi.getSessionList();
        if (sessionListData == null || !sessionListData.has("data")) return;
        JsonNode sessions = sessionListData.path("data").path("sessions");
        if (!sessions.isArray() || sessions.size() == 0) return;

        for (JsonNode session : sessions) {
            String cid = session.path("cid").asText();
            if (cid.isEmpty()) continue;

            JsonNode historyData = msgApi.getMessageHistory(cid, 20);
            if (historyData == null || !historyData.has("data")) continue;
            JsonNode msgsNode = historyData.path("data").path("messages");
            if (!msgsNode.isArray()) continue;

            for (JsonNode msg : msgsNode) {
                String msgId = msg.path("msgId").asText();
                if (msgId.isEmpty() || seenMsgIds.containsKey(msgId)) continue;

                XianyuMessage entity = new XianyuMessage();
                entity.setAccountId(acc.getId());
                entity.setCid(cid);
                entity.setMsgId(msgId);
                entity.setSenderId(msg.path("senderId").asText());
                entity.setSenderNick(msg.path("senderNick").asText());
                entity.setDirection("INCOMING");
                entity.setMsgType(msg.path("msgType").asText("text"));
                entity.setContent(msg.path("content").path("text").asText());
                entity.setMessageTime(LocalDateTime.now());

                Long count = messageMapper.selectCount(
                        new LambdaQueryWrapper<XianyuMessage>().eq(XianyuMessage::getMsgId, msgId));
                if (count != null && count == 0) {
                    messageMapper.insert(entity);
                    seenMsgIds.put(msgId, Boolean.TRUE);
                    eventPublisher.publishEvent(new NotifyEvent(this, entity));
                    log.info("[IM] new msg from {} (account {}): {}", entity.getSenderNick(), acc.getId(),
                            entity.getContent());
                }
            }
        }
    }
}
