package com.socialsdk.xianyu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsdk.chrome.config.ChromeConfig;
import com.socialsdk.xianyu.config.XianyuConfig;
import com.socialsdk.xianyu.model.XianyuHeadlessQrLoginSession;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 闲鱼无头浏览器二维码登录管理器
 */
public class XianyuHeadlessQrLoginManager {

    private static final Logger logger = LoggerFactory.getLogger(XianyuHeadlessQrLoginManager.class);
    private static final String DEFAULT_QR_LOGIN_URL =
            "https://passport.goofish.com/mini_login.htm?lang=zh_cn&appName=xianyu&appEntrance=web&styleType=vertical&stie=77";
    private static final int DEFAULT_EXPIRE_SECONDS = 300;
    private static final Pattern DATA_URI_PATTERN = Pattern.compile(
            "data:image/(?:png|jpeg|gif|webp|bmp|svg\\+xml|svg);base64,([A-Za-z0-9+/=]+)");
    private static final List<String> QR_SELECTORS = List.of(
            "#J_QRCodeImg",
            ".qrcode-img",
            ".qrcode img",
            ".qrcode canvas",
            "img[alt*='二维码']",
            "img[src*='qrcode']",
            "img[src*='qr']",
            "[class*='qrcode'] img",
            "[class*='qrcode'] canvas",
            "canvas");

    private final ChromeConfig chromeConfig;
    private final XianyuConfig xianyuConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, InternalSession> sessions = new ConcurrentHashMap<>();

    public XianyuHeadlessQrLoginManager(ChromeConfig chromeConfig, XianyuConfig xianyuConfig) {
        this.chromeConfig = chromeConfig != null ? chromeConfig : new ChromeConfig();
        this.xianyuConfig = xianyuConfig != null ? xianyuConfig : new XianyuConfig();
    }

    public XianyuHeadlessQrLoginSession createSession(String loginUrl, Integer expiresInSeconds) {
        cleanupExpiredSessions();

        InternalSession session = new InternalSession();
        session.sessionId = UUID.randomUUID().toString();
        session.createdAt = Instant.now();
        session.expiresInSeconds = normalizeExpireSeconds(expiresInSeconds);
        session.loginUrl = firstNonBlank(loginUrl, DEFAULT_QR_LOGIN_URL);

        try {
            session.driver = createHeadlessDriver();
            session.driver.get(session.loginUrl);
            String qrBase64 = captureQrBase64(session.driver, Duration.ofSeconds(20));
            if (isBlank(qrBase64)) {
                throw new IllegalStateException("Cannot locate QR code element on login page");
            }
            session.qrCodeBase64 = qrBase64;
            session.qrCodeDataUrl = "data:image/png;base64," + qrBase64;
            session.status = XianyuHeadlessQrLoginSession.STATUS_WAITING;
            sessions.put(session.sessionId, session);
            return toPublicSession(session);
        } catch (Exception e) {
            closeDriverQuietly(session.driver);
            throw new IllegalStateException("Create headless qr session failed: " + e.getMessage(), e);
        }
    }

    public XianyuHeadlessQrLoginSession getSessionStatus(String sessionId) {
        InternalSession session = sessions.get(sessionId);
        if (session == null) {
            XianyuHeadlessQrLoginSession notFound = new XianyuHeadlessQrLoginSession();
            notFound.setSessionId(sessionId);
            notFound.setStatus(XianyuHeadlessQrLoginSession.STATUS_NOT_FOUND);
            notFound.setMessage("headless qr session not found");
            return notFound;
        }

        if (isExpired(session) && !XianyuHeadlessQrLoginSession.STATUS_SUCCESS.equals(session.status)) {
            session.status = XianyuHeadlessQrLoginSession.STATUS_EXPIRED;
            session.message = "headless qr session expired";
            closeAndDetachDriver(session);
            return toPublicSession(session);
        }

        if (XianyuHeadlessQrLoginSession.STATUS_SUCCESS.equals(session.status)
                || XianyuHeadlessQrLoginSession.STATUS_EXPIRED.equals(session.status)
                || XianyuHeadlessQrLoginSession.STATUS_ERROR.equals(session.status)) {
            return toPublicSession(session);
        }

        try {
            pollSession(session);
        } catch (Exception e) {
            session.status = XianyuHeadlessQrLoginSession.STATUS_ERROR;
            session.message = "poll session failed: " + e.getMessage();
            closeAndDetachDriver(session);
        }
        return toPublicSession(session);
    }

