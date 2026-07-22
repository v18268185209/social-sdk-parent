package cn.net.rjnetwork.xianyu.manager.openapi.service;

import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiTokenCache;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenAppCreateRequest;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenAppResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.TokenRequest;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.TokenResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.mapper.OpenAppMapper;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OpenAppService 单测：覆盖凭证签发/校验/账号作用域/禁用与过期拒绝。
 * 不起 Spring 容器，纯 Mockito；CryptoUtil 用 spy 走真实 AES 加解密。
 */
@ExtendWith(MockitoExtension.class)
class OpenAppServiceTest {

    @Mock private OpenAppMapper openAppMapper;
    @Mock private OpenApiTokenCache tokenCache;
    private final CryptoUtil cryptoUtil = spyRealCrypto();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private OpenAppService service;

    private static CryptoUtil spyRealCrypto() {
        // 不 stub 加解密，走真实 AES/CBC 路径，验证明文与密文分离
        return new CryptoUtil("test-secret-for-crypto-util");
    }

    @BeforeEach
    void setUp() {
        // InjectMocks 会用上面的 spy + objectMapper 注入构造器
        service = new OpenAppService(openAppMapper, cryptoUtil, tokenCache, objectMapper);
    }

    // ===== createApp =====

    @Test
    void createApp_blankName_throwsInvalidParam() {
        OpenAppCreateRequest req = new OpenAppCreateRequest();
        req.setAppName(" ");

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.createApp(req));
        assertEquals(OpenApiErrorCode.INVALID_PARAM, e.getErrorCode());

