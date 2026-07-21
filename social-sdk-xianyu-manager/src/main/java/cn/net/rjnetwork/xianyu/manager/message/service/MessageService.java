package cn.net.rjnetwork.xianyu.manager.message.service;

import cn.net.rjnetwork.xianyu.api.XianyuImAccsClient;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuMessageApiService;
import cn.net.rjnetwork.xianyu.captcha.service.XianyuCaptchaSolver;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
    private final Map<Long, XianyuMessageApiService> accountMessageApis = new ConcurrentHashMap<>();
    private final Map<Long, String> accountCookieFingerprints = new ConcurrentHashMap<>();

    /** 账号服务 — 用于风控触发时按账号启动独占 Chrome 容器（per-account CDP 端点）。 */
    private AccountService accountService;

    public MessageService(MessageMapper messageMapper, RuleService ruleService,
                          ApplicationEventPublisher eventPublisher, AccountMapper accountMapper,
                          XianyuCaptchaSolver captchaSolver) {
        this.messageMapper = messageMapper;
        this.ruleService = ruleService;
        this.eventPublisher = eventPublisher;
        this.accountMapper = accountMapper;
        this.captchaSolver = captchaSolver;
    }

    /** Spring 注入 AccountService（可选，避免启动期循环依赖）。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAccountService(AccountService accountService) {
        this.accountService = accountService;
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
                row.put("contentType", last.getMsgType() != null ? last.getMsgType() : "TEXT");
                row.put("lastTime", last.getMessageTime() != null ? last.getMessageTime().toString() : null);
                row.put("direction", last.getDirection());
            }

            // 会话标题必须取“对方”信息：优先该会话最新 INCOMING 消息；
            // 如果最近消息是我发出的，不能用 OUTGOING 的 senderName（那是自己的账号名）。
            XianyuMessage peerMsg = messageMapper.selectLatestIncoming(accountId, sid);
            String peer = peerMsg != null ? peerMsg.getSenderName() : null;
            String peerId = peerMsg != null ? peerMsg.getSenderId() : null;
            String peerAvatar = peerMsg != null ? peerMsg.getSenderAvatar() : null;
            if (peer == null || peer.isBlank()) peer = peerId != null ? peerId : "未知";
            row.put("counterpartyName", peer);
            row.put("counterpartyId", peerId);
            row.put("counterpartyAvatar", peerAvatar);
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
            // 手动同步允许自动过滑块：若当前 imCookie 仍有效则直接成功；
            // 若被 punish 拦截，pullHistoryInternal 会调 solveCaptchaAndRetry 自动刷新 IM cookie 并重试。
            // 之前「已有 imCookie 就 allowCaptcha=false」会让滑块失效场景直接放弃，用户被迫手动重过验证。
            boolean ok = pullMessages(acc, true, true);
            if (!ok) {
                throw new IllegalStateException("IM 同步失败：可能仍被风控拦截或滑块自动解题失败，请完成滑块验证后重试");
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

    /**
     * 给 IM WSS 长连接挂推送监听器，让实时帧（新消息/会话变更）直接落库。
     * <p>闲鱼 IM 推送帧的 lwp 路径主要几类：</p>
     * <ul>
     *   <li>{@code /r/MessageManager/push} 或类似：新消息推送，body.userMessageModels 是消息列表</li>
     *   <li>{@code /r/Conversation/notify} 或 {@code /r/Conversation/change}：会话变更</li>
     *   <li>{@code /r/SyncStatus/notify}：增量同步通知，含新消息/会话变更</li>
     * </ul>
     * <p>监听器做兜底：把所有疑似包含消息数组的推送帧走 saveIncomingFromLwp 落库，
     * 已有 msgId 会被去重，所以重复推送无害。</p>
     *
     * @param accountId 当前账号 id
     * @param accsClient IM 长连接客户端
     */
    private XianyuMessageApiService getMessageApiForAccount(XianyuAccount acc, XianyuMtopApiClient mtopClient) {
        String fingerprint = cookieFingerprint(acc);
        String oldFingerprint = accountCookieFingerprints.get(acc.getId());
        XianyuMessageApiService existing = accountMessageApis.get(acc.getId());
        if (existing != null && fingerprint.equals(oldFingerprint)) {
            return existing;
        }
        closeMessageApi(acc.getId(), existing);
        XianyuMessageApiService created = new XianyuMessageApiService(mtopClient);
        accountMessageApis.put(acc.getId(), created);
        accountCookieFingerprints.put(acc.getId(), fingerprint);
        return created;
    }

    private String cookieFingerprint(XianyuAccount acc) {
        String loginCookie = acc.getCookieHeader() != null ? acc.getCookieHeader() : "";
        String imCookie = acc.getImCookieHeader() != null ? acc.getImCookieHeader() : "";
        return Integer.toHexString((loginCookie + "\n" + imCookie).hashCode());
    }

    private void closeMessageApi(Long accountId, XianyuMessageApiService msgApi) {
        if (msgApi == null) return;
        try {
            msgApi.closeAccsClient();
        } catch (Exception e) {
            log.debug("[MESSAGE] close old IM client failed for account {}: {}", accountId, e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        for (Map.Entry<Long, XianyuMessageApiService> entry : accountMessageApis.entrySet()) {
            closeMessageApi(entry.getKey(), entry.getValue());
        }
        accountMessageApis.clear();
        accountCookieFingerprints.clear();
    }

    private void registerPushListener(Long accountId, XianyuImAccsClient accsClient) {
        final String listenerKey = "msg-sync-" + accountId;
        // 先移除旧监听器，避免账号切换时重复挂监听
        accsClient.removePushListener(listenerKey);
        accsClient.addPushListener(listenerKey, frame -> {
            try {
                JsonNode body = frame.path("body");
                if (body.isMissingNode() || body.isNull()) return;

                // 兼容多种推送结构：直接数组 / {userMessageModels:[...]} / {messages:[...]}
                List<JsonNode> messages = extractArrayNodes(body,
                        "userMessageModels", "messages", "messageList", "userMessages", "list");
                if (messages.isEmpty()) return;

                int saved = 0;
                for (JsonNode msg : messages) {
                    try {
                        saveIncomingFromLwp(accountId, msg, "");
                        saved++;
                    } catch (Exception me) {
                        log.warn("[MESSAGE] push-listener save failed: {}", me.getMessage());
                    }
                }
                if (saved > 0) {
                    log.info("[MESSAGE] push-listener saved {} new messages for account {}", saved, accountId);
                }
            } catch (Exception e) {
                log.warn("[MESSAGE] push-listener parse failed: {}", e.getMessage());
            }
        });
    }

    /** 通过 IM 长连接拉取指定会话或全量历史 */
    private boolean pullHistoryInternal(XianyuAccount acc, String targetCid, int limit, boolean allowCaptcha) throws Exception {
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        if (acc.getImCookieHeader() != null && !acc.getImCookieHeader().isBlank()) {
            mtopClient.setImCookieHeader(acc.getImCookieHeader());
        }
        XianyuMessageApiService msgApi = getMessageApiForAccount(acc, mtopClient);

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

            // 历史拉完后挂 pushListener，让 WSS 实时推送帧直接落库（图片/视频/文本/语音）
            // 注意：getAccsClient() 内部会调 ensureAccs()，复用同一条已建立的长连接
            try {
                XianyuImAccsClient accsClient = msgApi.getAccsClient();
                if (accsClient != null) {
                    registerPushListener(acc.getId(), accsClient);
                }
            } catch (Exception pe) {
                log.warn("[MESSAGE] registerPushListener failed for account {}: {}", acc.getId(), pe.getMessage());
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
            publishCaptchaRequired(acc, url, rawError);
            // 风控触发时，按账号启动（或复用）独占 Chrome 容器，并把它返回的 CDP 端点传给 captchaSolver，
            // 避免 captchaSolver 写死连全局单点 127.0.0.1:9222（用户没手动开 Chrome 时连不上）。
            String accountCdpEndpoint = ensureAccountCdpEndpoint(acc);
            // 把账号登录 cookie + IM cookie 一并传给 solver，让它在导航到 goofish.com/im 之前
            // 通过 CDP Network.setCookies 注入到 Chrome 容器，解决空白 profile 没登录态的问题。
            cn.net.rjnetwork.xianyu.captcha.model.CaptchaResult result = captchaSolver.solve(
                    url, accountCdpEndpoint, acc.getCookieHeader(), acc.getImCookieHeader());

            // 登录态失效：注入登录 cookie 后 IM 页仍跳到登录页，说明账号登录 cookie 已过期。
            // 此时滑块过了也进不了消息页，推送 ACCOUNT_COOKIE_EXPIRED 通知，让用户在网页端重新登录。
            if (result.isLoginExpired()) {
                log.error("[MESSAGE] Account {} login cookie expired, IM page redirected to login", acc.getId());
                publishLoginExpired(acc);
                return false;
            }

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
     * 发送场景专用：解决滑块验证码并刷新 IM cookie，但不重试拉消息（与 solveCaptchaAndRetry 区别在此）。
     * <p>调用链：sendMessage → ensureAccs/connect 抛 IllegalStateException(含 punish URL)
     * → 抽 punish URL → captchaSolver.solve → 拿到 x5sec cookie 写入 imCookieHeader
     * → 上层 sendMessage 重试发送。</p>
     *
     * @param rawError 含 punish URL 的原始错误文本（IllegalStateException.getMessage()）
     * @return true=滑块已解决、IM cookie 已刷新，可重试发送；false=无法解决
     */
    private boolean solveCaptchaForSend(XianyuAccount acc, String rawError) {
        try {
            if (rawError == null || rawError.isBlank()) return false;
            log.info("[CDP-AUTH] sendMessage risk control, raw error: {}", truncate(rawError, 800));

            String url = extractPunishUrlFromRaw(rawError);
            if (url == null || url.isEmpty()) {
                log.warn("[MESSAGE] sendMessage: No punish URL found in error");
                return false;
            }

            log.info("[CDP-AUTH] sendMessage Punish URL: {}", truncate(url, 250));
            publishCaptchaRequired(acc, url, rawError);
            String accountCdpEndpoint = ensureAccountCdpEndpoint(acc);
            cn.net.rjnetwork.xianyu.captcha.model.CaptchaResult result = captchaSolver.solve(
                    url, accountCdpEndpoint, acc.getCookieHeader(), acc.getImCookieHeader());

            // 登录态失效：推送 ACCOUNT_COOKIE_EXPIRED 通知，sendMessage 上层不再重试
            if (result.isLoginExpired()) {
                log.error("[MESSAGE] sendMessage: Account {} login cookie expired", acc.getId());
                publishLoginExpired(acc);
                return false;
            }

            if (result.isSuccess()) {
                log.info("[MESSAGE] sendMessage: Slider captcha solved successfully!");
                // x5sec 等 IM 专用 cookie 单独存，不能覆盖登录 cookie
                acc.setImCookieHeader(result.getNewCookie());
                accountMapper.updateById(acc);
                log.info("[MESSAGE] sendMessage: Saved IM cookie (x5sec), len={}",
                        result.getNewCookie() != null ? result.getNewCookie().length() : 0);
                return true;
            } else {
                log.error("[MESSAGE] sendMessage: Slider captcha failed: {}", result.getError());
                return false;
            }
        } catch (Exception e) {
            log.error("[MESSAGE] sendMessage: Failed to solve captcha: {}", e.getMessage(), e);
            return false;
        }
    }

    private void publishCaptchaRequired(XianyuAccount acc, String captchaUrl, String rawError) {
        try {
            String accountName = accountName(acc.getId());
            String controlUrl = "/cdp-proxy/open?accountId=" + acc.getId();
            eventPublisher.publishEvent(new NotifyEvent("CAPTCHA_REQUIRED", acc.getId(), accountName,
                    Map.of("accountName", accountName,
                            "controlUrl", controlUrl,
                            "captchaUrl", captchaUrl != null ? captchaUrl : "",
                            "imPageUrl", captchaSolver.getManualVerificationPageUrl(),
                            "cdpEndpoint", captchaSolver.getCdpHttpEndpoint(),
                            "errorSummary", truncate(rawError, 500))));
        } catch (Exception e) {
            log.debug("[MESSAGE] publish CAPTCHA_REQUIRED failed: {}", e.getMessage());
        }
    }

    /**
     * 登录态失效时推送 ACCOUNT_COOKIE_EXPIRED 通知。
     * <p>触发条件：solve 流程已注入账号登录 cookie，但 IM 页仍跳到登录页，
     * 说明账号登录 Cookie 已过期，滑块过了也进不了消息页。</p>
     * <p>动作：同时把账号 status 标记为 COOKIE_EXPIRED，让上层任务跳过该账号，
     * 等用户在网页端重新登录后恢复。</p>
     */
    private void publishLoginExpired(XianyuAccount acc) {
        try {
            String accountName = accountName(acc.getId());
            // 更新账号状态为 COOKIE_EXPIRED，避免后续定时任务继续重试同一个失效账号
            acc.setStatus("COOKIE_EXPIRED");
            acc.setLastError("Login cookie expired, IM page redirected to login");
            acc.setUpdatedAt(java.time.LocalDateTime.now());
            accountMapper.updateById(acc);
            eventPublisher.publishEvent(new NotifyEvent("ACCOUNT_COOKIE_EXPIRED", acc.getId(), accountName,
                    Map.of("accountName", accountName)));
            log.warn("[MESSAGE] Published ACCOUNT_COOKIE_EXPIRED for account {} ({})",
                    acc.getId(), accountName);
        } catch (Exception e) {
            log.warn("[MESSAGE] publish ACCOUNT_COOKIE_EXPIRED failed: {}", e.getMessage());
        }
    }

    /**
     * 风控触发时，按账号启动（或复用）独占 Chrome 容器，返回该容器的 CDP HTTP 端点。
     * <p>如果 AccountService 不可用（非 Chrome 环境）或容器启动失败，返回 null，
     * captchaSolver 会回落到全局配置的 CDP 端点（127.0.0.1:9222）。</p>
     * <p>这是解决「无法连接 CDP 浏览器：null」的关键：
     * 用户没手动用 {@code --remote-debugging-port=9222} 启动 Chrome 时，
     * 系统按账号自动拉起一个独占 Chrome 容器（独立 profile dir + CDP 端口 + 代理 + 指纹 seed）。</p>
     */
    private String ensureAccountCdpEndpoint(XianyuAccount acc) {
        if (accountService == null) {
            log.warn("[MESSAGE] AccountService not injected, captchaSolver will fall back to global CDP endpoint");
            return null;
        }
        try {
            long accountId = acc.getId();
            // 容器已存活 → 直接用账号记录里的 cdpPort
            if (accountService.isChromeAlive(accountId) && acc.getCdpPort() != null && acc.getCdpPort() > 0) {
                String endpoint = "http://127.0.0.1:" + acc.getCdpPort();
                log.info("[MESSAGE] reuse alive Chrome container for account {}, cdpEndpoint={}",
                        accountId, endpoint);
                return endpoint;
            }
            // 容器未启动或已崩溃 → 启动该账号独占容器
            boolean launched = accountService.launchChromeContainer(acc);
            if (!launched || acc.getCdpPort() == null || acc.getCdpPort() <= 0) {
                log.warn("[MESSAGE] launchChromeContainer failed for account {}, fall back to global CDP", accountId);
                return null;
            }
            String endpoint = "http://127.0.0.1:" + acc.getCdpPort();
            log.info("[MESSAGE] launched Chrome container for account {}, cdpEndpoint={}",
                    accountId, endpoint);
            return endpoint;
        } catch (Exception e) {
            log.warn("[MESSAGE] ensureAccountCdpEndpoint failed for account {}: {}",
                    acc.getId(), e.getMessage());
            return null;
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

    private String firstTextDeep(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        String direct = firstText(node, fields);
        if (!direct.isEmpty()) return direct;
        if (node.isObject()) {
            for (java.util.Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                String nested = firstTextDeep(it.next(), fields);
                if (!nested.isEmpty()) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = firstTextDeep(child, fields);
                if (!nested.isEmpty()) return nested;
            }
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
        // MTOP 结构：sender 信息在嵌套 sender 对象里（sender.uid / sender.nick / sender.avatar），
        // 不是顶层 senderId/senderNick。旧实现取顶层导致 senderId 永远为空，direction 判错。
        JsonNode senderNode = msg.path("sender");
        entity.setSenderId(firstText(senderNode, "uid", "userId", "senderId", "senderUserId", "id"));
        if (entity.getSenderId().isEmpty()) {
            entity.setSenderId(msg.path("senderId").asText(""));
        }
        if (entity.getSenderId().isEmpty()) {
            entity.setSenderId(msg.path("senderUserId").asText(""));
        }
        if (entity.getSenderId().isEmpty() && !senderNode.isMissingNode()) {
            entity.setSenderId(senderNode.path("id").asText(""));
        }
        entity.setSenderName(firstText(senderNode, "nick", "name", "displayName", "senderNickName", "senderNick"));
        if (entity.getSenderName().isEmpty()) {
            entity.setSenderName(msg.path("senderNick").asText(""));
        }
        entity.setSenderAvatar(firstTextDeep(msg,
                "avatar", "avatarUrl", "headUrl", "headImg", "headPic", "userAvatar", "userAvatarUrl",
                "senderAvatar", "senderAvatarUrl", "senderHeadUrl", "logo", "portrait", "displayPic"));
        // direction: 不能用 senderRole（闲鱼 MTOP 里对方发的消息 senderRole=SENDER，自己发的=RECEIVER，
        // 直接判会把对方发的当成 OUTGOING，方向全反）。必须用 selfUserId 比对：senderId == selfId → OUTGOING。
        String selfUserId = extractSelfUserId(accountId);
        String senderId = entity.getSenderId();
        boolean isOutgoing = !selfUserId.isEmpty() && selfUserId.equals(senderId);
        entity.setDirection(isOutgoing ? "OUTGOING" : "INCOMING");
        entity.setMsgType(msg.path("msgType").asText("TEXT"));
        entity.setAutoReply(Boolean.TRUE.equals(msg.path("isAutoReply").asBoolean()));
        // 用闲鱼返回的真实时间，不要写死 now()，否则历史消息时间全是当下
        entity.setMessageTime(parseMessageTime(msg));

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
        // CDP 校验 2026-07-21：闲鱼 LWP 真实结构里发送方信息在两处：
        //   - messageNode.sender.uid = "2221026227266@goofish"（带 @goofish 后缀的完整 uid）
        //   - extension.senderUserId = "2221026227266"（裸 id）
        //   - messageNode.sender.nick / sender.avatar（昵称和头像在 sender 嵌套对象里）
        // 旧实现 firstText(messageNode,"senderId","senderUserId","uid") 在顶层找不到这些字段，
        // 导致 senderId 永远为空，direction 判错（全部 INCOMING），头像昵称也拿不到。
        JsonNode senderNode = messageNode.path("sender");
        entity.setSenderId(firstText(senderNode, "uid", "userId", "senderId", "senderUserId", "id"));
        if (entity.getSenderId().isEmpty()) {
            entity.setSenderId(firstText(messageNode, "senderId", "senderUserId", "uid"));
        }
        if (entity.getSenderId().isEmpty()) {
            // extension.senderUserId 是裸 id（不带 @goofish），补后缀与对话方向判定对齐
            String bareUid = firstText(extension, "senderUserId", "senderId");
            if (!bareUid.isEmpty()) {
                entity.setSenderId(bareUid.contains("@") ? bareUid : bareUid + "@goofish");
            }
        }
        // 昵称：sender.nick / sender.name / sender.displayName（嵌套在 sender 对象里）
        entity.setSenderName(firstText(senderNode, "nick", "name", "displayName", "senderNickName", "senderNick"));
        if (entity.getSenderName().isEmpty()) {
            entity.setSenderName(firstTextDeep(msg,
                    "senderNickName", "senderNick", "senderName", "nick", "nickname", "displayName"));
        }
        if (entity.getSenderName().isEmpty()) {
            entity.setSenderName(firstText(extension, "reminderTitle", "senderNickName", "senderNick"));
        }
        if (entity.getSenderName().isEmpty()) {
            entity.setSenderName(firstTextDeep(extension.path("sender"),
                    "nick", "nickname", "displayName", "name"));
        }
        // 头像：sender.avatar / sender.avatarUrl / sender.head（嵌套在 sender 对象里）
        entity.setSenderAvatar(firstText(senderNode,
                "avatar", "avatarUrl", "head", "headUrl", "headImg", "portrait", "logo", "icon"));
        if (entity.getSenderAvatar().isEmpty()) {
            entity.setSenderAvatar(firstTextDeep(msg,
                    "avatar", "avatarUrl", "headUrl", "headImg", "headPic", "userAvatar", "userAvatarUrl",
                    "senderAvatar", "senderAvatarUrl", "senderHeadUrl", "logo", "portrait", "displayPic",
                    "senderIcon", "senderLogo"));
        }
        if (entity.getSenderAvatar().isEmpty()) {
            entity.setSenderAvatar(firstTextDeep(extension.path("sender"),
                    "avatar", "avatarUrl", "head", "headUrl", "headImg", "portrait", "logo", "icon"));
        }
        if (entity.getSenderAvatar().isEmpty()) {
            entity.setSenderAvatar(firstTextDeep(extension,
                    "senderAvatar", "senderAvatarUrl", "senderHeadUrl", "avatar", "headUrl"));
        }

        // direction: senderId == selfId（账号 cookie 里的 userId）是 OUTGOING，否则 INCOMING
        // 原 bug：写死 INCOMING，导致自己发的消息被当进来消息，会话列表全是自己回自己的怪状
        String selfUserId = extractSelfUserId(accountId);
        String senderId = entity.getSenderId();
        boolean isOutgoing = !selfUserId.isEmpty() && selfUserId.equals(senderId);
        entity.setDirection(isOutgoing ? "OUTGOING" : "INCOMING");
        entity.setAutoReply(false);
        // messageTime: 用闲鱼返回的真实时间（msgTime / createTime / timestamp 字段，毫秒级 Unix）
        // 原 bug：写死 LocalDateTime.now()，导致所有消息时间戳错位（同步历史消息时间全是当下）
        entity.setMessageTime(parseMessageTime(messageNode));

        // 解析 content：闲鱼 IM 消息内容封装在 content 里，
        // - contentType=1：文本，custom.data 是 base64({"text":{"text":"..."}})
        // - contentType=2/3/4...：图片/视频/语音，custom.data 是 base64 JSON 含 url/path/pix
        // 旧实现只认 text.text，导致图片/视频消息 content 解码失败落空，丢失同步
        entity.setMsgType("TEXT");
        entity.setContent(parseLwpContent(messageNode, extension, entity));

        messageMapper.insert(entity);
        if ("INCOMING".equals(entity.getDirection())) {
            eventPublisher.publishEvent(new NotifyEvent(this, entity, "NEW_MESSAGE"));
        }
        log.info("[MESSAGE] pulled lwp msg {} in session {} from account {} (dir={}, type={})",
                msgId, cid, accountId, entity.getDirection(), entity.getMsgType());
    }

    /**
     * 解析 LWP 消息 content 字段，兼容 文本/图片/视频/语音/系统消息。
     * <p>闲鱼 IM content 结构：</p>
     * <ul>
     *   <li>{@code contentType=1}：文本消息，{@code custom.data} 是 base64 编码的 JSON
     *       {@code {"text":{"text":"消息内容"}}}（contentType 内层也可能是 1）</li>
     *   <li>{@code contentType=2/3/101}：图片/视频消息，{@code custom.data} 解码后含
     *       {@code url} / {@code path} / {@code pix} / {@code width} / {@code height} 等</li>
     *   <li>{@code contentType=4/5}：语音/文件</li>
     *   <li>系统消息（reminder 类）：{@code extension.reminderContent}</li>
     * </ul>
     *
     * @return 解析后的展示内容；图片/视频返回 CDN URL，便于前端直接渲染
     */
    private String parseLwpContent(JsonNode messageNode, JsonNode extension, XianyuMessage entity) {
        JsonNode content = messageNode.path("content");
        int contentType = content.path("contentType").asInt(0);

        // 1. custom.data base64 -> JSON（文本/图片/视频通用封装）
        if (content.has("custom") && content.path("custom").has("data")) {
            String decodedText = "";
            try {
                byte[] decoded = java.util.Base64.getDecoder().decode(content.path("custom").path("data").asText());
                decodedText = new String(decoded, StandardCharsets.UTF_8);
                JsonNode plain = MAPPER.readTree(decodedText);

                // contentType==1 文本：{"text":{"text":"..."}}
                if (contentType == 1 || contentType == 0) {
                    String text = plain.path("text").path("text").asText("");
                    if (!text.isEmpty()) {
                        entity.setMsgType("TEXT");
                        return text;
                    }
                }

                // 图片消息：{"image":{"url":"http://...","width":W,"height":H}}
                // 视频消息：{"video":{"url":"http://...","duration":D,"pic":"http://封面"}}
                // 语音消息：{"audio":{"url":"http://...","duration":D}}
                // 通用兜底：直接取 url/path 字段
                String mediaUrl = firstTextDeep(plain,
                        "url", "path", "imageUrl", "videoUrl", "audioUrl", "mediaUrl", "fileUrl");
                String mediaPic = firstTextDeep(plain, "pic", "cover", "coverUrl", "thumbnail", "poster");
                long duration = plain.path("video").path("duration").asLong(0);
                if (duration == 0) duration = plain.path("audio").path("duration").asLong(0);

                // 根据 contentType 判定类型；contentType==0 时尝试根据 JSON 子节点名推断
                String detectedType = detectMediaType(contentType, plain);
                if ("IMAGE".equals(detectedType)) {
                    entity.setMsgType("IMAGE");
                    return mediaUrl.isEmpty() ? decodedText : mediaUrl;
                }
                if ("VIDEO".equals(detectedType)) {
                    entity.setMsgType("VIDEO");
                    StringBuilder sb = new StringBuilder();
                    sb.append(mediaUrl.isEmpty() ? decodedText : mediaUrl);
                    if (!mediaPic.isEmpty()) sb.append("|poster=").append(mediaPic);
                    if (duration > 0) sb.append("|duration=").append(duration);
                    return sb.toString();
                }
                if ("AUDIO".equals(detectedType)) {
                    entity.setMsgType("AUDIO");
                    return mediaUrl.isEmpty() ? decodedText : mediaUrl + (duration > 0 ? "|duration=" + duration : "");
                }
                if ("FILE".equals(detectedType)) {
                    entity.setMsgType("FILE");
                    return mediaUrl.isEmpty() ? decodedText : mediaUrl;
                }

                // 兜底：尝试 text.text
                String text = plain.path("text").path("text").asText("");
                if (!text.isEmpty()) {
                    entity.setMsgType("TEXT");
                    return text;
                }
                // 解码后没有可用字段，保留原始 base64 解码结果（便于排障）
                return decodedText;
            } catch (Exception e) {
                // base64 解码或 JSON 解析失败：保留原始 custom.data 文本，避免丢消息
                return content.path("custom").path("data").asText("");
            }
        }

        // 2. 直接 text 字段（非 custom 封装）
        if (content.has("text")) {
            entity.setMsgType("TEXT");
            return content.path("text").asText("");
        }
        if (content.has("body") && content.path("body").has("text")) {
            entity.setMsgType("TEXT");
            return content.path("body").path("text").asText("");
        }

        // 3. 系统提醒类消息（订单提醒、加购提醒等）：取 extension.reminderContent / summary
        String reminder = firstText(extension, "reminderContent", "summary", "content", "reminderTitle");
        if (!reminder.isEmpty()) {
            entity.setMsgType("SYSTEM");
            return reminder;
        }

        // 4. 完全无内容时，存 contentType 元信息便于排障
        return content.asText("");
    }

    /** 根据 contentType 和 JSON 子节点名推断媒体类型 */
    private String detectMediaType(int contentType, JsonNode plain) {
        // 闲鱼已知 contentType：1=文本, 2=图片, 3=语音, 4=视频, 5=文件, 101=系统/卡片
        switch (contentType) {
            case 2: return "IMAGE";
            case 3: return "AUDIO";
            case 4: return "VIDEO";
            case 5: return "FILE";
            case 1: return "TEXT";
            default: break;
        }
        // contentType 未知时根据 JSON 子节点名推断
        if (plain.has("image")) return "IMAGE";
        if (plain.has("video")) return "VIDEO";
        if (plain.has("audio")) return "AUDIO";
        if (plain.has("file")) return "FILE";
        return "TEXT";
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
        // CDP 校验 2026-07-21：真实帧字段名是 createAt（不是 createTime）
        String[] candidates = {"createAt", "msgTime", "createTime", "timestamp", "time", "createTimeInMs"};
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

        // CDP 校验 2026-07-21：sendByReceiverScope 的 actualReceivers 必须是
        // [对方userId@goofish, 自己userId@goofish]，用 cid 兜底会被服务端拒绝。
        // selfUserId 从账号 cookie 的 unb 字段提取；
        // peerUserId 从该会话最新一条 INCOMING 消息的 senderId 取。
        String selfUserId = extractSelfUserId(request.getAccountId());
        if (selfUserId.isBlank()) {
            throw new IllegalStateException("无法从账号 cookie 提取 selfUserId，请重新登录");
        }
        XianyuMessage peerMsg = messageMapper.selectLatestIncoming(request.getAccountId(), request.getSessionId());
        String peerUserId = peerMsg != null ? peerMsg.getSenderId() : "";
        if (peerUserId.isBlank()) {
            // 该会话还没收到过对方消息（刚发起的会话），用 sessionId 兜底
            // sessionId 形如 "57783854401@goofish"，去掉 @goofish 后缀就是对方 userId
            String sid = request.getSessionId() != null ? request.getSessionId() : "";
            peerUserId = sid.contains("@") ? sid.substring(0, sid.indexOf('@')) : sid;
        }
        if (peerUserId.isBlank()) {
            throw new IllegalStateException("无法确定对方 userId，请先同步该会话消息");
        }

        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
        // IM/滑块 cookie（x5sec）与登录 cookie 合并，用于 IM 连接与 MTOP 风控后重试
        if (acc.getImCookieHeader() != null && !acc.getImCookieHeader().isBlank()) {
            mtopClient.setImCookieHeader(acc.getImCookieHeader());
        }
        XianyuMessageApiService msgApi = new XianyuMessageApiService(mtopClient);

        // 通过 IM 长连接发送文本消息
        // 风控链路：ensureAccs/connect 拿 token 被 punish → 抛 IllegalStateException(含 punish URL)
        // → 抽 URL 调 captchaSolver → 拿到 x5sec 写入 imCookieHeader → 重建 mtopClient + msgApi 重试
        JsonNode sendResp = null;
        String lastRiskError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                sendResp = msgApi.sendMessage(request.getSessionId(), request.getContent(), selfUserId, peerUserId);
                break;
            } catch (IllegalStateException ie) {
                String msg = ie.getMessage() != null ? ie.getMessage() : "";
                boolean isRisk = msg.contains("FAIL_SYS_USER_VALIDATE") || msg.contains("punish")
                        || msg.contains("captcha") || msg.contains("RGV587_ERROR");
                if (!isRisk || attempt > 0) {
                    // 非风控异常，或重试后仍失败：原样抛
                    throw ie;
                }
                lastRiskError = msg;
                log.warn("[MESSAGE] sendMessage hit risk control on attempt {}, solving captcha...", attempt + 1);
                boolean solved = solveCaptchaForSend(acc, msg);
                if (!solved) {
                    // 滑块没解出来：把 punish URL 带回去给前端，而不是抱一个赤裸的 IllegalStateException
                    throw new IllegalStateException("发送失败：闲鱼风控拦截，滑块未通过。错误摘要："
                            + truncate(msg, 300), ie);
                }
                // 滑块通过：用新 cookie 重建 mtopClient / msgApi，下一轮重试发送
                mtopClient = new XianyuMtopApiClient(acc.getCookieHeader());
                mtopClient.setImCookieHeader(acc.getImCookieHeader());
                msgApi = new XianyuMessageApiService(mtopClient);
            }
        }
        if (sendResp == null) {
            // 重试后仍拿不到响应（理论上上面 catch 已抛过），保险兜底
            throw new IllegalStateException("sendMessage returned null after retry, lastRisk=" + truncate(lastRiskError, 200));
        }
        // 异步帧立即返回合成 ack（code=200, async=true），发送是否真正成功要靠
        // pushListener 监听 sendByReceiverScope 回执或买家侧确认。
        // 这里只要 WSS 写出没抛异常就认为「已发出」，落本地 OUTGOING 记录。

        // 构造本地落库记录
        XianyuMessage msg = new XianyuMessage();
        msg.setAccountId(request.getAccountId());
        msg.setSessionId(request.getSessionId());
        // 生成客户端 msgId（与闲鱼服务端 msgId 不同），避免与 pushListener 拉回的消息冲突
        msg.setMsgId("local-out-" + System.currentTimeMillis() + "-" + request.getAccountId());
        msg.setSenderId(selfUserId);
        msg.setSenderName(acc.getDisplayName() != null ? acc.getDisplayName() : acc.getAccountName());
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
