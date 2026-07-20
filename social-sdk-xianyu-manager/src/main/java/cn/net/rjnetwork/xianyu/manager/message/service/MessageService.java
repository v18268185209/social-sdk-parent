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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MessageMapper messageMapper;
    private final RuleService ruleService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMapper accountMapper;
    private final XianyuCaptchaSolver captchaSolver;
    private final Map<Long, ReentrantLock> accountSyncLocks = new ConcurrentHashMap<>();

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
            pullHistoryInternal(acc, sessionId, limit, true);
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
                pullMessages(acc, false, false);
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
            // 如果已经保存过 IM 消息页 cookie，手动同步优先只用该 cookie 闭环，失败时不要再次自动打开验证码页；
            // 否则用户刚人工过完滑块后，一次同步失败会立刻把 CDP 页面又带回滑块。
            boolean hasImCookie = acc.getImCookieHeader() != null && !acc.getImCookieHeader().isBlank();
            boolean ok = pullMessages(acc, true, !hasImCookie);
            if (!ok) {
                throw new IllegalStateException(hasImCookie
                        ? "IM 同步失败：已使用 imCookieHeader 但仍被风控或接口失败，请重新完成消息页验证后重试"
                        : "IM 同步失败：可能仍被风控拦截，请完成滑块验证后重试");
            }
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
    public boolean pullMessages(XianyuAccount acc) throws Exception {
        return pullMessages(acc, true, true);
    }

    private boolean pullMessages(XianyuAccount acc, boolean waitForLock, boolean allowCaptcha) throws Exception {
        ReentrantLock lock = accountSyncLocks.computeIfAbsent(acc.getId(), id -> new ReentrantLock());
        boolean locked;
        if (waitForLock) {
            lock.lock();
            locked = true;
        } else {
            locked = lock.tryLock();
        }
        if (!locked) {
            log.info("[MESSAGE] account {} sync already running, skip scheduled pull", acc.getId());
            return true;
        }
        try {
            return pullMessagesLocked(acc, allowCaptcha);
        } finally {
            lock.unlock();
        }
    }

    private boolean pullMessagesLocked(XianyuAccount acc, boolean allowCaptcha) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        // 传递 IM/滑块验证 cookie（x5sec 等），与登录 cookie 合并用于 IM 连接
        if (acc.getImCookieHeader() != null && !acc.getImCookieHeader().isBlank()) {
            mtopClient.setImCookieHeader(acc.getImCookieHeader());
        }

        // 先拿 IM accessToken / 建立长连接，才能拉真实历史
        boolean ok = pullHistoryInternal(acc, null, 50, allowCaptcha);

        // mtop.taobao.idlemessage.pc.session.sync 当前返回 FAIL_SYS_API_NOT_FOUNDED，
        // 会话与历史统一走 IM WSS /r/Conversation/listNewestPagination + /r/MessageManager/listUserMessages。
        return ok;
    }

    /** 通过 IM 长连接拉取指定会话或全量历史 */
    private boolean pullHistoryInternal(XianyuAccount acc, String targetCid, int limit, boolean allowCaptcha) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        if (acc.getImCookieHeader() != null && !acc.getImCookieHeader().isBlank()) {
            mtopClient.setImCookieHeader(acc.getImCookieHeader());
        }
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);

        try {
            if (targetCid != null && !targetCid.isEmpty()) {
                // 单会话拉取
                JsonNode historyData = msgApi.getMessageHistory(normalizeCid(targetCid), Math.max(1, limit));
                if (isLwpError(historyData)) return false;
                saveMessagesFromHistoryResponse(acc.getId(), historyData, normalizeCid(targetCid));
                return true;
            }

            // 全量拉取：先获取会话列表，再逐条拉历史
            JsonNode convList = msgApi.getConversationList(limit > 0 ? limit : 50);
            if (convList == null) {
                log.warn("[MESSAGE] getConversationList returned null");
                return false;
            }
            if (isLwpError(convList)) {
                log.warn("[MESSAGE] conversation list lwp error, resp={}", truncate(convList.toString(), 2000));
                if (isRiskControlText(convList.toString())) {
                    return allowCaptcha && solveCaptchaAndRetry(acc, mtopClient, convList.toString());
                }
                return false;
            }
            List<JsonNode> conversations = extractArrayNodes(convList, "userConvs", "conversations", "conversationList", "sessions", "list");
            if (conversations.isEmpty()) {
                log.info("[MESSAGE] conversation list empty, keys={}, resp={}", fieldNames(convList), truncate(convList.toString(), 2000));
                return true;
            }
            log.info("[MESSAGE] found {} conversations", conversations.size());

            int historySuccess = 0;
            int historyFailure = 0;
            for (JsonNode conv : conversations) {
                String cid = extractConversationId(conv);
                if (cid.isEmpty()) {
                    log.warn("[MESSAGE] cannot find cid from conv: {}", truncate(conv.toString(), 1000));
                    historyFailure++;
                    continue;
                }
                try {
                    String normalizedCid = normalizeCid(cid);
                    JsonNode historyData = msgApi.getMessageHistory(normalizedCid, 20);
                    if (isLwpError(historyData)) {
                        log.warn("[MESSAGE] history lwp error for cid={}, resp={}", cid, truncate(String.valueOf(historyData), 1000));
                        historyFailure++;
                        continue;
                    }
                    saveMessagesFromHistoryResponse(acc.getId(), historyData, normalizedCid);
                    historySuccess++;
                } catch (Exception e) {
                    historyFailure++;
                    log.warn("[MESSAGE] failed to pull history for cid={}: {}", cid, e.getMessage());
                }
            }
            if (historySuccess == 0 && historyFailure > 0) {
                log.warn("[MESSAGE] all conversation histories failed, conversations={}", conversations.size());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[MESSAGE] full history pull failed for account {}: {}", acc.getId(), e.getMessage());
            
            // 检测风控并自动解决
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("FAIL_SYS_USER_VALIDATE") || 
                errorMessage.contains("punish") || errorMessage.contains("captcha"))) {
                if (!allowCaptcha) {
                    log.info("[MESSAGE] risk control detected during scheduled pull, skip captcha solving for account {}", acc.getId());
                    return false;
                }
                log.warn("[MESSAGE] Risk control detected! Attempting automatic slider captcha...");
                return solveCaptchaAndRetry(acc, mtopClient, errorMessage);
            }
            return false;
        }
    }

    /**
     * 自动解决滑块验证码并刷新 cookie
     */
    private boolean solveCaptchaAndRetry(XianyuAccount acc, XianyuMtopApiClient mtopClient, String exceptionMessage) {
        try {
            // 风控信息可能出现在：1.MTOP lastErrorResponse；2.当前异常消息本身（LWP/WebSocket层）
            String rawError = mtopClient.getLastErrorResponse();
            if (rawError == null || rawError.isEmpty()) {
                rawError = "";
            }
            if (exceptionMessage != null && !exceptionMessage.isBlank()) {
                rawError = rawError.isBlank() ? exceptionMessage : rawError + "\n" + exceptionMessage;
            }

            log.info("[CDP-AUTH] Raw error: {}", truncate(rawError, 800));

            // 从 JSON data.url 提取，也支持从异常消息里的明文 JSON 串正则提取
            String url = extractPunishUrlFromRaw(rawError);

            if (url == null || url.isEmpty()) {
                log.warn("[MESSAGE] No punish URL found in error");
                return false;
            }

            log.info("[CDP-AUTH] Punish URL: {}", truncate(url, 250));
            cn.net.rjnetwork.xianyu.captcha.model.CaptchaResult result = captchaSolver.solve(url);

            if (result.isSuccess()) {
                log.info("[MESSAGE] Slider captcha solved successfully!");

                // 重要：滑块验证获取的 cookie（x5sec 等）是 IM 专用，不能覆盖登录 cookie！
                // 必须存到 imCookieHeader 字段，与登录 cookie 分开管理
                acc.setImCookieHeader(result.getNewCookie());
                accountMapper.updateById(acc);
                log.info("[MESSAGE] Saved IM cookie (x5sec etc.) to imCookieHeader, login cookie preserved. imCookie={}",
                        truncate(result.getNewCookie(), 200));

                // 重新同步消息：已拿到新的 IM cookie 后只验证消息链路，不要在重试失败时再次打开验证码页，避免循环拉起滑块。
                log.info("[MESSAGE] Retrying message sync with new IM cookie (captcha disabled for retry)...");
                return pullMessages(acc, true, false);

            } else {
                log.error("[MESSAGE] Slider captcha failed: {}", result.getError());
                return false;
            }
        } catch (Exception e) {
            log.error("[MESSAGE] Failed to solve captcha and retry: {}", e.getMessage(), e);
            return false;
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

    private void saveMessagesFromHistoryResponse(Long accountId, JsonNode historyData, String fallbackCid) {
        if (historyData == null) {
            log.warn("[MESSAGE] history response is null, cid={}", fallbackCid);
            return;
        }
        List<JsonNode> messages = extractArrayNodes(historyData, "userMessageModels", "messages", "messageList", "userMessages", "list");
        if (messages.isEmpty()) {
            log.warn("[MESSAGE] history has no messages, cid={}, keys={}, resp={}", fallbackCid, fieldNames(historyData), truncate(historyData.toString(), 2000));
            return;
        }
        int before = messages.size();
        for (JsonNode msg : messages) {
            saveIncomingFromLwp(accountId, msg, fallbackCid);
        }
        log.info("[MESSAGE] processed {} history messages, cid={}", before, fallbackCid);
    }

    /** 从 LWP 响应体中保存消息（userMessageModels） */
    private void saveMessagesFromLwpResponse(Long accountId, JsonNode msgsNode) {
        if (msgsNode == null || !msgsNode.isArray()) return;
        for (JsonNode msg : msgsNode) {
            saveIncomingFromLwp(accountId, msg, "");
        }
    }

    private List<JsonNode> extractArrayNodes(JsonNode root, String... fieldNames) {
        List<JsonNode> result = new ArrayList<>();
        if (root == null || root.isMissingNode() || root.isNull()) return result;
        if (root.isArray()) {
            root.forEach(result::add);
            return result;
        }
        for (String fieldName : fieldNames) {
            JsonNode direct = root.path(fieldName);
            if (direct.isArray()) {
                direct.forEach(result::add);
                return result;
            }
        }
        if (root.isObject()) {
            for (java.util.Iterator<JsonNode> it = root.elements(); it.hasNext(); ) {
                JsonNode child = it.next();
                if (!child.isObject()) continue;
                List<JsonNode> nested = extractArrayNodes(child, fieldNames);
                if (!nested.isEmpty()) return nested;
            }
        }
        return result;
    }

    private boolean isLwpError(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return true;
        JsonNode code = node.path("code");
        return !code.isMissingNode() && code.asInt(200) >= 400;
    }

    /**
     * 判断 LWP / MTOP 响应文本是否含闲鱼风控关键字。
     * 用于在 WSS 业务帧返回 code>=400 时决定是否触发自动滑块。
     * 关键字覆盖：FAIL_SYS_USER_VALIDATE / punish / captcha / x5sec / 安全拦截 / 验证码。
     */
    private boolean isRiskControlText(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return text.contains("FAIL_SYS_USER_VALIDATE")
                || text.contains("punish")
                || text.contains("captcha")
                || lower.contains("x5sec")
                || text.contains("验证")
                || text.contains("拦截");
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node == null || !node.isObject()) return names;
        for (java.util.Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    private String extractConversationId(JsonNode node) {
        String cid = firstText(node, "cid", "conversationId", "conversationID", "userConvId", "sessionId");
        if (!cid.isEmpty()) return cid;
        JsonNode singleConv = node.path("singleChatUserConversation").path("singleChatConversation");
        cid = firstText(singleConv, "cid", "conversationId", "conversationID");
        if (!cid.isEmpty()) return cid;
        JsonNode lastMessage = node.path("singleChatUserConversation").path("lastMessage").path("message");
        cid = firstText(lastMessage, "cid", "conversationId", "conversationID");
        if (!cid.isEmpty()) return cid;
        String userId = firstText(node, "userId", "peerUserId", "counterUserId", "receiverId", "buyerId");
        return userId.isEmpty() ? "" : normalizeCid(userId);
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) return value.trim();
        }
        return "";
    }

    private String normalizeCid(String cid) {
        if (cid == null || cid.isBlank()) return "";
        String trimmed = cid.trim();
        return trimmed.contains("@") ? trimmed : trimmed + "@goofish";
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
    private void saveIncomingFromLwp(Long accountId, JsonNode msg, String fallbackCid) {
        // listUserMessages 返回常见结构是 userMessageModel.message，兼容 message 外包一层。
        JsonNode messageNode = msg.has("message") && msg.path("message").isObject() ? msg.path("message") : msg;
        String msgId = firstText(messageNode, "messageId", "msgId", "id", "messageID");
        if (msgId.isEmpty()) {
            log.warn("[MESSAGE] skip history message without msgId: {}", truncate(msg.toString(), 1000));
            return;
        }

        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<XianyuMessage>().eq(XianyuMessage::getMsgId, msgId));
        if (count != null && count > 0) return;

        String cid = firstText(messageNode, "cid", "conversationId", "conversationID", "sessionId", "userConvId");
        if (cid.isEmpty()) cid = fallbackCid;
        cid = normalizeCid(cid);
        if (cid.isEmpty()) {
            log.warn("[MESSAGE] skip history message without cid: {}", truncate(msg.toString(), 1000));
            return;
        }

        XianyuMessage entity = new XianyuMessage();
        entity.setAccountId(accountId);
        entity.setSessionId(cid);
        entity.setMsgId(msgId);
        JsonNode extension = messageNode.path("extension");
        entity.setSenderId(firstText(messageNode, "senderId", "senderUserId", "uid"));
        if (entity.getSenderId().isEmpty()) {
            entity.setSenderId(firstText(extension, "senderUserId", "senderId"));
        }
        entity.setSenderName(firstText(messageNode, "senderNickName", "senderNick", "senderName"));
        if (entity.getSenderName().isEmpty()) {
            entity.setSenderName(firstText(extension, "reminderTitle", "senderNickName", "senderNick"));
        }

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
        entity.setMessageTime(parseMessageTime(messageNode));

        // content.custom.data base64 -> JSON -> text
        JsonNode content = messageNode.path("content");
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
        if (entity.getContent() == null || entity.getContent().isBlank()) {
            entity.setContent(firstText(extension, "reminderContent", "summary", "content"));
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