        verifyNoInteractions(openAppMapper);
    }

    @Test
    void createApp_persistsAesSecretAndReturnsPlainOnce() {
        OpenAppCreateRequest req = new OpenAppCreateRequest();
        req.setAppName("my-app");
        req.setBoundAccountIds(List.of(1L, 2L));
        req.setRateLimitPerMinute(30);

        OpenAppResponse resp = service.createApp(req);

        ArgumentCaptor<OpenApp> captor = ArgumentCaptor.forClass(OpenApp.class);
        verify(openAppMapper).insert(captor.capture());
        OpenApp saved = captor.getValue();

        assertEquals("my-app", saved.getAppName());
        assertNotNull(saved.getAppKey());
        assertTrue(saved.getAppKey().startsWith("ak_"), "appKey 应以 ak_ 前缀生成");
        assertNotNull(saved.getAppSecretEnc(), "入库应存密文");
        assertNotEquals(resp.getAppSecret(), saved.getAppSecretEnc(),
                "密文不应等于明文");
        assertEquals("[1,2]", saved.getBoundAccountIds());
        assertEquals(30, saved.getRateLimitPerMinute());
        assertEquals("ENABLED", saved.getStatus());

        // 明文只返回一次
        assertNotNull(resp.getAppSecret());
        assertEquals(64, resp.getAppSecret().length(), "appSecret 为两个 UUID 拼接，应 64 位");
    }

    @Test
    void createApp_nullRate_defaultsTo60() {
        OpenAppCreateRequest req = new OpenAppCreateRequest();
        req.setAppName("x");

        OpenAppResponse resp = service.createApp(req);

        assertEquals(60, resp.getRateLimitPerMinute());
    }

    // ===== issueToken =====

    @Test
    void issueToken_unknownAppKey_throwsUnauthorized() {
        TokenRequest req = new TokenRequest();
        req.setAppKey("ak_unknown");
        req.setAppSecret("anything");

        when(openAppMapper.selectOne(any())).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.issueToken(req));
        assertEquals(OpenApiErrorCode.UNAUTHORIZED, e.getErrorCode());

        verifyNoInteractions(tokenCache);
    }

    @Test
    void issueToken_wrongSecret_throwsUnauthorized() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setAppSecretEnc(cryptoUtil.encrypt("correct-secret"));

        when(openAppMapper.selectOne(any())).thenReturn(app);

        TokenRequest req = new TokenRequest();
        req.setAppKey("ak_x");
        req.setAppSecret("wrong-secret");

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.issueToken(req));
        assertEquals(OpenApiErrorCode.UNAUTHORIZED, e.getErrorCode());

        verifyNoInteractions(tokenCache);
    }

    @Test
    void issueToken_correctSecret_issuesTokenAndUpdatesLastUsed() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setAppSecretEnc(cryptoUtil.encrypt("correct-secret"));

        when(openAppMapper.selectOne(any())).thenReturn(app);

        TokenRequest req = new TokenRequest();
        req.setAppKey("ak_x");
        req.setAppSecret("correct-secret");

        TokenResponse resp = service.issueToken(req);

        assertNotNull(resp.getAccessToken());
        assertEquals("Bearer", resp.getTokenType());
        assertEquals(OpenApiTokenCache.TOKEN_TTL_SECONDS, resp.getExpiresIn());
        verify(tokenCache).put(any(String.class), eq(1L), eq("ak_x"));
        verify(openAppMapper).updateById(app);
        assertNotNull(app.getLastUsedAt(), "最近使用时间应被刷新");
    }

    // ===== resolveToken =====

    @Test
    void resolveToken_cacheMiss_throwsInvalidToken() {
        when(tokenCache.get("stale")).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.resolveToken("stale"));
        assertEquals(OpenApiErrorCode.INVALID_TOKEN, e.getErrorCode());
    }

    @Test
    void resolveToken_appDeleted_throwsInvalidToken() {
        OpenApiTokenCache.TokenEntry entry = new OpenApiTokenCache.TokenEntry(99L, "ak_x");
        when(tokenCache.get("tok")).thenReturn(entry);
        when(openAppMapper.selectById(99L)).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.resolveToken("tok"));
        assertEquals(OpenApiErrorCode.INVALID_TOKEN, e.getErrorCode());
    }

    @Test
    void resolveToken_appDisabled_throwsAppDisabled() {
        OpenApiTokenCache.TokenEntry entry = new OpenApiTokenCache.TokenEntry(1L, "ak_x");
        when(tokenCache.get("tok")).thenReturn(entry);
        when(openAppMapper.selectById(1L)).thenReturn(TestOpenApp.disabled("ak_x"));

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.resolveToken("tok"));
        assertEquals(OpenApiErrorCode.APP_DISABLED, e.getErrorCode());
    }

    @Test
    void resolveToken_appExpired_throwsAppExpired() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setExpireAt(LocalDateTime.now().minusHours(1));

        OpenApiTokenCache.TokenEntry entry = new OpenApiTokenCache.TokenEntry(1L, "ak_x");
        when(tokenCache.get("tok")).thenReturn(entry);
        when(openAppMapper.selectById(1L)).thenReturn(app);

        OpenApiException e = assertThrows(OpenApiException.class, () -> service.resolveToken("tok"));
        assertEquals(OpenApiErrorCode.APP_EXPIRED, e.getErrorCode());
    }

    @Test
    void resolveToken_valid_returnsApp() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiTokenCache.TokenEntry entry = new OpenApiTokenCache.TokenEntry(1L, "ak_x");
        when(tokenCache.get("tok")).thenReturn(entry);
        when(openAppMapper.selectById(1L)).thenReturn(app);

        OpenApp resolved = service.resolveToken("tok");

        assertSame(app, resolved);
    }

    // ===== 账号作用域校验 =====

    @Test
    void assertAccountAccessible_nullAccountId_alwaysAllow() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L, 2L);

        assertDoesNotThrow(() -> service.assertAccountAccessible(app, null));
    }

    @Test
    void assertAccountAccessible_emptyBound_alwaysAllow() {
        OpenApp app = TestOpenApp.enabled("ak_x"); // boundAccountIds = null

        assertDoesNotThrow(() -> service.assertAccountAccessible(app, 999L));
    }

    @Test
    void assertAccountAccessible_inBound_allow() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L, 2L);

        assertDoesNotThrow(() -> service.assertAccountAccessible(app, 2L));
    }

    @Test
    void assertAccountAccessible_outOfBound_throwsAccountForbidden() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L, 2L);

        OpenApiException e = assertThrows(OpenApiException.class,
                () -> service.assertAccountAccessible(app, 3L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
    }

    @Test
    void getBoundAccountIds_malformedJson_returnsEmpty() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setBoundAccountIds("not-a-json");

        Set<Long> ids = service.getBoundAccountIds(app);
        assertNotNull(ids);
        assertTrue(ids.isEmpty(), "JSON 解析失败应降级为空集，不抛异常");
    }

    @Test
    void listApps_neverReturnsPlainSecret() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setAppSecretEnc("enc-xxx");
        when(openAppMapper.selectList(any())).thenReturn(List.of(app));

        List<OpenAppResponse> list = service.listApps();

        assertEquals(1, list.size());
        assertNull(list.get(0).getAppSecret(),
                "listApps 不应返回明文 appSecret，恒为 null");
    }
}