    public boolean invalidateSession(String sessionId) {
        InternalSession removed = sessions.remove(sessionId);
        if (removed == null) {
            return false;
        }
        closeAndDetachDriver(removed);
        return true;
    }

    public void cleanupExpiredSessions() {
        for (Map.Entry<String, InternalSession> entry : sessions.entrySet()) {
            InternalSession session = entry.getValue();
            if (!isExpired(session)) {
                continue;
            }
            closeAndDetachDriver(session);
            sessions.remove(entry.getKey());
        }
    }

    private void pollSession(InternalSession session) {
        if (session.driver == null) {
            session.status = XianyuHeadlessQrLoginSession.STATUS_ERROR;
            session.message = "driver not available";
            return;
        }

        session.currentUrl = safeCurrentUrl(session.driver);
        Map<String, String> cookies = exportCookies(session.driver);
        if (hasRequiredAuthCookies(cookies)) {
            completeSuccess(session, cookies);
            return;
        }

        boolean qrVisible = hasQrElement(session.driver);
        session.status = qrVisible
                ? XianyuHeadlessQrLoginSession.STATUS_WAITING
                : XianyuHeadlessQrLoginSession.STATUS_SCANNED;

        if (isBlank(session.qrCodeBase64) && qrVisible) {
            String refreshedBase64 = captureQrBase64(session.driver, Duration.ofSeconds(4));
            if (!isBlank(refreshedBase64)) {
                session.qrCodeBase64 = refreshedBase64;
                session.qrCodeDataUrl = "data:image/png;base64," + refreshedBase64;
            }
        }
    }

    private void completeSuccess(InternalSession session, Map<String, String> cookies) {
        session.status = XianyuHeadlessQrLoginSession.STATUS_SUCCESS;
        session.message = "login success";
        session.completedAt = Instant.now();
        session.currentUrl = safeCurrentUrl(session.driver);
        session.cookies = cookies;
        session.cookieHeader = buildCookieHeader(cookies);
        session.userId = firstNonBlank(cookies.get("unb"), cookies.get("cookie2"));
        session.tracknick = decodeCookieValue(cookies.get("tracknick"));

        collectBrowserState(session);
        closeAndDetachDriver(session);
    }

    private void collectBrowserState(InternalSession session) {
        WebDriver driver = session.driver;
        if (driver == null) {
            return;
        }

        Set<String> origins = new LinkedHashSet<>();
        addOrigin(origins, safeCurrentUrl(driver));
        addOrigin(origins, firstNonBlank(xianyuConfig.getBaseUrl(), "https://www.goofish.com/"));
        addOrigin(origins, xianyuConfig.getMessageUrl());

        for (String origin : origins) {
            try {
                driver.get(origin);
                sleepSilently(400);
                session.localStorageByOrigin.put(origin, readStorage(driver, true));
                session.sessionStorageByOrigin.put(origin, readStorage(driver, false));
                session.indexedDbByOrigin.put(origin, readIndexedDb(driver));
                session.cacheStorageByOrigin.put(origin, readCacheStorage(driver));
            } catch (Exception e) {
                logger.debug("Capture browser storage failed for origin {}: {}", origin, e.getMessage());
                session.localStorageByOrigin.putIfAbsent(origin, Collections.emptyMap());
                session.sessionStorageByOrigin.putIfAbsent(origin, Collections.emptyMap());
                session.indexedDbByOrigin.putIfAbsent(origin, Collections.emptyList());
                session.cacheStorageByOrigin.putIfAbsent(origin, Collections.emptyList());
            }
        }

        try {
            Object ua = ((JavascriptExecutor) driver).executeScript("return navigator.userAgent;");
            if (ua != null) {
                session.userAgent = ua.toString();
            }
        } catch (Exception e) {
            session.userAgent = null;
        }
    }

    private ChromeDriver createHeadlessDriver() {
        ChromeOptions options = chromeConfig.toChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        ChromeDriver chromeDriver = new ChromeDriver(options);
        chromeDriver.manage().timeouts()
                .pageLoadTimeout(Duration.ofMillis(chromeConfig.getPageLoadTimeout()))
                .implicitlyWait(Duration.ofMillis(chromeConfig.getImplicitWaitTimeout()))
                .setScriptTimeout(Duration.ofMillis(chromeConfig.getScriptTimeout()));
        return chromeDriver;
    }

