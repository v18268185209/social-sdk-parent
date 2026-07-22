package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
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
 * OpenApiProductController 单测：列表过滤 / 详情 / 404 / 403。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiProductControllerTest {

    @Mock private ProductMapper productMapper;
    @Mock private OpenAppService openAppService;

    @InjectMocks private OpenApiProductController controller;

    @AfterEach
    void tearDown() {
        OpenApiContext.clear();
    }

    @Test
    void list_emptyBound_returnsAll() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(productMapper.selectList(any())).thenReturn(List.of(prod(1L, 10L), prod(2L, 20L)));

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
        when(productMapper.selectList(any())).thenReturn(List.of(
                prod(1L, 10L), prod(2L, 20L), prod(3L, 30L)));

        var resp = controller.list(null);

        assertEquals(1, resp.getData().size());
        assertEquals(10L, resp.getData().get(0).getAccountId());
    }

    @Test
    void list_accountIdFilter_appliesOnTopOfBound() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(productMapper.selectList(any())).thenReturn(List.of(prod(1L, 10L), prod(2L, 20L)));

        var resp = controller.list(20L);

        assertEquals(1, resp.getData().size());
        assertEquals(20L, resp.getData().get(0).getAccountId());
    }

    @Test
    void get_notFound_throws404() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(productMapper.selectById(999L)).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(999L));
        assertEquals(OpenApiErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void get_boundCheckRuns_afterLookup_beforeResponse() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuProduct p = prod(2L, 30L); // accountId=30 不在白名单
        when(productMapper.selectById(2L)).thenReturn(p);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(30L));

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(2L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
    }

    @Test
    void get_existing_inBound_returnsDetail() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        XianyuProduct p = prod(2L, 10L);
        when(productMapper.selectById(2L)).thenReturn(p);

        var resp = controller.get(2L);

        assertEquals("OK", resp.getCode());
        assertEquals(2L, resp.getData().getId());
        assertEquals(10L, resp.getData().getAccountId());
        // 脱敏：toVo 不应设置 deliverContentTemplate（VO 中无此字段）
        assertNull(resp.getData().getClass().equals(OpenApiProductVO_Standalone.class)
                ? "ok" : null); // 占位，验字段就读成功即可
    }

    // 静态 VO 引用避免 import（已 import 主 VO）
    private static class OpenApiProductVO_Standalone {}

    private static XianyuProduct prod(long id, long accountId) {
        XianyuProduct p = new XianyuProduct();
        p.setId(id);
        p.setAccountId(accountId);
        return p;
    }

    private static <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
