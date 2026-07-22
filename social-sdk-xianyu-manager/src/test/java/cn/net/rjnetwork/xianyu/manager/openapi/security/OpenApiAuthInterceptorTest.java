package cn.net.rjnetwork.xianyu.manager.openapi.security;

import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OpenApiAuthInterceptor 单测：鉴权链路 + 限流 + Context 生命周期。
 * 用 StringWriter 收集响应体，验证错误信封 JSON 落盘。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiAuthInterceptorTest {

    @Mock private OpenAppService openAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private OpenApiAuthInterceptor interceptor;

    private StringWriter responseBuffer;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new OpenApiAuthInterceptor(openAppService, objectMapper);
        responseBuffer = new StringWriter();
    }

    @AfterEach
    void tearDown() {
        OpenApiContext.clear(); // 防止 ThreadLocal 跨测试污染
    }

    // ===== 鉴权失败链路（不走 Exception，直接写响应 + return false） =====

    @Test
    void preHandle_noAuthHeader_writesUnauthorized_andReturnsFalse() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn(null);

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertFalse(proceed);
        verify(resp).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertJsonCode(OpenApiErrorCode.UNAUTHORIZED.code, responseBuffer.toString());
        verifyNoInteractions(openAppService);
        assertNull(OpenApiContext.getOpenApp(), "鉴权失败不应写入 Context");
    }

    @Test
    void preHandle_nonBearer_writesUnauthorized() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn("Basic abc");

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertFalse(proceed);
        verify(resp).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertJsonCode(OpenApiErrorCode.UNAUTHORIZED.code, responseBuffer.toString());
    }

    @Test
    void preHandle_emptyToken_writesUnauthorized() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn("Bearer   ");

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertFalse(proceed);
        verify(resp).setStatus(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void preHandle_invalidToken_writesInvalidToken() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn("Bearer stale-tok");
        when(openAppService.resolveToken("stale-tok"))
                .thenThrow(new OpenApiException(OpenApiErrorCode.INVALID_TOKEN));

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertFalse(proceed);
        verify(resp).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertJsonCode(OpenApiErrorCode.INVALID_TOKEN.code, responseBuffer.toString());
        assertNull(OpenApiContext.getOpenApp(), "解析失败不应写入 Context");
    }

    // ===== 鉴权成功 + Context =====

    @Test
    void preHandle_validToken_setsContext_andReturnsTrue() throws Exception {
        OpenApp app = TestOpenApp.enabled("ak_x");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn("Bearer valid-tok");
        when(openAppService.resolveToken("valid-tok")).thenReturn(app);

        boolean proceed = interceptor.preHandle(req, resp, new Object());

        assertTrue(proceed);
        assertSame(app, OpenApiContext.getOpenApp(), "成功应把 OpenApp 写入 Context");
        verify(resp, never()).setStatus(anyInt());
    }

    @Test
    void afterCompletion_clearsContext() {
        OpenApiContext.setOpenApp(TestOpenApp.enabled("ak_x"));
        assertNotNull(OpenApiContext.getOpenApp());

        interceptor.afterCompletion(mock(HttpServletRequest.class), mock(HttpServletResponse.class),
                new Object(), null);

        assertNull(OpenApiContext.getOpenApp(), "afterCompletion 必须清理 ThreadLocal");
    }

    // ===== 限流 =====

    @Test
    void preHandle_rateLimitExceeded_writesRateLimit_andReturnsFalse() throws Exception {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setRateLimitPerMinute(1); // 上限 1

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn("Bearer valid-tok");
        when(openAppService.resolveToken("valid-tok")).thenReturn(app);

        // 第一次：放行
        assertTrue(interceptor.preHandle(req, resp, new Object()));
        OpenApiContext.clear(); // 模拟请求结束

        // 第二次：超限
        boolean proceed = interceptor.preHandle(req, resp, new Object());
        assertFalse(proceed);
        verify(resp).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        assertJsonCode(OpenApiErrorCode.RATE_LIMIT.code, responseBuffer.toString());
    }

    @Test
    void preHandle_rateLimitZero_alwaysAllow() throws Exception {
        OpenApp app = TestOpenApp.enabled("ak_x");
        app.setRateLimitPerMinute(0); // 不限制

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mockResp();
        when(req.getHeader("Authorization")).thenReturn("Bearer valid-tok");
        when(openAppService.resolveToken("valid-tok")).thenReturn(app);

        for (int i = 0; i < 100; i++) {
            assertTrue(interceptor.preHandle(req, resp, new Object()),
                    "limit=0 应恒放行，第 " + i + " 次被拒");
            OpenApiContext.clear();
        }
    }

    // ===== 工具 =====

    /** 构造一个 mock 响应，writer 落入 responseBuffer，便于断言 JSON 信封。
     *  用 lenient() 包裹：成功路径不写响应体，strict 模式会误报 UnnecessaryStubbing。 */
    private HttpServletResponse mockResp() throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        PrintWriter writer = new PrintWriter(responseBuffer);
        org.mockito.Mockito.lenient().when(resp.getWriter()).thenReturn(writer);
        return resp;
    }

    private static void assertJsonCode(String expectedCode, String json) {
        assertNotNull(json, "响应体不应为空");
        assertTrue(json.contains("\"code\":\"" + expectedCode + "\""),
                "响应 JSON 应含 code=" + expectedCode + "，实际：" + json);
    }

    /** Mockito anyInt 在 strict 模式下需要显式声明以避免 UnnecessaryStubbing */
    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
