package cn.net.rjnetwork.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.net.rjnetwork.core.exception.SocialAuthenticationException;
import cn.net.rjnetwork.core.provider.SocialProvider;
import cn.net.rjnetwork.core.model.PostResult;
import cn.net.rjnetwork.core.model.SocialContent;
import cn.net.rjnetwork.core.model.SocialSession;
import cn.net.rjnetwork.starter.config.SocialSdkAutoConfiguration;
import cn.net.rjnetwork.xianyu.model.XianyuCredentials;
import cn.net.rjnetwork.xianyu.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.model.XianyuQrLoginSession;
import cn.net.rjnetwork.xianyu.service.XianyuRealtimeClient;
import cn.net.rjnetwork.xianyu.service.XianyuProvider;
import cn.net.rjnetwork.demo.model.CookieLoginRequest;
import cn.net.rjnetwork.demo.model.QrAuthenticateRequest;
import cn.net.rjnetwork.demo.model.SendMessageRequest;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class XianyuDemoService {

    private static final int MAX_RECENT_MESSAGES = 200;

    private final SocialSdkAutoConfiguration.SocialProviderManager providerManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final Object providerLock = new Object();

    public XianyuDemoService(SocialSdkAutoConfiguration.SocialProviderManager providerManager) {
        this.providerManager = providerManager;
    }

    public Map<String, Object> loginWithCookies(CookieLoginRequest request) throws Exception {
        XianyuCredentials credentials = new XianyuCredentials();
        if (request != null) {
            credentials.setCookieHeader(trimToNull(request.getCookieHeader()));
            if (request.getCookies() != null && !request.getCookies().isEmpty()) {
                credentials.setCookies(new LinkedHashMap<>(request.getCookies()));
            }
            if (request.getAllowManualLogin() != null) {
                credentials.setAllowManualLogin(request.getAllowManualLogin());
            }
            if (request.getLoginTimeoutSeconds() != null && request.getLoginTimeoutSeconds() > 0) {
                credentials.setLoginTimeoutSeconds(request.getLoginTimeoutSeconds());
            }
            credentials.setStartUrl(trimToNull(request.getStartUrl()));
        }

        SocialSession socialSession;
        synchronized (providerLock) {
            socialSession = requireXianyuProvider().authenticate(credentials);
        }
        return saveSession(socialSession, "cookies");
    }

    public XianyuQrLoginSession createQrLoginSession() throws SocialAuthenticationException {
        synchronized (providerLock) {
            return requireXianyuProvider().createQrLoginSession();
        }
    }

    public XianyuQrLoginSession getQrLoginSessionStatus(String qrSessionId) {
        synchronized (providerLock) {
            return requireXianyuProvider().getQrLoginSessionStatus(qrSessionId);
        }
    }

    public boolean invalidateQrLoginSession(String qrSessionId) {
        synchronized (providerLock) {
            return requireXianyuProvider().invalidateQrLoginSession(qrSessionId);
        }
    }

    public Map<String, Object> authenticateByQr(QrAuthenticateRequest request) throws Exception {
        if (request == null || isBlank(request.getQrSessionId())) {
            throw new IllegalArgumentException("qrSessionId is required");
        }

        XianyuCredentials credentials = new XianyuCredentials();
        credentials.setQrLoginSessionId(request.getQrSessionId().trim());
        if (request.getAllowManualLogin() != null) {
            credentials.setAllowManualLogin(request.getAllowManualLogin());
        } else {
            credentials.setAllowManualLogin(false);
        }

        SocialSession socialSession;
        synchronized (providerLock) {
            socialSession = requireXianyuProvider().authenticate(credentials);
        }
        return saveSession(socialSession, "qr");
    }

    public Map<String, Object> sendMessage(SendMessageRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        SessionContext context = requireSession(request.getDemoSessionId());

        boolean hasText = !isBlank(request.getText());
        boolean hasImage = !isBlank(request.getImageUrl()) || !isBlank(request.getImagePath());
        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("text or imageUrl/imagePath is required");
        }
        if (isBlank(request.getToUserId())) {
            throw new IllegalArgumentException("toUserId is required");
        }

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
        if (!isBlank(request.getImageUrl())) {
            content.setImageUrls(List.of(request.getImageUrl().trim()));
        }
        content.setRawData(objectMapper.writeValueAsString(rawData));

        PostResult result;
        synchronized (providerLock) {
            result = requireXianyuProvider().postContent(context.socialSession, content);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("demoSessionId", context.demoSessionId);
        data.put("result", result);
        return data;
    }

    public Object getTimeline(String demoSessionId, int limit) throws Exception {
        SessionContext context = requireSession(demoSessionId);
        synchronized (providerLock) {
            return requireXianyuProvider().getTimeline(context.socialSession, limit);
        }
    }

    public Map<String, Object> startRealtime(String demoSessionId) throws Exception {
        SessionContext context = requireSession(demoSessionId);
        synchronized (context.lock) {
            if (context.realtimeClient != null && context.realtimeClient.isRunning()) {
                return sessionSummary(context, "realtime already running");
            }

            XianyuRealtimeClient client;
            synchronized (providerLock) {
                client = requireXianyuProvider().subscribeMessages(context.socialSession, new XianyuRealtimeClient.MessageListener() {
                    @Override
                    public void onMessage(XianyuMessage message) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("type", "message");
                        payload.put("chatId", message.getChatId());
                        payload.put("senderUserId", message.getSenderUserId());
                        payload.put("senderNick", message.getSenderNick());
                        payload.put("itemId", message.getItemId());
                        payload.put("content", message.getContent());
                        payload.put("timestamp", message.getTimestamp() != null
                                ? message.getTimestamp().toString() : Instant.now().toString());
                        appendRealtimeEvent(context, payload);
                    }

                    @Override
                    public void onSystemMessage(Map<String, Object> message) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("type", "system");
                        payload.put("timestamp", Instant.now().toString());
                        payload.put("payload", message);
                        appendRealtimeEvent(context, payload);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("type", "error");
                        payload.put("timestamp", Instant.now().toString());
                        payload.put("message", throwable != null ? throwable.getMessage() : "unknown error");
                        appendRealtimeEvent(context, payload);
                    }
                });
            }
            context.realtimeClient = client;
            appendRealtimeEvent(context, mapOf(
                    "type", "lifecycle",
                    "timestamp", Instant.now().toString(),
                    "message", "realtime started"));
            return sessionSummary(context, "realtime started");
        }
    }

    public Map<String, Object> stopRealtime(String demoSessionId) {
        SessionContext context = requireSession(demoSessionId);
        synchronized (context.lock) {
            if (context.realtimeClient != null) {
                context.realtimeClient.close();
                context.realtimeClient = null;
                appendRealtimeEvent(context, mapOf(
                        "type", "lifecycle",
                        "timestamp", Instant.now().toString(),
                        "message", "realtime stopped"));
            }
            return sessionSummary(context, "realtime stopped");
        }
    }

    public List<Map<String, Object>> listRealtimeMessages(String demoSessionId) {
        SessionContext context = requireSession(demoSessionId);
        synchronized (context.lock) {
            return new ArrayList<>(context.recentMessages);
        }
    }

    public SseEmitter openRealtimeStream(String demoSessionId) {
        SessionContext context = requireSession(demoSessionId);
        SseEmitter emitter = new SseEmitter(0L);

        synchronized (context.lock) {
            context.emitters.add(emitter);
        }
        emitter.onCompletion(() -> removeEmitter(context, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(context, emitter);
        });
        emitter.onError(error -> removeEmitter(context, emitter));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", "snapshot");
        snapshot.put("timestamp", Instant.now().toString());
        snapshot.put("items", listRealtimeMessages(demoSessionId));
        try {
            sendSseEvent(emitter, "snapshot", snapshot);
        } catch (IOException e) {
            removeEmitter(context, emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public Map<String, Object> getSession(String demoSessionId) {
        SessionContext context = requireSession(demoSessionId);
        synchronized (context.lock) {
            return sessionSummary(context, "session found");
        }
    }

    public Map<String, Object> logout(String demoSessionId) {
        SessionContext removed = sessions.remove(demoSessionId);
        if (removed == null) {
            throw new IllegalArgumentException("demoSessionId not found: " + demoSessionId);
        }

        synchronized (removed.lock) {
            if (removed.realtimeClient != null) {
                removed.realtimeClient.close();
                removed.realtimeClient = null;
            }
        }
        closeEmitters(removed);
        synchronized (providerLock) {
            requireXianyuProvider().logout(removed.socialSession);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("demoSessionId", removed.demoSessionId);
        result.put("loggedOut", true);
        return result;
    }

    @PreDestroy
    public void shutdown() {
        for (SessionContext context : sessions.values()) {
            synchronized (context.lock) {
                if (context.realtimeClient != null) {
                    context.realtimeClient.close();
                    context.realtimeClient = null;
                }
            }
            closeEmitters(context);
        }
        sessions.clear();
        requireXianyuProvider().closeDriver();
    }

    private XianyuProvider requireXianyuProvider() {
        if (providerManager == null) {
            throw new IllegalStateException("SocialProviderManager is not available");
        }
        SocialProvider provider = providerManager.getProvider("xianyu");
        if (provider instanceof XianyuProvider xianyuProvider) {
            return xianyuProvider;
        }
        throw new IllegalStateException("XianyuProvider is not configured. Please set social-sdk.xianyu.enabled=true");
    }

    private SessionContext requireSession(String demoSessionId) {
        if (isBlank(demoSessionId)) {
            throw new IllegalArgumentException("demoSessionId is required");
        }
        SessionContext context = sessions.get(demoSessionId.trim());
        if (context == null) {
            throw new IllegalArgumentException("demoSessionId not found: " + demoSessionId);
        }
        return context;
    }

    private Map<String, Object> saveSession(SocialSession socialSession, String loginType) {
        String demoSessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(demoSessionId, socialSession);
        sessions.put(demoSessionId, context);

        Map<String, Object> data = sessionSummary(context, "login success");
        data.put("loginType", loginType);
        return data;
    }

    private Map<String, Object> sessionSummary(SessionContext context, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message);
        data.put("demoSessionId", context.demoSessionId);
        data.put("createdAt", context.createdAt.toString());
        data.put("platform", context.socialSession.getPlatform() != null
                ? context.socialSession.getPlatform().getCode() : null);
        data.put("userId", context.socialSession.getUserId());
        data.put("expiresAt", context.socialSession.getExpiresAt() != null
                ? context.socialSession.getExpiresAt().toString() : null);
        data.put("sessionValid", !context.socialSession.isExpired() && !isBlank(context.socialSession.getAccessToken()));
        data.put("realtimeRunning", context.realtimeClient != null && context.realtimeClient.isRunning());
        data.put("recentMessageCount", context.recentMessages.size());
        data.put("rawData", context.socialSession.getRawData());
        return data;
    }

    private void appendRealtimeEvent(SessionContext context, Map<String, Object> payload) {
        List<SseEmitter> emitterSnapshot;
        synchronized (context.lock) {
            context.recentMessages.addFirst(payload);
            while (context.recentMessages.size() > MAX_RECENT_MESSAGES) {
                context.recentMessages.removeLast();
            }
            emitterSnapshot = new ArrayList<>(context.emitters);
        }

        if (emitterSnapshot.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitterSnapshot) {
            try {
                sendSseEvent(emitter, "message", payload);
            } catch (IOException e) {
                removeEmitter(context, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload));
    }

    private void removeEmitter(SessionContext context, SseEmitter emitter) {
        synchronized (context.lock) {
            context.emitters.remove(emitter);
        }
    }

    private void closeEmitters(SessionContext context) {
        List<SseEmitter> emitters;
        synchronized (context.lock) {
            if (context.emitters.isEmpty()) {
                return;
            }
            emitters = new ArrayList<>(context.emitters);
            context.emitters.clear();
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // Ignore emitter close errors during cleanup.
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvs.length; i += 2) {
            if (kvs[i] != null) {
                map.put(kvs[i].toString(), kvs[i + 1]);
            }
        }
        return map;
    }

    private static final class SessionContext {
        private final String demoSessionId;
        private final SocialSession socialSession;
        private final Instant createdAt = Instant.now();
        private final Deque<Map<String, Object>> recentMessages = new ArrayDeque<>();
        private final List<SseEmitter> emitters = new ArrayList<>();
        private final Object lock = new Object();
        private volatile XianyuRealtimeClient realtimeClient;

        private SessionContext(String demoSessionId, SocialSession socialSession) {
            this.demoSessionId = Objects.requireNonNull(demoSessionId, "demoSessionId");
            this.socialSession = Objects.requireNonNull(socialSession, "socialSession");
        }
    }
}