    private String captureQrBase64(WebDriver driver, Duration timeout) {
        long deadline = System.currentTimeMillis() + Math.max(timeout.toMillis(), 1000L);
        while (System.currentTimeMillis() < deadline) {
            String found = tryCaptureQrInCurrentContext(driver);
            if (!isBlank(found)) {
                return found;
            }
            found = tryCaptureQrInIframes(driver);
            if (!isBlank(found)) {
                return found;
            }
            sleepSilently(300);
        }
        return null;
    }

    private String tryCaptureQrInIframes(WebDriver driver) {
        List<WebElement> iframes = driver.findElements(By.cssSelector("iframe,frame"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                String found = tryCaptureQrInCurrentContext(driver);
                if (!isBlank(found)) {
                    return found;
                }
            } catch (Exception ignored) {
                // Try next iframe.
            } finally {
                try {
                    driver.switchTo().defaultContent();
                } catch (Exception ignored) {
                    // Ignore switch failures.
                }
            }
        }
        return null;
    }

    private String tryCaptureQrInCurrentContext(WebDriver driver) {
        for (String selector : QR_SELECTORS) {
            List<WebElement> elements;
            try {
                elements = driver.findElements(By.cssSelector(selector));
            } catch (Exception e) {
                continue;
            }
            for (WebElement element : elements) {
                if (!isLikelyQrElement(element)) {
                    continue;
                }
                String fromDataUri = extractFromElementDataUri(element);
                if (!isBlank(fromDataUri)) {
                    return fromDataUri;
                }
                try {
                    String base64 = element.getScreenshotAs(OutputType.BASE64);
                    if (!isBlank(base64) && base64.length() > 100) {
                        return base64;
                    }
                } catch (Exception ignored) {
                    // Continue with next element.
                }
            }
        }
        return null;
    }

    private boolean hasQrElement(WebDriver driver) {
        for (String selector : QR_SELECTORS) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                for (WebElement element : elements) {
                    if (isLikelyQrElement(element)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // Try next selector.
            }
        }
        return false;
    }

