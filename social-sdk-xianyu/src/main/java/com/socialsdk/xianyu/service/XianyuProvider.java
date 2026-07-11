package com.socialsdk.xianyu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsdk.chrome.config.ChromeConfig;
import com.socialsdk.xianyu.config.XianyuConfig;
import com.socialsdk.xianyu.model.XianyuCredentials;
import com.socialsdk.xianyu.model.XianyuHeadlessQrLoginSession;
import com.socialsdk.xianyu.model.XianyuQrLoginSession;
import com.socialsdk.core.constant.SocialPlatform;
import com.socialsdk.core.exception.SocialAuthenticationException;
import com.socialsdk.core.exception.SocialNetworkException;
import com.socialsdk.core.model.PostResult;
import com.socialsdk.core.model.SocialContent;
import com.socialsdk.core.model.SocialSession;
import com.socialsdk.core.model.SocialUserProfile;
import com.socialsdk.core.provider.AbstractSocialProvider;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * 闲鱼平台提供者（基于 Chrome Web 自动化）
 */
public class XianyuProvider extends AbstractSocialProvider {

    private static final Logger logger = LoggerFactory.getLogger(XianyuProvider.class);
    private static final String DEFAULT_QR_LOGIN_URL =
            "https://passport.goofish.com/mini_login.htm?lang=zh_cn&appName=xianyu&appEntrance=web&styleType=vertical&stie=77";

    private final ChromeConfig chromeConfig;
    private final XianyuConfig xianyuConfig;
    private final XianyuQrLoginManager qrLoginManager;
    private final XianyuHeadlessQrLoginManager headlessQrLoginManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile WebDriver driver;

    public XianyuProvider() {
        this(new ChromeConfig(), new XianyuConfig());
    }

    public XianyuProvider(ChromeConfig chromeConfig, XianyuConfig xianyuConfig) {
        this.chromeConfig = chromeConfig != null ? chromeConfig : new ChromeConfig();
        this.xianyuConfig = xianyuConfig != null ? xianyuConfig : new XianyuConfig();
        this.qrLoginManager = new XianyuQrLoginManager();
        this.headlessQrLoginManager = new XianyuHeadlessQrLoginManager(this.chromeConfig, this.xianyuConfig);
    }

    @Override
    public SocialPlatform getPlatform() {
        return SocialPlatform.XIANYU;
    }

