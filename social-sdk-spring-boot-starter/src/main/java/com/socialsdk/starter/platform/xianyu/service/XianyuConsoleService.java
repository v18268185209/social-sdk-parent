package com.socialsdk.starter.platform.xianyu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsdk.core.constant.SocialPlatform;
import com.socialsdk.core.model.PostResult;
import com.socialsdk.core.model.SocialContent;
import com.socialsdk.core.model.SocialSession;
import com.socialsdk.core.model.SocialUserProfile;
import com.socialsdk.core.provider.SocialProvider;
import com.socialsdk.starter.config.SocialSdkAutoConfiguration;
import com.socialsdk.starter.platform.xianyu.config.XianyuConsoleProperties;
import com.socialsdk.starter.platform.xianyu.dto.AccountCookieLoginRequest;
import com.socialsdk.starter.platform.xianyu.dto.AccountCookieUpdateRequest;
import com.socialsdk.starter.platform.xianyu.dto.AccountStatusUpdateRequest;
import com.socialsdk.starter.platform.xianyu.dto.ChatTakeoverRequest;
import com.socialsdk.starter.platform.xianyu.dto.HeadlessQrLoginCreateRequest;
import com.socialsdk.starter.platform.xianyu.dto.KeywordRuleUpsertRequest;
import com.socialsdk.starter.platform.xianyu.dto.MessageSendRequest;
import com.socialsdk.starter.platform.xianyu.dto.ProductUpsertRequest;
import com.socialsdk.starter.platform.xianyu.dto.RuleMatchRequest;
import com.socialsdk.starter.platform.xianyu.model.XianyuAccountEntity;
import com.socialsdk.starter.platform.xianyu.model.XianyuKeywordRuleEntity;
import com.socialsdk.starter.platform.xianyu.model.XianyuLoginSnapshotEntity;
import com.socialsdk.starter.platform.xianyu.model.XianyuProductEntity;
import com.socialsdk.starter.platform.xianyu.repository.XianyuAccountRepository;
import com.socialsdk.starter.platform.xianyu.repository.XianyuKeywordRuleRepository;
import com.socialsdk.starter.platform.xianyu.repository.XianyuLoginSnapshotRepository;
import com.socialsdk.starter.platform.xianyu.repository.XianyuProductRepository;
import com.socialsdk.xianyu.model.XianyuCredentials;
import com.socialsdk.xianyu.model.XianyuHeadlessQrLoginSession;
import com.socialsdk.xianyu.model.XianyuMessage;
import com.socialsdk.xianyu.service.XianyuProvider;
import com.socialsdk.xianyu.service.XianyuRealtimeClient;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XianyuConsoleService {

    private static final Pattern VERIFICATION_URL_PATTERN = Pattern.compile("https://[^\\s\"']+/punish\\?[^\\s\"']+");
    private static final int MAX_CHAT_EVENTS = 500;
    public static final String ACCOUNT_STATUS_ACTIVE = "ACTIVE";
    public static final String ACCOUNT_STATUS_PENDING_VERIFY = "PENDING_VERIFY";
    public static final String ACCOUNT_STATUS_FAILED = "FAILED";

    private final XianyuConsoleProperties properties;
    private final ObjectMapper objectMapper;
    private final XianyuAccountRepository accountRepository;
    private final XianyuProductRepository productRepository;
    private final XianyuKeywordRuleRepository keywordRuleRepository;
    private final XianyuLoginSnapshotRepository loginSnapshotRepository;
    private final SocialSdkAutoConfiguration.SocialProviderManager providerManager;
    private final ConcurrentMap<String, HeadlessQrSessionContext> headlessQrSessionContexts = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ChatTakeoverContext> chatTakeovers = new ConcurrentHashMap<>();

    public XianyuConsoleService(
            XianyuConsoleProperties properties,
            ObjectMapper objectMapper,
            XianyuAccountRepository accountRepository,
            XianyuProductRepository productRepository,
            XianyuKeywordRuleRepository keywordRuleRepository,
            XianyuLoginSnapshotRepository loginSnapshotRepository,
            SocialSdkAutoConfiguration.SocialProviderManager providerManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.keywordRuleRepository = keywordRuleRepository;
        this.loginSnapshotRepository = loginSnapshotRepository;
        this.providerManager = providerManager;
    }

    public Map<String, Object> health() {
        List<XianyuAccountEntity> accounts = accountRepository.findAll();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("platform", "xianyu");
        data.put("sqlitePath", properties.getSqlitePath());
        data.put("accounts", accounts.size());
        data.put("products", productRepository.findByAccountId(null).size());
        data.put("rules", keywordRuleRepository.findByAccountId(null).size());
        data.put("accountStatusSummary", summarizeAccountStatus(accounts));
        data.put("timestamp", Instant.now().toString());
        return data;
    }

    public List<XianyuAccountEntity> listAccounts() {
        return accountRepository.findAll();
    }

    public Optional<XianyuAccountEntity> getAccount(long id) {
        return accountRepository.findById(id);
    }

    public XianyuAccountEntity loginWithCookies(AccountCookieLoginRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        Map<String, String> cookies = normalizeCookies(request.getCookieHeader(), request.getCookies());
        boolean allowManualLogin = request.getAllowManualLogin() != null && request.getAllowManualLogin();
        if (cookies.isEmpty() && !allowManualLogin) {
            throw new IllegalArgumentException("cookieHeader or cookies is required");
        }

        XianyuProvider provider = requireXianyuProvider();
        XianyuCredentials credentials = new XianyuCredentials();
        credentials.setCookies(cookies);
        credentials.setCookieHeader(buildCookieHeader(cookies));
        credentials.setAllowManualLogin(allowManualLogin);
        if (request.getLoginTimeoutSeconds() != null && request.getLoginTimeoutSeconds() > 0) {
            credentials.setLoginTimeoutSeconds(request.getLoginTimeoutSeconds());
        }
        if (StringUtils.hasText(request.getStartUrl())) {
            credentials.setStartUrl(request.getStartUrl().trim());
        }

        SocialSession session;
        try {
            session = provider.authenticate(credentials);
        } catch (Exception e) {
            Map<String, Object> verification = extractVerificationHint(e.getMessage());
            recordLoginFailure(request, cookies, e.getMessage(), verification);
            String url = asString(verification.get("verificationUrl"));
            if (StringUtils.hasText(url) && properties.isAutoOpenVerificationUrl()) {
                openVerificationUrl(url);
            }
            throw e;
        }

        Map<String, String> finalCookies = extractCookiesFromSessionRawData(session.getRawData());
        if (finalCookies.isEmpty()) {
            finalCookies = cookies;
        }

        XianyuAccountEntity entity = new XianyuAccountEntity();
        entity.setPlatform(SocialPlatform.XIANYU.getCode());
        entity.setAccountName(firstNonBlank(request.getAccountName(), decodeCookieValue(finalCookies.get("tracknick")), session.getUserId()));
        entity.setUserId(firstNonBlank(session.getUserId(), finalCookies.get("unb"), finalCookies.get("cookie2")));
        entity.setDisplayName(firstNonBlank(decodeCookieValue(finalCookies.get("tracknick")), entity.getAccountName()));
        entity.setCookieHeader(buildCookieHeader(finalCookies));
        entity.setCookiesJson(toJson(finalCookies));
        entity.setSessionRawData(session.getRawData());
        entity.setStatus(ACCOUNT_STATUS_ACTIVE);
        entity.setRemark(request.getRemark());
        entity.setLastError(null);
        entity.setLastLoginAt(Instant.now());
        return upsertAccount(entity);
    }

    public Map<String, Object> createHeadlessQrLoginSession(HeadlessQrLoginCreateRequest request) {
        XianyuProvider provider = requireXianyuProvider();
        XianyuHeadlessQrLoginSession session = provider.createHeadlessQrLoginSession(
                request == null ? null : trimToNull(request.getLoginUrl()),
                request == null ? null : request.getExpiresInSeconds());

        HeadlessQrSessionContext context = new HeadlessQrSessionContext();
        context.accountName = request == null ? null : trimToNull(request.getAccountName());
        context.remark = request == null ? null : trimToNull(request.getRemark());
        headlessQrSessionContexts.put(session.getSessionId(), context);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", session);
        data.put("accountPersisted", false);
        return data;
    }

    public Map<String, Object> getHeadlessQrLoginSessionStatus(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }

        XianyuProvider provider = requireXianyuProvider();
        XianyuHeadlessQrLoginSession session = provider.getHeadlessQrLoginSessionStatus(sessionId.trim());
        if (session == null) {
            throw new IllegalStateException("headless qr login session is null");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", session);
        data.put("accountPersisted", false);
        data.put("accountId", null);
        data.put("snapshotId", null);

        if (XianyuHeadlessQrLoginSession.STATUS_NOT_FOUND.equals(session.getStatus())) {
            headlessQrSessionContexts.remove(sessionId.trim());
            return data;
        }

        HeadlessQrSessionContext context = headlessQrSessionContexts.computeIfAbsent(
                sessionId.trim(),
                key -> new HeadlessQrSessionContext());

        if (XianyuHeadlessQrLoginSession.STATUS_SUCCESS.equals(session.getStatus())) {
            synchronized (context.lock) {
                if (!context.persisted) {
                    persistHeadlessQrSuccess(sessionId.trim(), session, context);
                }
            }
            data.put("accountPersisted", context.persisted);
            data.put("accountId", context.accountId);
            data.put("snapshotId", context.snapshotId);
        }

        if (XianyuHeadlessQrLoginSession.STATUS_EXPIRED.equals(session.getStatus())
                || XianyuHeadlessQrLoginSession.STATUS_ERROR.equals(session.getStatus())) {
            if (!context.persisted) {
                headlessQrSessionContexts.remove(sessionId.trim());
            }
        }

        return data;
    }

    public boolean invalidateHeadlessQrLoginSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        headlessQrSessionContexts.remove(sessionId.trim());
        return requireXianyuProvider().invalidateHeadlessQrLoginSession(sessionId.trim());
    }

    public XianyuAccountEntity updateAccountCookies(long id, AccountCookieUpdateRequest request) {
        XianyuAccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + id));

        Map<String, String> cookies = normalizeCookies(request == null ? null : request.getCookieHeader(),
                request == null ? null : request.getCookies());

        if (!cookies.isEmpty()) {
            entity.setCookieHeader(buildCookieHeader(cookies));
            entity.setCookiesJson(toJson(cookies));
            entity.setStatus(ACCOUNT_STATUS_ACTIVE);
            entity.setLastError(null);
            entity.setLastLoginAt(Instant.now());
        }
        if (request != null && request.getRemark() != null) {
            entity.setRemark(request.getRemark());
        }

        accountRepository.update(entity);
        return accountRepository.findById(id).orElse(entity);
    }

    public XianyuAccountEntity updateAccountStatus(long id, AccountStatusUpdateRequest request) {
        XianyuAccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + id));
        if (request == null || !StringUtils.hasText(request.getStatus())) {
            throw new IllegalArgumentException("status is required");
        }
        String normalizedStatus = normalizeAccountStatus(request.getStatus());
        if (!isSupportedAccountStatus(normalizedStatus)) {
            throw new IllegalArgumentException("unsupported status: " + request.getStatus());
        }

        entity.setStatus(normalizedStatus);
        entity.setLastError(trimToNull(request.getLastError()));
        if (ACCOUNT_STATUS_ACTIVE.equals(normalizedStatus)) {
            entity.setLastError(null);
            entity.setLastLoginAt(Instant.now());
        }
        accountRepository.update(entity);
        return accountRepository.findById(id).orElse(entity);
    }

    public boolean deleteAccount(long id) {
        return accountRepository.deleteById(id);
    }

    public Map<String, Object> refreshAccountProfile(long id) throws Exception {
        XianyuAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + id));

        Map<String, String> cookies = extractCookiesFromAccount(account);
        if (cookies.isEmpty()) {
            throw new IllegalStateException("account cookies are empty");
        }

        XianyuProvider provider = requireXianyuProvider();
        XianyuCredentials credentials = new XianyuCredentials();
        credentials.setCookies(cookies);
        credentials.setCookieHeader(buildCookieHeader(cookies));
        credentials.setAllowManualLogin(false);

        SocialSession session;
        SocialUserProfile profile;
        try {
            session = provider.authenticate(credentials);
            profile = provider.getUserProfile(session);
        } catch (Exception e) {
            Map<String, Object> verification = extractVerificationHint(e.getMessage());
            updateFailureStatus(account, e.getMessage(), verification);
            String url = asString(verification.get("verificationUrl"));
            if (StringUtils.hasText(url) && properties.isAutoOpenVerificationUrl()) {
                openVerificationUrl(url);
            }
            throw e;
        }

        Map<String, String> refreshedCookies = extractCookiesFromSessionRawData(session.getRawData());
        if (refreshedCookies.isEmpty()) {
            refreshedCookies = cookies;
        }

        account.setUserId(firstNonBlank(profile.getUserId(), session.getUserId(), account.getUserId()));
        account.setDisplayName(firstNonBlank(profile.getDisplayName(), profile.getUsername(), account.getDisplayName()));
        account.setCookieHeader(buildCookieHeader(refreshedCookies));
        account.setCookiesJson(toJson(refreshedCookies));
        account.setSessionRawData(session.getRawData());
        account.setStatus(ACCOUNT_STATUS_ACTIVE);
        account.setLastError(null);
        account.setLastLoginAt(Instant.now());
        accountRepository.update(account);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("account", accountRepository.findById(id).orElse(account));
        data.put("profile", profile);
        return data;
    }

    public Map<String, Object> sendMessage(MessageSendRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getAccountId() == null || request.getAccountId() <= 0) {
            throw new IllegalArgumentException("accountId is required");
        }
        boolean hasText = StringUtils.hasText(request.getText());
        boolean hasImage = StringUtils.hasText(request.getImageUrl()) || StringUtils.hasText(request.getImagePath());
        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("text or imageUrl/imagePath is required");
        }
        if (!StringUtils.hasText(request.getToUserId())) {
            throw new IllegalArgumentException("toUserId is required");
        }

        XianyuAccountEntity account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + request.getAccountId()));
        Map<String, String> cookies = extractCookiesFromAccount(account);
        if (cookies.isEmpty()) {
            throw new IllegalStateException("account cookies are empty");
        }

        SocialSession session = new SocialSession(SocialPlatform.XIANYU, account.getUserId(),
                "xianyu-session-" + firstNonBlank(account.getUserId(), String.valueOf(account.getId())));
        session.setRawData(StringUtils.hasText(account.getSessionRawData())
                ? account.getSessionRawData()
                : buildSimpleSessionRawData(cookies, account.getUserId()));

        Map<String, Object> rawData = new LinkedHashMap<>();
        rawData.put("toUserId", request.getToUserId().trim());
        rawData.put("itemId", trimToNull(request.getItemId()));
        rawData.put("cid", trimToNull(request.getCid()));
        rawData.put("useRealtime", request.getUseRealtime() == null || request.getUseRealtime());
        rawData.put("imageUrl", trimToNull(request.getImageUrl()));
        rawData.put("imagePath", trimToNull(request.getImagePath()));
        rawData.put("imageWidth", request.getImageWidth() == null ? 800 : request.getImageWidth());
        rawData.put("imageHeight", request.getImageHeight() == null ? 600 : request.getImageHeight());

        SocialContent content = new SocialContent();
        content.setText(trimToNull(request.getText()));
        if (StringUtils.hasText(request.getImageUrl())) {
            content.setImageUrls(List.of(request.getImageUrl().trim()));
        }
        content.setRawData(toJson(rawData));

        XianyuProvider provider = requireXianyuProvider();
        try {
            PostResult result = provider.postContent(session, content);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accountId", account.getId());
            data.put("result", result);
            appendManualSendEventIfTakeoverRunning(account.getId(), request, result);
            return data;
        } catch (Exception e) {
            Map<String, Object> verification = extractVerificationHint(e.getMessage());
            updateFailureStatus(account, e.getMessage(), verification);
            String url = asString(verification.get("verificationUrl"));
            if (StringUtils.hasText(url) && properties.isAutoOpenVerificationUrl()) {
                openVerificationUrl(url);
            }
            throw e;
        }
    }

    public Object getTimeline(long accountId, int limit) throws Exception {
        XianyuAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + accountId));
        Map<String, String> cookies = extractCookiesFromAccount(account);
        if (cookies.isEmpty()) {
            throw new IllegalStateException("account cookies are empty");
        }
        SocialSession session = new SocialSession(SocialPlatform.XIANYU, account.getUserId(),
                "xianyu-session-" + firstNonBlank(account.getUserId(), String.valueOf(account.getId())));
        session.setRawData(StringUtils.hasText(account.getSessionRawData())
                ? account.getSessionRawData()
                : buildSimpleSessionRawData(cookies, account.getUserId()));
        return requireXianyuProvider().getTimeline(session, limit);
    }

    public Map<String, Object> startChatTakeover(ChatTakeoverRequest request) throws Exception {
        if (request == null || request.getAccountId() == null || request.getAccountId() <= 0) {
            throw new IllegalArgumentException("accountId is required");
        }
        long accountId = request.getAccountId();
        boolean autoReply = request.getAutoReply() != null && request.getAutoReply();
        XianyuAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + accountId));
        ChatTakeoverContext context = chatTakeovers.computeIfAbsent(accountId, id -> new ChatTakeoverContext(accountId));
        synchronized (context.lock) {
            context.autoReply = autoReply;
            if (context.client != null && context.client.isRunning()) {
                return chatTakeoverSummary(context, "chat takeover already running");
            }
            SocialSession session = buildSessionFromAccount(account);
            XianyuProvider provider = requireXianyuProvider();
            XianyuRealtimeClient client = provider.subscribeMessages(session, new XianyuRealtimeClient.MessageListener() {
                @Override
                public void onMessage(XianyuMessage message) {
                    Map<String, Object> payload = messageToEvent(accountId, message);
                    appendChatEvent(context, payload);
                    if (context.autoReply && message != null && !message.isOutgoing() && StringUtils.hasText(message.getContent())) {
                        handleAutoReply(context, message);
                    }
                }

                @Override
                public void onSystemMessage(Map<String, Object> message) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "system");
                    payload.put("accountId", accountId);
                    payload.put("message", message);
                    payload.put("timestamp", Instant.now().toString());
                    appendChatEvent(context, payload);

                    for (Map<String, Object> hint : conversationHintsFromSystemMessage(accountId, message)) {
                        appendChatEvent(context, hint);
                        fetchConversationMessagesAsync(context, account, provider, asString(hint.get("chatId")));
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "error");
                    payload.put("accountId", accountId);
                    payload.put("message", throwable == null ? "unknown" : throwable.getMessage());
                    payload.put("timestamp", Instant.now().toString());
                    appendChatEvent(context, payload);
                }
            });
            context.client = client;
            context.startedAt = Instant.now();
            return chatTakeoverSummary(context, "chat takeover started");
        }
    }

    public Map<String, Object> stopChatTakeover(long accountId) {
        ChatTakeoverContext context = chatTakeovers.get(accountId);
        if (context == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accountId", accountId);
            data.put("running", false);
            data.put("message", "chat takeover not found");
            return data;
        }
        synchronized (context.lock) {
            if (context.client != null) {
                context.client.close();
                context.client = null;
            }
            closeChatEmitters(context);
            return chatTakeoverSummary(context, "chat takeover stopped");
        }
    }

    public Map<String, Object> getChatTakeoverStatus(Long accountId) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (accountId != null) {
            ChatTakeoverContext context = chatTakeovers.get(accountId);
            return context == null ? chatTakeoverNotRunning(accountId) : chatTakeoverSummary(context, "OK");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatTakeoverContext context : chatTakeovers.values()) {
            items.add(chatTakeoverSummary(context, "OK"));
        }
        data.put("items", items);
        data.put("count", items.size());
        return data;
    }

    public List<Map<String, Object>> listChatEvents(long accountId) {
        ChatTakeoverContext context = chatTakeovers.get(accountId);
        if (context == null) {
            return Collections.emptyList();
        }
        synchronized (context.events) {
            return new ArrayList<>(context.events);
        }
    }

    public SseEmitter openChatStream(long accountId) {
        ChatTakeoverContext context = chatTakeovers.get(accountId);
        SseEmitter emitter = new SseEmitter(0L);
        if (context == null) {
            sendChatSse(emitter, "status", chatTakeoverNotRunning(accountId));
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }
        context.emitters.add(emitter);
        emitter.onCompletion(() -> context.emitters.remove(emitter));
        emitter.onTimeout(() -> context.emitters.remove(emitter));
        emitter.onError(error -> context.emitters.remove(emitter));
        sendChatSse(emitter, "status", chatTakeoverSummary(context, "connected"));
        return emitter;
    }

    public List<XianyuProductEntity> listProducts(Long accountId) {
        return productRepository.findByAccountId(accountId);
    }

    public XianyuProductEntity createProduct(ProductUpsertRequest request) {
        validateProductRequest(request);
        XianyuProductEntity entity = toProductEntity(request, null);
        long id = productRepository.insert(entity);
        return productRepository.findById(id).orElse(entity);
    }

    public XianyuProductEntity updateProduct(long id, ProductUpsertRequest request) {
        validateProductRequest(request);
        XianyuProductEntity current = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("product not found: " + id));
        XianyuProductEntity entity = toProductEntity(request, current);
        entity.setId(id);
        productRepository.update(entity);
        return productRepository.findById(id).orElse(entity);
    }

    public boolean deleteProduct(long id) {
        return productRepository.deleteById(id);
    }

    public List<XianyuKeywordRuleEntity> listRules(Long accountId) {
        return keywordRuleRepository.findByAccountId(accountId);
    }

    public XianyuKeywordRuleEntity createRule(KeywordRuleUpsertRequest request) {
        validateRuleRequest(request);
        XianyuKeywordRuleEntity entity = toRuleEntity(request, null);
        long id = keywordRuleRepository.insert(entity);
        return keywordRuleRepository.findById(id).orElse(entity);
    }

    public XianyuKeywordRuleEntity updateRule(long id, KeywordRuleUpsertRequest request) {
        validateRuleRequest(request);
        XianyuKeywordRuleEntity current = keywordRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("rule not found: " + id));
        XianyuKeywordRuleEntity entity = toRuleEntity(request, current);
        entity.setId(id);
        keywordRuleRepository.update(entity);
        return keywordRuleRepository.findById(id).orElse(entity);
    }

    public boolean deleteRule(long id) {
        return keywordRuleRepository.deleteById(id);
    }

    public Map<String, Object> matchRule(RuleMatchRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            throw new IllegalArgumentException("text is required");
        }

        List<XianyuKeywordRuleEntity> rules = keywordRuleRepository.findByAccountId(request.getAccountId());
        List<Map<String, Object>> matched = new ArrayList<>();
        String text = request.getText();

        for (XianyuKeywordRuleEntity rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (isRuleMatched(rule, text)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("ruleId", rule.getId());
                item.put("ruleName", rule.getRuleName());
                item.put("keyword", rule.getKeyword());
                item.put("matchType", rule.getMatchType());
                item.put("replyText", rule.getReplyText());
                item.put("priority", rule.getPriority());
                matched.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        data.put("matched", !matched.isEmpty());
        data.put("matches", matched);
        data.put("suggestedReply", matched.isEmpty() ? null : matched.get(0).get("replyText"));
        return data;
    }

    public Map<String, Object> extractVerificationHint(String errorMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("risk", false);
        data.put("verificationUrl", null);
        if (!StringUtils.hasText(errorMessage)) {
            return data;
        }

        boolean risk = errorMessage.contains("FAIL_SYS_USER_VALIDATE")
                || errorMessage.contains("action=captcha")
                || errorMessage.contains("punish?");
        data.put("risk", risk);

        Matcher matcher = VERIFICATION_URL_PATTERN.matcher(errorMessage);
        if (matcher.find()) {
            data.put("verificationUrl", matcher.group());
        }
        return data;
    }

    public Optional<XianyuAccountEntity> recordLoginFailure(
            AccountCookieLoginRequest request,
            String errorMessage,
            Map<String, Object> verificationHint) {
        if (request == null) {
            return Optional.empty();
        }
        Map<String, String> cookies = normalizeCookies(request.getCookieHeader(), request.getCookies());
        return recordLoginFailure(request, cookies, errorMessage, verificationHint);
    }

    private Optional<XianyuAccountEntity> recordLoginFailure(
            AccountCookieLoginRequest request,
            Map<String, String> cookies,
            String errorMessage,
            Map<String, Object> verificationHint) {
        if (request == null || cookies == null || cookies.isEmpty()) {
            return Optional.empty();
        }
        XianyuAccountEntity entity = new XianyuAccountEntity();
        entity.setPlatform(SocialPlatform.XIANYU.getCode());
        entity.setAccountName(firstNonBlank(request.getAccountName(), decodeCookieValue(cookies.get("tracknick"))));
        entity.setUserId(firstNonBlank(cookies.get("unb"), cookies.get("cookie2")));
        entity.setDisplayName(firstNonBlank(decodeCookieValue(cookies.get("tracknick")), entity.getAccountName()));
        entity.setCookieHeader(buildCookieHeader(cookies));
        entity.setCookiesJson(toJson(cookies));
        entity.setSessionRawData(null);
        entity.setStatus(resolveFailureStatus(verificationHint));
        entity.setRemark(request.getRemark());
        entity.setLastError(trimToNull(errorMessage));
        entity.setLastLoginAt(Instant.now());
        return Optional.of(upsertAccount(entity));
    }

    public void openVerificationUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder builder;
            if (os.contains("mac")) {
                builder = new ProcessBuilder("open", url);
            } else if (os.contains("win")) {
                builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else {
                builder = new ProcessBuilder("xdg-open", url);
            }
            builder.start();
        } catch (Exception ignored) {
            // Opening browser is best-effort and should not break API flow.
        }
    }

    private SocialSession buildSessionFromAccount(XianyuAccountEntity account) {
        Map<String, String> cookies = extractCookiesFromAccount(account);
        if (cookies.isEmpty()) {
            throw new IllegalStateException("account cookies are empty");
        }
        SocialSession session = new SocialSession(SocialPlatform.XIANYU, account.getUserId(),
                "xianyu-session-" + firstNonBlank(account.getUserId(), String.valueOf(account.getId())));
        session.setRawData(StringUtils.hasText(account.getSessionRawData())
                ? account.getSessionRawData()
                : buildSimpleSessionRawData(cookies, account.getUserId()));
        return session;
    }

    private Map<String, Object> messageToEvent(long accountId, XianyuMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message");
        payload.put("accountId", accountId);
        if (message != null) {
            payload.put("chatId", message.getChatId());
            payload.put("sessionId", message.getSessionId());
            payload.put("senderUserId", message.getSenderUserId());
            payload.put("senderNick", message.getSenderNick());
            payload.put("receiverUserId", message.getReceiverUserId());
            payload.put("itemId", message.getItemId());
            payload.put("content", message.getContent());
            payload.put("outgoing", message.isOutgoing());
            payload.put("rawData", message.getRawData());
            payload.put("timestamp", message.getTimestamp() == null ? Instant.now().toString() : message.getTimestamp().toString());
        } else {
            payload.put("timestamp", Instant.now().toString());
        }
        return payload;
    }

    private Map<String, Object> conversationHintToEvent(long accountId, String chatId, String userId, Map<String, Object> rawMessage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "conversation_hint");
        event.put("accountId", accountId);
        event.put("chatId", chatId);
        event.put("sessionId", chatId);
        event.put("senderUserId", userId);
        event.put("receiverUserId", null);
        event.put("content", "检测到会话状态变更，可点击回填并回复");
        event.put("outgoing", false);
        event.put("rawData", rawMessage);
        event.put("timestamp", Instant.now().toString());
        return event;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> conversationHintsFromSystemMessage(long accountId, Map<String, Object> message) {
        if (message == null) {
            return Collections.emptyList();
        }
        Object conversations = message.get("1");
        if (!(conversations instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> hints = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String chatId = normalizeGoofishId(asString(map.get("1")));
            String userId = normalizeGoofishId(asString(map.get("4")));
            if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userId)) {
                continue;
            }
            hints.add(conversationHintToEvent(accountId, chatId, userId, message));
        }
        return hints;
    }

    private String normalizeGoofishId(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        int at = text.indexOf('@');
        return at >= 0 ? text.substring(0, at) : text;
    }

    private void fetchConversationMessagesAsync(
            ChatTakeoverContext context,
            XianyuAccountEntity account,
            XianyuProvider provider,
            String chatId) {
        if (context == null || account == null || provider == null || !StringUtils.hasText(chatId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                SocialSession session = buildSessionFromAccount(account);
                session.setRawData(withSessionId(session.getRawData(), chatId));
                Object timeline = provider.getTimeline(session, 10);
                appendTimelineMessages(context, timeline, account.getUserId());
            } catch (Exception e) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("type", "sync_error");
                event.put("accountId", context.accountId);
                event.put("chatId", chatId);
                event.put("message", e.getMessage());
                event.put("timestamp", Instant.now().toString());
                appendChatEvent(context, event);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void appendTimelineMessages(ChatTakeoverContext context, Object timeline, String myUserId) {
        if (!(timeline instanceof Map<?, ?> root)) {
            return;
        }
        Object itemsObj = root.get("items");
        if (!(itemsObj instanceof List<?> items)) {
            return;
        }
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            String messageUuid = asString(item.get("messageUuid"));
            String chatId = asString(item.get("chatId"));
            String dedupeKey = firstNonBlank(messageUuid, chatId + ":" + asString(item.get("timeStamp")) + ":" + asString(item.get("senderUserId")));
            if (!StringUtils.hasText(dedupeKey) || !context.seenMessageKeys.add(dedupeKey)) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "message");
            event.put("accountId", context.accountId);
            event.put("chatId", chatId);
            event.put("sessionId", chatId);
            event.put("senderUserId", asString(item.get("senderUserId")));
            event.put("senderNick", asString(item.get("senderNick")));
            event.put("receiverUserId", null);
            event.put("itemId", null);
            event.put("content", asString(item.get("contentText")));
            event.put("outgoing", StringUtils.hasText(asString(item.get("senderUserId")))
                    && asString(item.get("senderUserId")).equals(myUserId));
            event.put("rawData", item);
            event.put("timestamp", timelineTimestampToInstant(item.get("timeStamp")));
            appendChatEvent(context, event);
        }
    }

    private String timelineTimestampToInstant(Object value) {
        try {
            long timestamp = Long.parseLong(asString(value));
            if (timestamp > 0) {
                return Instant.ofEpochMilli(timestamp).toString();
            }
        } catch (Exception ignored) {
        }
        return Instant.now().toString();
    }

    private String withSessionId(String rawData, String sessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (StringUtils.hasText(rawData)) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(rawData, new TypeReference<Map<String, Object>>() {
                });
                if (parsed != null) {
                    data.putAll(parsed);
                }
            } catch (Exception ignored) {
            }
        }
        data.put("sessionId", sessionId);
        return toJson(data);
    }

    private void appendManualSendEventIfTakeoverRunning(long accountId, MessageSendRequest request, PostResult result) {
        ChatTakeoverContext context = chatTakeovers.get(accountId);
        if (context == null || context.client == null || !context.client.isRunning()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "message");
        event.put("accountId", accountId);
        event.put("chatId", trimToNull(request.getCid()));
        event.put("senderUserId", null);
        event.put("senderNick", "我方");
        event.put("receiverUserId", trimToNull(request.getToUserId()));
        event.put("itemId", trimToNull(request.getItemId()));
        event.put("content", trimToNull(request.getText()));
        event.put("imageUrl", trimToNull(request.getImageUrl()));
        event.put("imagePath", trimToNull(request.getImagePath()));
        event.put("outgoing", true);
        event.put("sendResult", result);
        event.put("timestamp", Instant.now().toString());
        appendChatEvent(context, event);
    }

    private void handleAutoReply(ChatTakeoverContext context, XianyuMessage message) {
        try {
            RuleMatchRequest matchRequest = new RuleMatchRequest();
            matchRequest.setAccountId(context.accountId);
            matchRequest.setText(message.getContent());
            Map<String, Object> matched = matchRule(matchRequest);
            String replyText = asString(matched.get("suggestedReply"));
            if (!StringUtils.hasText(replyText) || !StringUtils.hasText(message.getSenderUserId())) {
                return;
            }
            MessageSendRequest sendRequest = new MessageSendRequest();
            sendRequest.setAccountId(context.accountId);
            sendRequest.setToUserId(message.getSenderUserId());
            sendRequest.setCid(message.getChatId());
            sendRequest.setItemId(message.getItemId());
            sendRequest.setText(replyText);
            sendRequest.setUseRealtime(true);
            Map<String, Object> sendResult = sendMessage(sendRequest);

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "auto_reply");
            event.put("accountId", context.accountId);
            event.put("chatId", message.getChatId());
            event.put("toUserId", message.getSenderUserId());
            event.put("matched", matched);
            event.put("sendResult", sendResult);
            event.put("timestamp", Instant.now().toString());
            appendChatEvent(context, event);
        } catch (Exception e) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "auto_reply_error");
            event.put("accountId", context.accountId);
            event.put("chatId", message == null ? null : message.getChatId());
            event.put("message", e.getMessage());
            event.put("timestamp", Instant.now().toString());
            appendChatEvent(context, event);
        }
    }

    private void appendChatEvent(ChatTakeoverContext context, Map<String, Object> event) {
        synchronized (context.events) {
            context.events.addLast(event);
            while (context.events.size() > MAX_CHAT_EVENTS) {
                context.events.removeFirst();
            }
        }
        String eventName = asString(event.get("type"));
        if (!StringUtils.hasText(eventName)) {
            eventName = "message";
        }
        for (SseEmitter emitter : new ArrayList<>(context.emitters)) {
            if (!sendChatSse(emitter, eventName, event)) {
                context.emitters.remove(emitter);
            }
        }
    }

    private boolean sendChatSse(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private void closeChatEmitters(ChatTakeoverContext context) {
        for (SseEmitter emitter : new ArrayList<>(context.emitters)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        context.emitters.clear();
    }

    private Map<String, Object> chatTakeoverNotRunning(long accountId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountId", accountId);
        data.put("running", false);
        data.put("autoReply", false);
        data.put("message", "not running");
        return data;
    }

    private Map<String, Object> chatTakeoverSummary(ChatTakeoverContext context, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountId", context.accountId);
        data.put("running", context.client != null && context.client.isRunning());
        data.put("autoReply", context.autoReply);
        data.put("startedAt", context.startedAt == null ? null : context.startedAt.toString());
        data.put("eventCount", context.events.size());
        data.put("streamClients", context.emitters.size());
        data.put("message", message);
        return data;
    }

    private Map<String, Integer> summarizeAccountStatus(List<XianyuAccountEntity> accounts) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put(ACCOUNT_STATUS_ACTIVE, 0);
        summary.put(ACCOUNT_STATUS_PENDING_VERIFY, 0);
        summary.put(ACCOUNT_STATUS_FAILED, 0);
        summary.put("UNKNOWN", 0);
        if (accounts == null || accounts.isEmpty()) {
            return summary;
        }
        for (XianyuAccountEntity account : accounts) {
            String status = account == null ? null : normalizeAccountStatus(account.getStatus());
            if (!isSupportedAccountStatus(status)) {
                status = "UNKNOWN";
            }
            summary.put(status, summary.getOrDefault(status, 0) + 1);
        }
        return summary;
    }

    private XianyuAccountEntity upsertAccount(XianyuAccountEntity candidate) {
        Optional<XianyuAccountEntity> existing = Optional.empty();
        if (StringUtils.hasText(candidate.getUserId())) {
            existing = accountRepository.findFirstByUserId(candidate.getUserId());
        }
        if (existing.isEmpty() && StringUtils.hasText(candidate.getAccountName())) {
            existing = accountRepository.findFirstByAccountName(candidate.getAccountName());
        }
        if (existing.isPresent()) {
            XianyuAccountEntity current = existing.get();
            candidate.setId(current.getId());
            if (candidate.getRemark() == null) {
                candidate.setRemark(current.getRemark());
            }
            accountRepository.update(candidate);
            return accountRepository.findById(current.getId()).orElse(candidate);
        }
        long id = accountRepository.insert(candidate);
        return accountRepository.findById(id).orElse(candidate);
    }

    private void persistHeadlessQrSuccess(
            String sessionId,
            XianyuHeadlessQrLoginSession session,
            HeadlessQrSessionContext context) {
        Map<String, String> cookies = session.getCookies() == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(session.getCookies());
        if (cookies.isEmpty()) {
            throw new IllegalStateException("headless qr login success but cookies are empty");
        }

        XianyuAccountEntity account = new XianyuAccountEntity();
        account.setPlatform(SocialPlatform.XIANYU.getCode());
        account.setAccountName(firstNonBlank(
                context.accountName,
                decodeCookieValue(firstNonBlank(session.getTracknick(), cookies.get("tracknick"))),
                firstNonBlank(session.getUserId(), cookies.get("unb"), cookies.get("cookie2"))));
        account.setUserId(firstNonBlank(session.getUserId(), cookies.get("unb"), cookies.get("cookie2")));
        account.setDisplayName(firstNonBlank(
                decodeCookieValue(firstNonBlank(session.getTracknick(), cookies.get("tracknick"))),
                account.getAccountName()));
        account.setCookieHeader(firstNonBlank(session.getCookieHeader(), buildCookieHeader(cookies)));
        account.setCookiesJson(toJson(cookies));
        account.setSessionRawData(buildHeadlessSessionRawData(session));
        account.setStatus(ACCOUNT_STATUS_ACTIVE);
        account.setRemark(context.remark);
        account.setLastError(null);
        account.setLastLoginAt(Instant.now());

        XianyuAccountEntity persistedAccount = upsertAccount(account);

        XianyuLoginSnapshotEntity snapshot = new XianyuLoginSnapshotEntity();
        snapshot.setAccountId(persistedAccount.getId());
        snapshot.setSessionId(sessionId);
        snapshot.setLoginMode("headless_qr");
        snapshot.setUserId(account.getUserId());
        snapshot.setCookieHeader(account.getCookieHeader());
        snapshot.setCookiesJson(account.getCookiesJson());
        snapshot.setLocalStorageJson(toJson(firstNonNull(session.getLocalStorageByOrigin(), Collections.emptyMap())));
        snapshot.setSessionStorageJson(toJson(firstNonNull(session.getSessionStorageByOrigin(), Collections.emptyMap())));
        snapshot.setIndexedDbJson(toJson(firstNonNull(session.getIndexedDbByOrigin(), Collections.emptyMap())));
        snapshot.setCacheStorageJson(toJson(firstNonNull(session.getCacheStorageByOrigin(), Collections.emptyMap())));
        snapshot.setCurrentUrl(session.getCurrentUrl());
        snapshot.setUserAgent(session.getUserAgent());
        snapshot.setCapturedAt(firstNonNull(session.getCompletedAt(), Instant.now()));
        long snapshotId = loginSnapshotRepository.insert(snapshot);

        context.accountId = persistedAccount.getId();
        context.snapshotId = snapshotId;
        context.persisted = true;
    }

    private String buildHeadlessSessionRawData(XianyuHeadlessQrLoginSession session) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", "xianyu");
        raw.put("loginMode", "headless_qr");
        raw.put("capturedAt", Instant.now().toString());
        raw.put("currentUrl", session.getCurrentUrl());
        raw.put("startUrl", session.getLoginUrl());
        raw.put("cookieHeader", session.getCookieHeader());
        raw.put("cookies", firstNonNull(session.getCookies(), Collections.emptyMap()));
        raw.put("localStorageByOrigin", firstNonNull(session.getLocalStorageByOrigin(), Collections.emptyMap()));
        raw.put("sessionStorageByOrigin", firstNonNull(session.getSessionStorageByOrigin(), Collections.emptyMap()));
        raw.put("indexedDbByOrigin", firstNonNull(session.getIndexedDbByOrigin(), Collections.emptyMap()));
        raw.put("cacheStorageByOrigin", firstNonNull(session.getCacheStorageByOrigin(), Collections.emptyMap()));
        return toJson(raw);
    }

    private void updateFailureStatus(XianyuAccountEntity account, String errorMessage, Map<String, Object> verificationHint) {
        if (account == null) {
            return;
        }
        account.setStatus(resolveFailureStatus(verificationHint));
        account.setLastError(trimToNull(errorMessage));
        account.setLastLoginAt(Instant.now());
        accountRepository.update(account);
    }

    private String resolveFailureStatus(Map<String, Object> verificationHint) {
        if (verificationHint != null && Boolean.TRUE.equals(verificationHint.get("risk"))) {
            return ACCOUNT_STATUS_PENDING_VERIFY;
        }
        return ACCOUNT_STATUS_FAILED;
    }

    private String normalizeAccountStatus(String status) {
        return firstNonBlank(status, "").toUpperCase();
    }

    private boolean isSupportedAccountStatus(String status) {
        return ACCOUNT_STATUS_ACTIVE.equals(status)
                || ACCOUNT_STATUS_PENDING_VERIFY.equals(status)
                || ACCOUNT_STATUS_FAILED.equals(status);
    }

    private boolean isRuleMatched(XianyuKeywordRuleEntity rule, String text) {
        String keyword = firstNonBlank(rule.getKeyword(), "").trim();
        if (keyword.isEmpty()) {
            return false;
        }
        String matchType = firstNonBlank(rule.getMatchType(), "CONTAINS").trim().toUpperCase();
        switch (matchType) {
            case "EXACT":
                return text.trim().equalsIgnoreCase(keyword);
            case "REGEX":
                try {
                    return Pattern.compile(keyword, Pattern.CASE_INSENSITIVE).matcher(text).find();
                } catch (Exception e) {
                    return false;
                }
            case "CONTAINS":
            default:
                return text.toLowerCase().contains(keyword.toLowerCase());
        }
    }

    private XianyuProductEntity toProductEntity(ProductUpsertRequest request, XianyuProductEntity base) {
        XianyuProductEntity entity = base == null ? new XianyuProductEntity() : base;
        entity.setAccountId(request.getAccountId());
        entity.setItemId(trimToNull(request.getItemId()));
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setPrice(request.getPrice());
        entity.setStock(request.getStock());
        entity.setStatus(firstNonBlank(trimToNull(request.getStatus()), "ON_SHELF"));
        entity.setDetailUrl(trimToNull(request.getDetailUrl()));
        entity.setDescription(trimToNull(request.getDescription()));
        return entity;
    }

    private XianyuKeywordRuleEntity toRuleEntity(KeywordRuleUpsertRequest request, XianyuKeywordRuleEntity base) {
        XianyuKeywordRuleEntity entity = base == null ? new XianyuKeywordRuleEntity() : base;
        entity.setAccountId(request.getAccountId());
        entity.setRuleName(trimToNull(request.getRuleName()));
        entity.setKeyword(trimToNull(request.getKeyword()));
        entity.setMatchType(firstNonBlank(trimToNull(request.getMatchType()), "CONTAINS").toUpperCase());
        entity.setReplyText(trimToNull(request.getReplyText()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        return entity;
    }

    private void validateProductRequest(ProductUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.getAccountId() == null || request.getAccountId() <= 0) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("title is required");
        }
    }

    private void validateRuleRequest(KeywordRuleUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(request.getKeyword())) {
            throw new IllegalArgumentException("keyword is required");
        }
        if (!StringUtils.hasText(request.getReplyText())) {
            throw new IllegalArgumentException("replyText is required");
        }
    }

    private XianyuProvider requireXianyuProvider() {
        if (providerManager == null) {
            throw new IllegalStateException("SocialProviderManager is not available");
        }
        SocialProvider provider = providerManager.getProvider(SocialPlatform.XIANYU.getCode());
        if (!(provider instanceof XianyuProvider xianyuProvider)) {
            throw new IllegalStateException("XianyuProvider is not enabled. Set social-sdk.xianyu.enabled=true");
        }
        return xianyuProvider;
    }

    private String buildSimpleSessionRawData(Map<String, String> cookies, String userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "xianyu");
        payload.put("capturedAt", Instant.now().toString());
        payload.put("userId", userId);
        payload.put("cookieHeader", buildCookieHeader(cookies));
        payload.put("cookies", cookies);
        return toJson(payload);
    }

    private Map<String, String> extractCookiesFromAccount(XianyuAccountEntity account) {
        if (account == null) {
            return Collections.emptyMap();
        }
        Map<String, String> fromRawData = extractCookiesFromSessionRawData(account.getSessionRawData());
        if (!fromRawData.isEmpty()) {
            return fromRawData;
        }
        Map<String, String> fromJson = parseCookiesJson(account.getCookiesJson());
        if (!fromJson.isEmpty()) {
            return fromJson;
        }
        return parseCookieHeader(account.getCookieHeader());
    }

    private Map<String, String> extractCookiesFromSessionRawData(String rawData) {
        if (!StringUtils.hasText(rawData)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> data = objectMapper.readValue(rawData, new TypeReference<Map<String, Object>>() {
            });
            Object cookiesObj = data.get("cookies");
            if (cookiesObj instanceof Map<?, ?> rawMap) {
                Map<String, String> cookies = new LinkedHashMap<>();
                rawMap.forEach((k, v) -> {
                    if (k != null && v != null) {
                        cookies.put(k.toString(), v.toString());
                    }
                });
                return cookies;
            }
            Object cookieHeader = data.get("cookieHeader");
            if (cookieHeader instanceof String header) {
                return parseCookieHeader(header);
            }
        } catch (Exception ignored) {
            // ignore malformed payload
        }
        return Collections.emptyMap();
    }

    private Map<String, String> parseCookiesJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, String> normalizeCookies(String cookieHeader, Map<String, String> cookiesMap) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (cookiesMap != null) {
            cookiesMap.forEach((k, v) -> {
                if (StringUtils.hasText(k) && v != null) {
                    cookies.put(k.trim(), v);
                }
            });
        }
        if (cookies.isEmpty() && StringUtils.hasText(cookieHeader)) {
            cookies.putAll(parseCookieHeader(cookieHeader));
        }
        return cookies;
    }

    private Map<String, String> parseCookieHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return Collections.emptyMap();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        String[] parts = header.split(";");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && StringUtils.hasText(kv[0])) {
                cookies.put(kv[0].trim(), kv[1].trim());
            }
        }
        return cookies;
    }

    private String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        cookies.forEach((k, v) -> {
            if (!StringUtils.hasText(k) || v == null) {
                return;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(k).append("=").append(v);
        });
        return builder.toString();
    }

    private String decodeCookieValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize JSON failed: " + e.getMessage(), e);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static final class HeadlessQrSessionContext {
        private final Object lock = new Object();
        private String accountName;
        private String remark;
        private boolean persisted;
        private Long accountId;
        private Long snapshotId;
    }

    private static final class ChatTakeoverContext {
        private final Object lock = new Object();
        private final long accountId;
        private final Set<String> seenMessageKeys = ConcurrentHashMap.newKeySet();
        private final ConcurrentLinkedDeque<Map<String, Object>> events = new ConcurrentLinkedDeque<>();
        private final List<SseEmitter> emitters = Collections.synchronizedList(new ArrayList<>());
        private volatile XianyuRealtimeClient client;
        private volatile boolean autoReply;
        private volatile Instant startedAt;

        private ChatTakeoverContext(long accountId) {
            this.accountId = accountId;
        }
    }
}