    private boolean isLikelyQrElement(WebElement element) {
        if (element == null) {
            return false;
        }
        try {
            if (!element.isDisplayed()) {
                return false;
            }
            int width = element.getSize().getWidth();
            int height = element.getSize().getHeight();
            return width >= 80 && height >= 80;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractFromElementDataUri(WebElement element) {
        if (element == null) {
            return null;
        }
        String src = firstNonBlank(element.getAttribute("data-src"), element.getAttribute("src"));
        if (isBlank(src) || !src.startsWith("data:image")) {
            return null;
        }
        Matcher matcher = DATA_URI_PATTERN.matcher(src);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Map<String, String> readStorage(WebDriver driver, boolean localStorage) {
        String script = localStorage
                ? "var out={}; for (var i=0;i<localStorage.length;i++){var k=localStorage.key(i); out[k]=localStorage.getItem(k);} return out;"
                : "var out={}; for (var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i); out[k]=sessionStorage.getItem(k);} return out;";
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(script);
            return castToStringMap(result);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<Map<String, Object>> readIndexedDb(WebDriver driver) {
        String script = "var cb = arguments[arguments.length - 1];"
                + "if (!window.indexedDB || !indexedDB.databases) { cb('[]'); return; }"
                + "indexedDB.databases().then(function(dbs){ cb(JSON.stringify(dbs || [])); })"
                + ".catch(function(){ cb('[]'); });";
        try {
            Object result = ((JavascriptExecutor) driver).executeAsyncScript(script);
            String json = result == null ? "[]" : result.toString();
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (TimeoutException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> readCacheStorage(WebDriver driver) {
        String script = "var cb = arguments[arguments.length - 1];"
                + "if (!window.caches || !caches.keys) { cb('[]'); return; }"
                + "caches.keys().then(function(keys){ cb(JSON.stringify(keys || [])); })"
                + ".catch(function(){ cb('[]'); });";
        try {
            Object result = ((JavascriptExecutor) driver).executeAsyncScript(script);
            String json = result == null ? "[]" : result.toString();
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (TimeoutException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, String> castToStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return result;
    }

    private Map<String, String> exportCookies(WebDriver driver) {
        if (driver == null) {
            return Collections.emptyMap();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        try {
            for (Cookie cookie : driver.manage().getCookies()) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        return cookies;
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

    private XianyuHeadlessQrLoginSession toPublicSession(InternalSession session) {
        XianyuHeadlessQrLoginSession out = new XianyuHeadlessQrLoginSession();
        out.setSessionId(session.sessionId);
        out.setStatus(session.status);
        out.setMessage(session.message);
        out.setQrCodeBase64(session.qrCodeBase64);
        out.setQrCodeDataUrl(session.qrCodeDataUrl);
        out.setLoginUrl(session.loginUrl);
        out.setCurrentUrl(session.currentUrl);
        out.setUserAgent(session.userAgent);
        out.setCookieHeader(session.cookieHeader);
        out.setUserId(session.userId);
        out.setTracknick(session.tracknick);
        out.setCookies(new LinkedHashMap<>(session.cookies));
        out.setLocalStorageByOrigin(new LinkedHashMap<>(session.localStorageByOrigin));
        out.setSessionStorageByOrigin(new LinkedHashMap<>(session.sessionStorageByOrigin));
        out.setIndexedDbByOrigin(new LinkedHashMap<>(session.indexedDbByOrigin));
        out.setCacheStorageByOrigin(new LinkedHashMap<>(session.cacheStorageByOrigin));
        out.setCreatedAt(session.createdAt);
        out.setCompletedAt(session.completedAt);
        out.setExpiresInSeconds(session.expiresInSeconds);
        return out;
    }

    private void closeAndDetachDriver(InternalSession session) {
        if (session == null) {
            return;
        }
        closeDriverQuietly(session.driver);
        session.driver = null;
    }

    private void closeDriverQuietly(WebDriver webDriver) {
        if (webDriver == null) {
            return;
        }
        try {
            webDriver.quit();
        } catch (Exception ignored) {
            // Ignore close failures.
        }
    }

    private boolean isExpired(InternalSession session) {
        if (session == null || session.createdAt == null) {
            return true;
        }
        return Instant.now().isAfter(session.createdAt.plusSeconds(Math.max(1, session.expiresInSeconds)));
    }

    private int normalizeExpireSeconds(Integer expiresInSeconds) {
        int value = expiresInSeconds == null ? DEFAULT_EXPIRE_SECONDS : expiresInSeconds;
        if (value < 60) {
            return 60;
        }
        if (value > 1800) {
            return 1800;
        }
        return value;
    }

    private void addOrigin(Set<String> origins, String url) {
        if (isBlank(url)) {
            return;
        }
        try {
            URI uri = URI.create(url.trim());
            if (isBlank(uri.getScheme()) || isBlank(uri.getHost())) {
                return;
            }
            origins.add(uri.getScheme() + "://" + uri.getHost() + "/");
        } catch (Exception ignored) {
            // Ignore malformed URLs.
        }
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

    private String safeCurrentUrl(WebDriver webDriver) {
        if (webDriver == null) {
            return null;
        }
        try {
            return webDriver.getCurrentUrl();
        } catch (Exception e) {
            return null;
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private static final class InternalSession {
        private String sessionId;
        private String status = XianyuHeadlessQrLoginSession.STATUS_WAITING;
        private String message;
        private String qrCodeBase64;
        private String qrCodeDataUrl;
        private String loginUrl;
        private String currentUrl;
        private String userAgent;
        private String cookieHeader;
        private String userId;
        private String tracknick;
        private Map<String, String> cookies = new LinkedHashMap<>();
        private Map<String, Map<String, String>> localStorageByOrigin = new LinkedHashMap<>();
        private Map<String, Map<String, String>> sessionStorageByOrigin = new LinkedHashMap<>();
        private Map<String, List<Map<String, Object>>> indexedDbByOrigin = new LinkedHashMap<>();
        private Map<String, List<String>> cacheStorageByOrigin = new LinkedHashMap<>();
        private Instant createdAt;
        private Instant completedAt;
        private int expiresInSeconds = DEFAULT_EXPIRE_SECONDS;
        private WebDriver driver;
    }
}