    @Override
    public SocialSession authenticate(Object credentials) throws SocialAuthenticationException {
        XianyuCredentials auth = normalizeCredentials(credentials);
        String startUrl = resolveAuthStartUrl(auth);

        try {
            Map<String, String> incomingCookies = resolveIncomingCookies(auth);
            if (incomingCookies.isEmpty() && !isBlank(auth.getQrLoginSessionId())) {
                incomingCookies = resolveQrSessionCookies(auth.getQrLoginSessionId());
            }

            if (!incomingCookies.isEmpty() && hasRequiredAuthCookies(incomingCookies)) {
                return buildAuthenticatedSession(incomingCookies, startUrl);
            }

            ensureDriver();
            driver.get(startUrl);

            if (!incomingCookies.isEmpty()) {
                injectCookies(startUrl, incomingCookies);
                Map<String, String> injectedCookies = exportCookies();
                if (hasRequiredAuthCookies(injectedCookies)) {
                    return buildAuthenticatedSession(injectedCookies, startUrl);
                }
            }

            long timeoutSeconds = auth.getLoginTimeoutSeconds() > 0
                    ? auth.getLoginTimeoutSeconds()
                    : xianyuConfig.getLoginTimeoutSeconds();
            waitUntilLoggedIn(timeoutSeconds, auth.isAllowManualLogin());

            Map<String, String> sessionCookies = exportCookies();
            if (!hasRequiredAuthCookies(sessionCookies)) {
                throw new SocialAuthenticationException("Xianyu login failed: required auth cookies missing");
            }
            return buildAuthenticatedSession(sessionCookies, startUrl);
        } catch (SocialAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new SocialAuthenticationException("Xianyu authentication failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SocialUserProfile getUserProfile(SocialSession session) throws SocialAuthenticationException {
        validateSessionOrThrow(session);
        Map<String, String> sessionCookies = extractSessionCookies(session);

        SocialUserProfile profile = new SocialUserProfile();
        profile.setPlatform(SocialPlatform.XIANYU);
        profile.setUserId(firstNonBlank(session.getUserId(), sessionCookies.get("unb")));

        String nick = decodeCookieValue(sessionCookies.get("tracknick"));
        profile.setUsername(firstNonBlank(nick, sessionCookies.get("unick"), session.getUserId()));
        profile.setDisplayName(firstNonBlank(nick, profile.getUsername(), "xianyu-user"));
        profile.setVerified(false);

        if (profile.getUserId() != null) {
            profile.setProfileUrl("https://www.goofish.com/personal?userId=" + profile.getUserId());
        } else {
            profile.setProfileUrl("https://www.goofish.com/");
        }
        profile.setRawData(session.getRawData());
        return profile;
    }

    @Override
    public PostResult postContent(SocialSession session, SocialContent content)
            throws SocialNetworkException, SocialAuthenticationException {
        validateSessionOrThrow(session);

        Map<String, Object> raw = parseRawDataMap(content != null ? content.getRawData() : null);
        boolean hasText = content != null && !isBlank(content.getText());
        String imageUrlOrPath = resolveFirstImageUrl(content, raw);
        boolean hasImage = !isBlank(imageUrlOrPath);

        if (content == null || (!hasText && !hasImage)) {
            throw new SocialNetworkException("Xianyu postContent requires text or image content");
        }

        if (xianyuConfig.isRealtimeEnabled()) {
            String toUserId = firstNonBlank(
                    asString(raw.get("toUserId")),
                    asString(raw.get("receiverUserId")));
            String itemId = asString(raw.get("itemId"));
            String cid = asString(raw.get("cid"));
            boolean useRealtime = asBoolean(raw.get("useRealtime"), !isBlank(toUserId));

            if (useRealtime && !isBlank(toUserId)) {
                try (XianyuRealtimeClient realtimeClient = openRealtimeClient(session, message -> {
                })) {
                    String targetCid = cid;
                    if (isBlank(targetCid)) {
                        targetCid = realtimeClient.createConversation(toUserId, itemId, Duration.ofSeconds(12));
                    }

                    List<String> sentTypes = new ArrayList<>();
                    if (hasText) {
                        realtimeClient.sendTextMessage(targetCid, toUserId, content.getText());
                        sentTypes.add("text");
                    }
                    if (hasImage) {
                        int imageWidth = asInt(raw.get("imageWidth"), 800);
                        int imageHeight = asInt(raw.get("imageHeight"), 600);
                        realtimeClient.sendImageMessageAuto(
                                targetCid, toUserId, imageUrlOrPath, imageWidth, imageHeight);
                        sentTypes.add("image");
                    }
                    if (sentTypes.isEmpty()) {
                        throw new SocialNetworkException("Xianyu realtime send has no valid payload");
                    }

                    PostResult result = PostResult.success("xianyu-msg-" + System.currentTimeMillis());
                    result.setPublishedAt(Instant.now());
                    result.setShareUrl("realtime://goofish/" + targetCid);
                    Map<String, Object> realtimeResp = new LinkedHashMap<>();
                    realtimeResp.put("channel", "realtime");
                    realtimeResp.put("cid", targetCid);
                    realtimeResp.put("sentTypes", sentTypes);
                    result.setRawResponse(toJson(realtimeResp));
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Send message via Xianyu realtime failed, fallback to DOM: {}", e.getMessage());
                } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
                    logger.warn("Send message via Xianyu realtime failed, fallback to DOM: {}", e.getMessage());
                } catch (Exception e) {
                    logger.warn("Send message via Xianyu realtime failed, fallback to DOM: {}", e.getMessage());
                }
            }
        }

        if (!hasText) {
            throw new SocialNetworkException("Xianyu DOM fallback only supports text content");
        }

        ensureDriver();
        restoreSessionCookiesIfNeeded(session);

        String targetUrl = resolveTargetUrl(content);

        try {
            driver.get(targetUrl);

            WebElement input = waitForElementBySelectors(
                    xianyuConfig.getMessageInputSelectors(),
                    Duration.ofSeconds(20),
                    true);
            if (input == null) {
                throw new SocialNetworkException("Cannot find message input element on page: " + targetUrl);
            }

            typeMessage(input, content.getText());

            WebElement sendButton = waitForElementBySelectors(
                    xianyuConfig.getSendButtonSelectors(),
                    Duration.ofSeconds(4),
                    false);
            if (sendButton != null) {
                sendButton.click();
            } else {
                input.sendKeys(Keys.ENTER);
            }

            PostResult result = PostResult.success("xianyu-msg-" + System.currentTimeMillis());
            result.setPublishedAt(Instant.now());
            result.setShareUrl(targetUrl);
            return result;
        } catch (SocialNetworkException e) {
            throw e;
        } catch (Exception e) {
            throw new SocialNetworkException("Xianyu send message failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deletePost(SocialSession session, String postId) throws SocialAuthenticationException {
        validateSessionOrThrow(session);
        logger.warn("XianyuProvider deletePost is not implemented yet, postId={}", postId);
        return false;
    }

    @Override
    public Object getPostDetail(SocialSession session, String postId) throws SocialAuthenticationException {
        validateSessionOrThrow(session);

        if (isBlank(postId)) {
            return Collections.emptyMap();
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("postId", postId);
        detail.put("capturedAt", Instant.now().toString());

        if (postId.startsWith("http://") || postId.startsWith("https://")) {
            ensureDriver();
            restoreSessionCookiesIfNeeded(session);
            driver.get(postId);
            detail.put("url", driver.getCurrentUrl());
            detail.put("title", driver.getTitle());
        }
        return detail;
    }

    @Override
    public Object getTimeline(SocialSession session, int limit) throws SocialAuthenticationException {
        validateSessionOrThrow(session);
        int safeLimit = limit > 0 ? Math.min(limit, 50) : 20;
        if (xianyuConfig.isTimelinePreferMtop()) {
            try {
                Map<String, Object> mtopTimeline = fetchTimelineByMtop(session, safeLimit);
                if (mtopTimeline != null) {
                    return mtopTimeline;
                }
            } catch (Exception e) {
                logger.warn("Fetch Xianyu timeline by MTOP failed, fallback to DOM: {}", e.getMessage());
            }
        }

        ensureDriver();
        restoreSessionCookiesIfNeeded(session);
        driver.get(firstNonBlank(xianyuConfig.getMessageUrl(), xianyuConfig.getBaseUrl()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "dom");
        result.put("url", driver.getCurrentUrl());
        result.put("title", driver.getTitle());
        result.put("capturedAt", Instant.now().toString());
        result.put("items", extractTimelineItems(safeLimit));
        return result;
    }

    @Override
    public boolean isSessionValid(SocialSession session) {
        return validateSession(session);
    }

    @Override
    public boolean validateSession(SocialSession session) {
        if (session == null || session.getPlatform() != SocialPlatform.XIANYU) {
            return false;
        }
        if (isBlank(session.getAccessToken()) || !session.getAccessToken().startsWith("xianyu-session-")) {
            return false;
        }
        if (session.isExpired()) {
            return false;
        }

        Map<String, String> cookies = extractSessionCookies(session);
        return hasRequiredAuthCookies(cookies);
    }

    @Override
    public void logout(SocialSession session) {
        super.logout(session);
        closeDriver();
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        throw new UnsupportedOperationException("XianyuProvider does not support OAuth URL mode");
    }

    @Override
    public SocialSession handleCallback(String authorizationCode, String redirectUri)
            throws SocialAuthenticationException {
        throw new SocialAuthenticationException("XianyuProvider does not support OAuth callback mode");
    }

    public synchronized void closeDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                logger.warn("Close Xianyu chrome driver failed", e);
            } finally {
                driver = null;
            }
        }
    }

    public Optional<WebDriver> getDriver() {
        return Optional.ofNullable(driver);
    }

    /**
     * 创建并连接闲鱼实时消息客户端
     */
    public XianyuRealtimeClient openRealtimeClient(
            SocialSession session,
            XianyuRealtimeClient.MessageListener listener)
            throws SocialAuthenticationException, SocialNetworkException {
        validateSessionOrThrow(session);

        Map<String, String> cookies = extractSessionCookies(session);
        if (!hasRequiredAuthCookies(cookies)) {
            throw new SocialAuthenticationException("Xianyu session cookies are missing required auth fields");
        }

        String userId = firstNonBlank(session.getUserId(), resolveUserId(cookies));
        XianyuRealtimeClient client = new XianyuRealtimeClient(xianyuConfig, cookies, userId, listener);
        try {
            client.connect();
            return client;
        } catch (Exception e) {
            client.close();
            throw new SocialNetworkException("Open Xianyu realtime client failed: " + e.getMessage(), e);
        }
    }

    /**
     * 订阅实时消息（调用方负责关闭返回的 client）
     */
    public XianyuRealtimeClient subscribeMessages(
            SocialSession session,
            XianyuRealtimeClient.MessageListener listener)
            throws SocialAuthenticationException, SocialNetworkException {
        return openRealtimeClient(session, listener);
    }

    /**
     * 创建二维码登录会话
     */
    public XianyuQrLoginSession createQrLoginSession() throws SocialAuthenticationException {
        try {
            return qrLoginManager.createSession();
        } catch (Exception e) {
            throw new SocialAuthenticationException("Create Xianyu QR login session failed: " + e.getMessage(), e);
        }
    }

    /**
     * 查询二维码登录会话状态
     */
    public XianyuQrLoginSession getQrLoginSessionStatus(String sessionId) {
        return qrLoginManager.getSessionStatus(sessionId);
    }

    /**
     * 失效二维码登录会话
     */
    public boolean invalidateQrLoginSession(String sessionId) {
        return qrLoginManager.invalidateSession(sessionId);
    }

    /**
     * 创建无头浏览器二维码登录会话
     */
    public XianyuHeadlessQrLoginSession createHeadlessQrLoginSession(String loginUrl, Integer expiresInSeconds) {
        return headlessQrLoginManager.createSession(loginUrl, expiresInSeconds);
    }

    /**
     * 查询无头浏览器二维码登录会话状态
     */
    public XianyuHeadlessQrLoginSession getHeadlessQrLoginSessionStatus(String sessionId) {
        return headlessQrLoginManager.getSessionStatus(sessionId);
    }

    /**
     * 失效无头浏览器二维码登录会话
     */
    public boolean invalidateHeadlessQrLoginSession(String sessionId) {
        return headlessQrLoginManager.invalidateSession(sessionId);
    }

    private synchronized void ensureDriver() {
        if (driver == null) {
            driver = new ChromeDriver(chromeConfig.toChromeOptions());
            driver.manage().timeouts()
                    .pageLoadTimeout(Duration.ofMillis(chromeConfig.getPageLoadTimeout()))
                    .implicitlyWait(Duration.ofMillis(chromeConfig.getImplicitWaitTimeout()))
                    .setScriptTimeout(Duration.ofMillis(chromeConfig.getScriptTimeout()));
        }
    }

    private XianyuCredentials normalizeCredentials(Object credentials) throws SocialAuthenticationException {
        if (credentials == null) {
            return new XianyuCredentials();
        }

        if (credentials instanceof XianyuCredentials) {
            return (XianyuCredentials) credentials;
        }

        if (credentials instanceof String) {
            XianyuCredentials xianyuCredentials = new XianyuCredentials();
            xianyuCredentials.setCookieHeader((String) credentials);
            return xianyuCredentials;
        }

        if (credentials instanceof Map<?, ?> map) {
            XianyuCredentials xianyuCredentials = new XianyuCredentials();
            Map<String, String> cookieMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = entry.getKey().toString();
                Object value = entry.getValue();

                if ("startUrl".equalsIgnoreCase(key)) {
                    xianyuCredentials.setStartUrl(value.toString());
                    continue;
                }
                if ("cookieHeader".equalsIgnoreCase(key)) {
                    xianyuCredentials.setCookieHeader(value.toString());
                    continue;
                }
                if ("loginTimeoutSeconds".equalsIgnoreCase(key)) {
                    long timeout = parseLong(value, xianyuCredentials.getLoginTimeoutSeconds());
                    xianyuCredentials.setLoginTimeoutSeconds(timeout);
                    continue;
                }
                if ("allowManualLogin".equalsIgnoreCase(key)) {
                    xianyuCredentials.setAllowManualLogin(asBoolean(value, true));
                    continue;
                }
                if ("preferQrLogin".equalsIgnoreCase(key)) {
                    xianyuCredentials.setPreferQrLogin(asBoolean(value, false));
                    continue;
                }
                if ("qrLoginSessionId".equalsIgnoreCase(key)) {
                    xianyuCredentials.setQrLoginSessionId(value.toString());
                    continue;
                }
                if ("cookies".equalsIgnoreCase(key) && value instanceof Map<?, ?> rawCookies) {
                    rawCookies.forEach((k, v) -> {
                        if (k != null && v != null) {
                            cookieMap.put(k.toString(), v.toString());
                        }
                    });
                    continue;
                }

                cookieMap.put(key, value.toString());
            }
            xianyuCredentials.setCookies(cookieMap);
            return xianyuCredentials;
        }

        throw new SocialAuthenticationException(
                "Unsupported Xianyu credentials type: " + credentials.getClass().getName());
    }

    private void waitUntilLoggedIn(long timeoutSeconds, boolean allowManualLogin)
            throws SocialAuthenticationException {
        Map<String, String> currentCookies = exportCookies();
        if (hasRequiredAuthCookies(currentCookies)) {
            return;
        }

        if (!allowManualLogin) {
            throw new SocialAuthenticationException("Manual login disabled and cookies are not valid");
        }

        long start = System.currentTimeMillis();
        long timeoutMs = Math.max(timeoutSeconds, 1) * 1000;
        long pollInterval = Math.max(xianyuConfig.getPollIntervalMillis(), 300);
        logger.info("Waiting for Xianyu login completion (timeout={}s)...", timeoutSeconds);

        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SocialAuthenticationException("Interrupted while waiting for Xianyu login", e);
            }

            currentCookies = exportCookies();
            if (hasRequiredAuthCookies(currentCookies)) {
                return;
            }
        }

