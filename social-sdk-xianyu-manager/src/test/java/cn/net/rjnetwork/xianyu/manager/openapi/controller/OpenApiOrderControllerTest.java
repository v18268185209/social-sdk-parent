package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
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
 * OpenApiOrderController 单测：列表过滤 / 详情 / 404 / 403。
 * 顺手验脱敏：toVo 不应设置 deliverContent（VO 无此字段）。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiOrderControllerTest {

    @Mock private OrderMapper orderMapper;
    @Mock private OpenAppService openAppService;

    @InjectMocks private OpenApiOrderController controller;

    @AfterEach
    void tearDown() {
        OpenApiContext.clear();
    }

    @Test
    void list_emptyBound_returnsAll() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(orderMapper.selectList(any())).thenReturn(List.of(order(1L, 10L), order(2L, 20L)));

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
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order(1L, 10L), order(2L, 20L)));

        var resp = controller.list(null);

        assertEquals(1, resp.getData().size());
        assertEquals(10L, resp.getData().get(0).getAccountId());
    }

    @Test
    void get_notFound_throws404() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(orderMapper.selectById(999L)).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(999L));
        assertEquals(OpenApiErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void get_boundCheckRuns_afterLookup_beforeResponse() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuOrder o = order(2L, 30L);
        when(orderMapper.selectById(2L)).thenReturn(o);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(30L));

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(2L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
    }

    @Test
    void get_existing_inBound_returnsDetail() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuOrder o = order(2L, 10L);
        when(orderMapper.selectById(2L)).thenReturn(o);

        var resp = controller.get(2L);

        assertEquals("OK", resp.getCode());
        assertEquals(2L, resp.getData().getId());
        assertEquals(10L, resp.getData().getAccountId());
    }

    private static XianyuOrder order(long id, long accountId) {
        XianyuOrder o = new XianyuOrder();
        o.setId(id);
        o.setAccountId(accountId);
        return o;
    }

    private static <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
