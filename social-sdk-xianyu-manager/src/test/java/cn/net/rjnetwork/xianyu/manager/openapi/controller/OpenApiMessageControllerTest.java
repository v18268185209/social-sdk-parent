package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OpenApiMessageController 单测：列表过滤 / 详情 / 404 / 403。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiMessageControllerTest {

    @Mock private MessageMapper messageMapper;
    @Mock private OpenAppService openAppService;

    @InjectMocks private OpenApiMessageController controller;

    @AfterEach
    void tearDown() {
        OpenApiContext.clear();
    }

    @Test
    void list_emptyBound_returnsAll() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(messageMapper.selectList(any())).thenReturn(List.of(msg(1L, 10L), msg(2L, 20L)));

        var resp = controller.list(null);

        assertEquals("OK", resp.getCode());
        assertEquals(2, resp.getData().size());
        verify(openAppService).assertAccountAccessible(app, null);
    }

    @Test
    void list_boundFilter_onlyReturnsBoundAccounts() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of(10L));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                msg(1L, 10L), msg(2L, 20L)));

        var resp = controller.list(null);

        assertEquals(1, resp.getData().size());
        assertEquals(10L, resp.getData().get(0).getAccountId());
    }

    @Test
    void list_accountIdFilter_appliesOnTopOfBound() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(messageMapper.selectList(any())).thenReturn(List.of(msg(1L, 10L), msg(2L, 20L)));

        var resp = controller.list(20L);

        assertEquals(1, resp.getData().size());
        assertEquals(20L, resp.getData().get(0).getAccountId());
    }

    @Test
    void get_notFound_throws404() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(messageMapper.selectById(999L)).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(999L));
        assertEquals(OpenApiErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void get_boundCheckRuns_afterLookup_beforeResponse() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuMessage m = msg(2L, 30L);
        when(messageMapper.selectById(2L)).thenReturn(m);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(30L));

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(2L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
    }

    @Test
    void get_existing_inBound_returnsDetail() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuMessage m = msg(2L, 10L);
        when(messageMapper.selectById(2L)).thenReturn(m);

        var resp = controller.get(2L);

        assertEquals("OK", resp.getCode());
        assertEquals(2L, resp.getData().getId());
        assertEquals(10L, resp.getData().getAccountId());
    }

    private static XianyuMessage msg(long id, long accountId) {
        XianyuMessage m = new XianyuMessage();
        m.setId(id);
        m.setAccountId(accountId);
        return m;
    }

    private static <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