        throw new SocialAuthenticationException("Xianyu login timeout: required auth cookies not found");
    }

    private SocialSession buildAuthenticatedSession(Map<String, String> sessionCookies, String startUrl) {
        SocialSession session = new SocialSession();
        session.setPlatform(SocialPlatform.XIANYU);
        session.setUserId(resolveUserId(sessionCookies));
        session.setAccessToken("xianyu-session-" + UUID.randomUUID());
        session.setExpiresAt(Instant.now().plus(Duration.ofHours(12)));
        session.setRawData(serializeSessionData(sessionCookies, startUrl));
        return session;
    }

    private String resolveAuthStartUrl(XianyuCredentials auth) {
        if (auth != null && !isBlank(auth.getStartUrl())) {
            return auth.getStartUrl();
        }
        if (auth != null && auth.isPreferQrLogin()) {
            return DEFAULT_QR_LOGIN_URL;
        }
        return firstNonBlank(xianyuConfig.getBaseUrl(), "https://www.goofish.com/");
    }

    private Map<String, String> resolveQrSessionCookies(String qrSessionId) throws SocialAuthenticationException {
        XianyuQrLoginSession qrSession = qrLoginManager.getSessionStatus(qrSessionId);
        if (qrSession == null) {
            throw new SocialAuthenticationException("Xianyu QR login session not found: " + qrSessionId);
        }
        if (!XianyuQrLoginSession.STATUS_SUCCESS.equals(qrSession.getStatus())) {
            throw new SocialAuthenticationException(
                    "Xianyu QR login session is not ready: status=" + qrSession.getStatus()
                            + ", message=" + firstNonBlank(qrSession.getMessage(), ""));
        }
        Map<String, String> cookies = parseCookieHeader(qrSession.getCookieHeader());
        if (cookies.isEmpty()) {
            throw new SocialAuthenticationException("Xianyu QR login session has empty cookies");
        }
        return cookies;
    }

    private String resolveTargetUrl(SocialContent content) {
        String fromLink = content.getLinkUrl();
        if (!isBlank(fromLink)) {
            return fromLink;
        }

        if (!isBlank(content.getRawData())) {
            try {
                Map<String, Object> rawData = objectMapper.readValue(
                        content.getRawData(),
                        new TypeReference<Map<String, Object>>() {
                        });
                Object chatUrl = rawData.get("chatUrl");
                if (chatUrl == null) {
                    chatUrl = rawData.get("messageUrl");
                }
                if (chatUrl instanceof String && !isBlank((String) chatUrl)) {
                    return (String) chatUrl;
                }
            } catch (Exception ignore) {
                // Raw data parsing failure is non-fatal and falls back to default message URL.
            }
        }

        return firstNonBlank(xianyuConfig.getMessageUrl(), xianyuConfig.getBaseUrl());
    }

    private void typeMessage(WebElement input, String text) {
        String contentEditable = input.getAttribute("contenteditable");
        boolean isContentEditable = "true".equalsIgnoreCase(contentEditable);

        if (isContentEditable && driver instanceof JavascriptExecutor javascriptExecutor) {
            javascriptExecutor.executeScript(
                    "arguments[0].focus(); arguments[0].innerText='';",
                    input);
            input.sendKeys(text);
            return;
        }

        input.clear();
        input.sendKeys(text);
    }

    private WebElement waitForElementBySelectors(List<String> selectors, Duration timeout, boolean visible) {
        if (selectors == null || selectors.isEmpty()) {
            return null;
        }

        WebDriverWait wait = new WebDriverWait(driver, timeout);
        for (String selector : selectors) {
            if (isBlank(selector)) {
                continue;
            }
            try {
                if (visible) {
                    return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector)));
                }
                return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
            } catch (TimeoutException ignored) {
                // Try next selector.
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractTimelineItems(int limit) {
        if (!(driver instanceof JavascriptExecutor javascriptExecutor)) {
            return Collections.emptyList();
        }

        Object jsResult = javascriptExecutor.executeScript(
                "const max = arguments[0];" +
                "const nodes = Array.from(document.querySelectorAll('a,button,div'));" +
                "const out = [];" +
                "for (const n of nodes) {" +
                "  const text = (n.innerText || '').trim().replace(/\\s+/g, ' ');" +
                "  if (!text || text.length < 2) continue;" +
                "  if (out.some(item => item.text === text)) continue;" +
                "  const href = n.href || n.getAttribute('data-href') || '';" +
                "  out.push({text: text.slice(0, 120), href: href});" +
                "  if (out.length >= max) break;" +
                "}" +
                "return out;",
                limit);

        if (!(jsResult instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> cleanMap = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        cleanMap.put(k.toString(), v);
                    }
                });
                timeline.add(cleanMap);
            }
        }
        return timeline;
    }

    private void restoreSessionCookiesIfNeeded(SocialSession session) {
        Map<String, String> currentCookies = exportCookies();
        if (hasRequiredAuthCookies(currentCookies)) {
            return;
        }

        Map<String, String> sessionCookies = extractSessionCookies(session);
        if (!sessionCookies.isEmpty()) {
            injectCookies(firstNonBlank(xianyuConfig.getBaseUrl(), "https://www.goofish.com/"), sessionCookies);
        }
    }

    private void injectCookies(String startUrl, Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        driver.get(startUrl);
        driver.manage().deleteAllCookies();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            try {
                driver.manage().addCookie(new Cookie(entry.getKey().trim(), entry.getValue()));
            } catch (Exception e) {
                logger.debug("Ignore invalid cookie {}: {}", entry.getKey(), e.getMessage());
            }
        }
        driver.navigate().refresh();
    }

    private Map<String, String> resolveIncomingCookies(XianyuCredentials credentials) {
        if (credentials == null) {
            return Collections.emptyMap();
        }
        if (credentials.getCookies() != null && !credentials.getCookies().isEmpty()) {
            return new LinkedHashMap<>(credentials.getCookies());
        }
        if (!isBlank(credentials.getCookieHeader())) {
            return parseCookieHeader(credentials.getCookieHeader());
        }
        return Collections.emptyMap();
    }

    private Map<String, String> exportCookies() {
        if (driver == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> result = new LinkedHashMap<>();
            for (Cookie cookie : driver.manage().getCookies()) {
                result.put(cookie.getName(), cookie.getValue());
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private boolean hasRequiredAuthCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return false;
        }
        List<String> required = xianyuConfig.getRequiredAuthCookies();
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String name : required) {
            if (isBlank(name) || isBlank(cookies.get(name))) {
                return false;
            }
        }
        return true;
    }

    private String resolveUserId(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        return firstNonBlank(cookies.get("unb"), cookies.get("cookie2"), cookies.get("tracknick"));
    }

    private String serializeSessionData(Map<String, String> cookies, String startUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "xianyu");
        payload.put("capturedAt", Instant.now().toString());
        String currentUrl = driver != null ? driver.getCurrentUrl() : null;
        payload.put("currentUrl", currentUrl);
        payload.put("startUrl", startUrl);
        String sessionId = extractSessionIdFromUrl(firstNonBlank(currentUrl, startUrl));
        if (!isBlank(sessionId)) {
            payload.put("sessionId", sessionId);
        }
        payload.put("cookieHeader", buildCookieHeader(cookies));
        payload.put("cookies", cookies);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.warn("Serialize Xianyu session rawData failed", e);
            return "{\"provider\":\"xianyu\"}";
        }
    }

    private Map<String, String> extractSessionCookies(SocialSession session) {
        if (session == null || isBlank(session.getRawData())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> data = objectMapper.readValue(
                    session.getRawData(),
                    new TypeReference<Map<String, Object>>() {
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
            if (cookieHeader instanceof String header && !isBlank(header)) {
                return parseCookieHeader(header);
            }
        } catch (Exception e) {
            logger.debug("Parse Xianyu session rawData failed: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> parseRawDataMap(String rawData) {
        if (isBlank(rawData)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(rawData, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String resolveSessionId(SocialSession session) {
        Map<String, Object> rawData = parseRawDataMap(session != null ? session.getRawData() : null);
        String directSessionId = asString(rawData.get("sessionId"));
        if (!isBlank(directSessionId)) {
            return directSessionId;
        }

        String currentUrl = asString(rawData.get("currentUrl"));
        String fromCurrentUrl = extractSessionIdFromUrl(currentUrl);
        if (!isBlank(fromCurrentUrl)) {
            return fromCurrentUrl;
        }

        String startUrl = asString(rawData.get("startUrl"));
        return extractSessionIdFromUrl(startUrl);
    }

    private String extractSessionIdFromUrl(String url) {
        if (isBlank(url)) {
            return null;
        }
        int idx = url.indexOf("sessionId=");
        if (idx < 0) {
            return null;
        }
        String tail = url.substring(idx + "sessionId=".length());
        int amp = tail.indexOf("&");
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }

    private Map<String, Object> fetchTimelineByMtop(SocialSession session, int limit)
            throws SocialNetworkException {
        Map<String, String> cookies = extractSessionCookies(session);
        if (cookies.isEmpty()) {
            throw new SocialNetworkException("No session cookies found");
        }
        String tk = cookies.get("_m_h5_tk");
        if (isBlank(tk)) {
            throw new SocialNetworkException("Cookie _m_h5_tk is missing");
        }

        String token = tk;
        int underscore = tk.indexOf('_');
        if (underscore > 0) {
            token = tk.substring(0, underscore);
        }

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", xianyuConfig.getMtopRequestType());
        req.put("fetchs", limit);
        req.put("start", xianyuConfig.getMtopStart());
        req.put("includeRequestMsg", xianyuConfig.isMtopIncludeRequestMsg());
        String sessionId = resolveSessionId(session);
        if (!isBlank(sessionId)) {
            req.put("sessionId", sessionId);
        }

        String reqJson = toJson(req);
        String dataJson = "{\"req\":\"" + escapeJsonForNestedString(reqJson) + "\"}";

        String apiName = firstNonBlank(
                xianyuConfig.getMtopMessageSyncApi(),
                "mtop.taobao.idlemessage.pc.message.sync");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = md5Hex(token + "&" + timestamp + "&" + xianyuConfig.getMtopAppKey() + "&" + dataJson);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", firstNonBlank(xianyuConfig.getMtopJsv(), "2.7.2"));
        params.put("appKey", firstNonBlank(xianyuConfig.getMtopAppKey(), "34839810"));
        params.put("t", timestamp);
        params.put("sign", sign);
        params.put("v", firstNonBlank(xianyuConfig.getMtopApiVersion(), "1.0"));
        params.put("type", "originaljson");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", String.valueOf(Math.max(1000, xianyuConfig.getMtopTimeoutMillis())));
        params.put("api", apiName);
        params.put("sessionOption", "AutoLoginOnly");
        params.put("spm_cnt", "a21ybx.im.0.0");
        params.put("spm_pre", "a21ybx.home.sidebar.1.4c053da6vYwnmf");
        params.put("log_id", "social-sdk");

        String endpoint = "https://h5api.m.goofish.com/h5/"
                + apiName + "/" + firstNonBlank(xianyuConfig.getMtopApiVersion(), "1.0") + "/"
                + "?" + buildQuery(params);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(2000, xianyuConfig.getMtopTimeoutMillis())))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(Math.max(2000, xianyuConfig.getMtopTimeoutMillis())))
                .header("Accept", "application/json")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://www.goofish.com")
                .header("Referer", "https://www.goofish.com/")
                .header("User-Agent", xianyuConfig.getRealtimeUserAgent())
                .header("Cookie", buildCookieHeader(cookies))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "data=" + URLEncoder.encode(dataJson, StandardCharsets.UTF_8)))
                .build();

        String body;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SocialNetworkException("MTOP HTTP status " + response.statusCode());
            }
            body = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SocialNetworkException("MTOP request interrupted", e);
        } catch (Exception e) {
            throw new SocialNetworkException("MTOP request failed: " + e.getMessage(), e);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (!isMtopSuccess(root)) {
                throw new SocialNetworkException("MTOP response is not success: " + body);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "mtop");
            result.put("api", apiName);
            result.put("capturedAt", Instant.now().toString());
            result.put("ret", toStringList(root.path("ret")));

            JsonNode dataNode = root.path("data");
            result.put("fetchs", dataNode.path("fetchs").asInt(limit));
            result.put("hasMore", dataNode.path("hasMore").asBoolean(false));
            result.put("sessionId", dataNode.path("sessionId").asText(null));

            List<Map<String, Object>> items = new ArrayList<>();
            JsonNode messages = dataNode.path("messages");
            if (messages.isArray()) {
                for (JsonNode message : messages) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("messageUuid", message.path("messageUuid").asText(null));
                    item.put("timeStamp", message.path("timeStamp").asLong());
                    item.put("arg1", message.path("arg1").asText(null));

                    JsonNode senderInfo = message.path("senderInfo");
                    item.put("senderNick", senderInfo.path("nick").asText(null));
                    item.put("senderUserId", senderInfo.path("userId").asText(null));

                    JsonNode sessionInfo = message.path("sessionInfo");
                    item.put("chatId", sessionInfo.path("sessionId").asText(null));
                    item.put("sessionType", sessionInfo.path("sessionType").asText(null));

                    JsonNode content = message.path("content");
                    item.put("contentText", extractTimelineContentText(content));
                    items.add(item);
                }
            }
            result.put("items", items);
            return result;
        } catch (SocialNetworkException e) {
            throw e;
        } catch (Exception e) {
            throw new SocialNetworkException("Parse MTOP timeline response failed: " + e.getMessage(), e);
        }
    }

    private boolean isMtopSuccess(JsonNode root) {
        JsonNode ret = root.path("ret");
        return ret.isArray() && !ret.isEmpty() && ret.get(0).asText("").contains("SUCCESS");
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText(null));
        }
        return values;
    }

    private String extractTimelineContentText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return null;
        }
        String text = firstNonBlank(
                content.path("text").path("text").asText(null),
                content.path("text").asText(null),
                content.path("reminderContent").asText(null),
                content.path("card").path("title").asText(null),
                content.path("card").path("text").asText(null),
                content.path("custom").path("text").asText(null));
        if (!isBlank(text)) {
            return text;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        return content.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize JSON failed", e);
        }
    }

    private String escapeJsonForNestedString(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 calculation failed", e);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String str = value.toString();
        if ("true".equalsIgnoreCase(str) || "1".equals(str)) {
            return true;
        }
        if ("false".equalsIgnoreCase(str) || "0".equals(str)) {
            return false;
        }
        return defaultValue;
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String resolveFirstImageUrl(SocialContent content, Map<String, Object> rawData) {
        if (content != null && content.getImageUrls() != null) {
            for (String imageUrl : content.getImageUrls()) {
                if (!isBlank(imageUrl)) {
                    return imageUrl;
                }
            }
        }
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }
        return firstNonBlank(
                asString(rawData.get("imageUrl")),
                asString(rawData.get("imagePath")),
                asString(rawData.get("localImagePath")));
    }

    private Map<String, String> parseCookieHeader(String header) {
        if (isBlank(header)) {
            return Collections.emptyMap();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        String[] parts = header.split(";");
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && !isBlank(kv[0])) {
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
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.toString();
    }

    private String decodeCookieValue(String value) {
        if (isBlank(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
