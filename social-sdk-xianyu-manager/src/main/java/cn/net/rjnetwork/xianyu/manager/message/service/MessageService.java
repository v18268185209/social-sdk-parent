package cn.net.rjnetwork.xianyu.manager.message.service;

import cn.net.rjnetwork.xianyu.api.XianyuImAccsClient;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuMessageApiService;
import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageMapper messageMapper;
    private final RuleService ruleService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMapper accountMapper;
    private final XianyuCaptchaSolver captchaSolver;

    public MessageService(MessageMapper messageMapper, RuleService ruleService,
                          ApplicationEventPublisher eventPublisher, AccountMapper accountMapper,
                          XianyuCaptchaSolver captchaSolver) {
        this.messageMapper = messageMapper;
        this.ruleService = ruleService;
        this.eventPublisher = eventPublisher;
        this.accountMapper = accountMapper;
        this.captchaSolver = captchaSolver;
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

    public XianyuMessage getById(Long id) {
        return messageMapper.selectById(id);
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

    /**
     * 实时监听定时任务 — 每 30 秒按账号拉新增消息推入本地
     * <p>这是「实时监听」的核心：定时轮询所有活跃账号的最新会话，
     * saveIncomingFromLwp 按 msgId 去重，已有消息不重复插，所以每次只增量存新消息。
     * saveIncomingFromLwp 第 447 行有「eventPublisher.publishEvent(NEW_MESSAGE)」，
     * 新消息会触发 NotifyEvent，实时推送给 AI 自动回复 / WebSocket 广播。</p>
     *
     * <p>「实时」= 30 秒粒度的轮询实时，不是 WSS 长连接推送实时。
     * 闲鱼 LWP frame 拉消息比 WSS 推送更稳定（不依赖长连接保活），30s 延迟可接受。
     * 若后续要秒级实时，可改用 {@link XianyuImAccsClient#addPushListener} 监听推送帧。</p>
     */
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

    /**
     * 单账号手动同步消息 — 真调闲鱼按 accountId 拉最新会话推入本地
     * <p>供 MessageController.syncByAccount 调，前端「手动同步」按钮触发，
     * 比 syncAllAccounts（全账号循环）更轻量，专拉一个账号。</p>
     */
    public void syncSingleAccount(Long accountId) {
        XianyuAccount acc;
        try {
            acc = accountMapper.selectById(accountId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (acc == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + accountId);
        }
        try {
            pullMessages(acc);
        } catch (Exception e) {
            log.warn("[MESSAGE] syncSingleAccount failed for account {}: {}", accountId, e.getMessage());
            throw new IllegalStateException("Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * 按账号拉本地已同步的消息列表 — 前端消息页面渲染用
     * <p>不再只按 sessionId 拉单会话，而是按 accountId 拉该账号所有消息，
     * 前端按 sessionId 二次分组渲染会话卡片，每个账号一个 tab。
     * 按 messageTime 倒序排（最新消息在前），limit 控制返回条数。</p>
     */
    public List<XianyuMessage> listByAccount(Long accountId, int limit) {
        if (limit <= 0 || limit > 500) limit = 100;
        LambdaQueryWrapper<XianyuMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuMessage::getAccountId, accountId)
               .orderByDesc(XianyuMessage::getMessageTime)
               .last("LIMIT " + limit);
        return messageMapper.selectList(wrapper);
    }

    /** 定时任务：每 30 秒拉取一次所有活跃账号的消息 */
    @PostConstruct
    public void init() {
        // 无需额外初始化
    }

    /**
     * 轮询方式拉取消息。
     * 先通过 WSS 获取会话列表，再逐条拉历史消息。
     */
    public void pullMessages(XianyuAccount acc) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);

        // 1. 先拿 IM accessToken / 建立长连接，才能拉真实历史
        try {
            pullHistoryInternal(acc, null, 50);
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

        try {
            if (targetCid != null && !targetCid.isEmpty()) {
                // 单会话拉取
                JsonNode historyData = msgApi.getMessageHistory(targetCid, Math.max(1, limit));
                if (historyData == null || !historyData.has("body")) return;
                saveMessagesFromLwpResponse(acc.getId(), historyData.path("body").path("userMessageModels"));
                return;
            }

            // 全量拉取：先获取会话列表，再逐条拉历史
            JsonNode convList = msgApi.getConversationList(limit > 0 ? limit : 50);
            if (convList == null || !convList.has("body")) {
                log.warn("[MESSAGE] getConversationList has no body");
                return;
            }
            JsonNode bodyNode = convList.path("body");
            // 收集所有字段名用于调试
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (java.util.Iterator<String> it = bodyNode.fieldNames(); it.hasNext(); ) {
                keys.add(it.next());
            }
            log.info("[MESSAGE] conversation body keys: {}", keys);
            JsonNode userConvs = bodyNode.path("userConvs");
            if (!userConvs.isArray() || userConvs.size() == 0) {
                log.warn("[MESSAGE] userConvs empty, full response: {}", convList.toString());
                return;
            }
            log.info("[MESSAGE] found {} conversations", userConvs.size());

            for (JsonNode conv : userConvs) {
                String cid = conv.path("cid").asText();
                if (cid.isEmpty()) {
                    // 尝试其他可能的字段：userId, accountId, userConvId 等
                    cid = conv.path("userId").asText();
                    if (cid.isEmpty()) {
                        log.warn("[MESSAGE] cannot find cid from conv: {}", conv.toString());
                        continue;
                    }
                }
                // listUserMessages 的 cid 不需要 @goofish 后缀，内部会自动拼接
                try {
                    JsonNode historyData = msgApi.getMessageHistory(cid, 20);
                    if (historyData == null || !historyData.has("body")) continue;
                    saveMessagesFromLwpResponse(acc.getId(), historyData.path("body").path("userMessageModels"));
                } catch (Exception e) {
                    log.warn("[MESSAGE] failed to pull history for cid={}: {}", cid, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[MESSAGE] full history pull failed for account {}: {}", acc.getId(), e.getMessage());
            
            // 检测风控并自动解决
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("FAIL_SYS_USER_VALIDATE") || 
                errorMessage.contains("punish") || errorMessage.contains("captcha"))) {
                
                log.warn("[MESSAGE] Risk control detected! Attempting automatic slider captcha...");
                solveCaptchaAndRetry(acc, mtopClient);
            }
        }
    }

    /**
     * 自动解决滑块验证码并刷新 cookie
     */
    private void solveCaptchaAndRetry(XianyuAccount acc, XianyuMtopApiClient mtopClient) {
        try {
            // 风控信息可能出现在：1.MTOP lastErrorResponse；2.当前异常消息本身（LWP/WebSocket层）
            String rawError = mtopClient.getLastErrorResponse();
            if (rawError == null || rawError.isEmpty()) {
                rawError = "";
            }

            log.info("[CDP-AUTH] Raw error: {}", truncate(rawError, 400));

            // 从 JSON data.url 提取，也支持从异常消息里的明文 JSON 串正则提取
            String url = extractPunishUrlFromRaw(rawError);

            if (url == null || url.isEmpty()) {
                log.warn("[MESSAGE] No punish URL found in error");
                return;
            }

            log.info("[CDP-AUTH] Punish URL: {}", truncate(url, 250));
            cn.net.rjnetwork.xianyu.captcha.model.CaptchaResult result = captchaSolver.solve(url);

            if (result.isSuccess()) {
                log.info("[MESSAGE] Slider captcha solved successfully!");

                // 更新账号 cookie
                acc.setCookieHeader(result.getNewCookie());
                accountMapper.updateById(acc);

                // 重新同步消息
                log.info("[MESSAGE] Retrying message sync with new cookie...");
                pullMessages(acc);

            } else {
                log.error("[MESSAGE] Slider captcha failed: {}", result.getError());
            }
        } catch (Exception e) {
            log.error("[MESSAGE] Failed to solve captcha and retry: {}", e.getMessage(), e);
        }
    }

    /**
     * 从原始错误信息中提取 punish URL。支持两种格式：
     * 1) JSON data.url: {"ret": [...], "data": {"url": "..."}}
     * 2) 异常消息中内嵌的明文 JSON 字符串，如 resp={"ret":[...],"data":{"url":"..."}}
     */
    private String extractPunishUrlFromRaw(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        // 1. 直接是 JSON
        String json = extractJsonPunishUrl(raw);
        if (json != null && !json.isEmpty()) return json;

        // 2. 消息里包含 resp= 或类似 JSON 片段
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("resp=(\\{.*?\\})")
                .matcher(raw);
        while (m.find()) {
            String jsonStr = m.group(1);
            try {
                JsonNode node = MAPPER.readTree(jsonStr);
                if (node.has("data") && node.path("data").has("url")) {
                    return node.path("data").path("url").asText();
                }
            } catch (Exception e) {
                // skip
            }
        }

        // 3. 兜底：直接在任意位置找 h5api.m.goofish.com:443/h5/mtop.*/punish?...
        java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("(https?://[^\"]+punish[^\"]+)")
                .matcher(raw);
        if (urlMatcher.find()) {
            return urlMatcher.group(1).trim().replaceAll("[\\s]+$", "");
        }

        return null;
    }

    /**
     * 从 JSON 字符串中解析 data.url 字段的 punish URL
     */
    private String extractJsonPunishUrl(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return null;
        try {
            JsonNode node = MAPPER.readTree(jsonStr);
            JsonNode data = node.path("data");
            if (data.has("url")) {
                return data.path("url").asText();
            }
        } catch (Exception e) {
            log.warn("[CDP-AUTH] Failed to parse error body as JSON: {}", e.getMessage());
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 从 LWP 响应体中保存消息（userMessageModels） */
    private void saveMessagesFromLwpResponse(Long accountId, JsonNode msgsNode) {
        if (msgsNode == null || !msgsNode.isArray()) return;
        for (JsonNode msg : msgsNode) {
            saveIncomingFromLwp(accountId, msg);
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

    /** 从 LWP 响应体保存消息（userMessageModels） */
    private void saveIncomingFromLwp(Long accountId, JsonNode msg) {
        // LWP 消息结构：msg.messageId, msg.senderId, msg.senderNickName, msg.content
        String msgId = msg.path("messageId").asText("");
        if (msgId.isEmpty()) {
            // 兼容旧格式
            msgId = msg.path("msgId").asText("");
        }
        if (msgId.isEmpty()) return;

        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<XianyuMessage>().eq(XianyuMessage::getMsgId, msgId));
        if (count != null && count > 0) return;

        String cid = msg.path("cid").asText(msg.path("conversationId").asText(""));
        if (!cid.contains("@")) {
            cid += "@goofish";
        }

        XianyuMessage entity = new XianyuMessage();
        entity.setAccountId(accountId);
        entity.setSessionId(cid);
        entity.setMsgId(msgId);
        entity.setSenderId(msg.path("senderId").asText(""));
        entity.setSenderName(msg.path("senderNickName").asText(msg.path("senderNick").asText("")));

        // direction: senderId == selfId（账号 cookie 里的 userId）是 OUTGOING，否则 INCOMING
        // 原 bug：写死 INCOMING，导致自己发的消息被当进来消息，会话列表全是自己回自己的怪状
        String selfUserId = extractSelfUserId(accountId);
        String senderId = entity.getSenderId();
        boolean isOutgoing = !selfUserId.isEmpty() && selfUserId.equals(senderId);
        entity.setDirection(isOutgoing ? "OUTGOING" : "INCOMING");
        entity.setMsgType("TEXT");
        entity.setAutoReply(false);
        // messageTime: 用闲鱼返回的真实时间（msgTime / createTime / timestamp 字段，毫秒级 Unix）
        // 原 bug：写死 LocalDateTime.now()，导致所有消息时间戳错位（同步历史消息时间全是当下）
        entity.setMessageTime(parseMessageTime(msg));

        // content.custom.data base64 -> JSON -> text
        JsonNode content = msg.path("content");
        if (content.has("custom") && content.path("custom").has("data")) {
            try {
                byte[] decoded = java.util.Base64.getDecoder().decode(content.path("custom").path("data").asText());
                String plainJson = new String(decoded, StandardCharsets.UTF_8);
                JsonNode plain = MAPPER.readTree(plainJson);
                entity.setContent(plain.path("text").path("text").asText(""));
            } catch (Exception e) {
                entity.setContent(content.path("custom").path("data").asText(""));
            }
        } else if (content.has("text")) {
            entity.setContent(content.path("text").asText(""));
        } else if (content.has("body") && content.path("body").has("text")) {
            entity.setContent(content.path("body").path("text").asText(""));
        } else {
            entity.setContent(content.asText(""));
        }

        messageMapper.insert(entity);
        if ("INCOMING".equals(entity.getDirection())) {
            eventPublisher.publishEvent(new NotifyEvent(this, entity, "NEW_MESSAGE"));
        }
        log.info("[MESSAGE] pulled lwp msg {} in session {} from account {} (dir={})", msgId, cid, accountId, entity.getDirection());
    }

    /**
     * 从账号 cookie 里提取 selfUserId（闲鱼 cookie 的 unb 字段或 userId 字段）
     * 用于判消息 direction：senderId == selfId 则 OUTGOING（自己发的），否则 INCOMING（对方发的）
     */
    private String extractSelfUserId(Long accountId) {
        try {
            XianyuAccount acc = accountMapper.selectById(accountId);
            if (acc == null || acc.getCookieHeader() == null) return "";
            String cookie = acc.getCookieHeader();
            // cookie 形如 "key1=val1; key2=val2; ..."，找 unb= 或 userId= 字段
            for (String seg : cookie.split(";")) {
                seg = seg.trim();
                if (seg.startsWith("unb=")) return seg.substring(4).trim();
                if (seg.startsWith("_m_h5_tk=")) {
                    // _m_h5_tk 形如 "token_userid_timestamp"，第二个是 userId
                    String val = seg.substring("_m_h5_tk=".length());
                    String[] parts = val.split("_");
                    if (parts.length >= 2) return parts[1];
                }
            }
        } catch (Exception e) {
            log.warn("[MESSAGE] extractSelfUserId failed for account {}: {}", accountId, e.getMessage());
        }
        return "";
    }

    /**
     * 从闲鱼 LWP 消息里解析真实时间戳，多个候选字段名兼容
     * 候选：msgTime / createTime / timestamp / time（毫秒级 Unix），转 LocalDateTime
     * 解析失败回退 LocalDateTime.now()（保留原 bug 行为不至于存不进 DB）
     */
    private LocalDateTime parseMessageTime(JsonNode msg) {
        String[] candidates = {"msgTime", "createTime", "timestamp", "time", "createTimeInMs"};
        for (String key : candidates) {
            JsonNode v = msg.path(key);
            if (v.isMissingNode() || v.isNull()) continue;
            try {
                long ms = v.asLong(0);
                if (ms > 0) {
                    // 13 位毫秒级 Unix 时间戳
                    return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms),
                            java.time.ZoneId.systemDefault());
                }
            } catch (Exception ignored) {}
        }
        return LocalDateTime.now();
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
}
