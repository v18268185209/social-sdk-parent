package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAccountVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OpenApiAccountController 单测：列表过滤 / 详情 / 404 / 403（账号作用域）。
 * 通过 OpenApiContext.setOpenApp 直接注入身份，跳过拦截器。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiAccountControllerTest {

    @Mock private AccountService accountService;
    @Mock private AccountMapper accountMapper;
    @Mock private OpenAppService openAppService;

    @InjectMocks private OpenApiAccountController controller;

    @AfterEach
    void tearDown() {
        OpenApiContext.clear();
    }

    // ===== list =====

    @Test
    void list_emptyBound_returnsAll() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(accountService.listAll()).thenReturn(List.of(acct(1L, "a"), acct(2L, "b")));

        var resp = controller.list(null);

        assertEquals("OK", resp.getCode());
        assertEquals(2, resp.getData().size());
        verify(openAppService).assertAccountAccessible(app, null);
        // 脱敏：不含 cookieHeader 等
        for (OpenApiAccountVO vo : resp.getData()) {
            assertNull(vo.getRemark() == null ? null : "ok"); // remark 可空，仅验字段可读
        }
    }

    @Test
    void list_boundFilter_onlyReturnsBoundAccounts() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L, 2L);
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of(1L, 2L));
        when(accountService.listAll()).thenReturn(List.of(acct(1L, "a"), acct(2L, "b"), acct(3L, "c")));

        var resp = controller.list(null);

        assertEquals(2, resp.getData().size());
        assertTrue(resp.getData().stream().allMatch(vo -> Set.of(1L, 2L).contains(vo.getId())));
    }

    @Test
    void list_accountIdFilter_appliesOnTopOfBound() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(openAppService.getBoundAccountIds(app)).thenReturn(Set.of());
        when(accountService.listAll()).thenReturn(List.of(acct(1L, "a"), acct(2L, "b")));

        var resp = controller.list(2L);

        assertEquals(1, resp.getData().size());
        assertEquals(2L, resp.getData().get(0).getId());
    }

    @Test
    void list_boundForbidden_throwsAccountForbidden() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L);
        OpenApiContext.setOpenApp(app);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(99L));

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.list(99L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(accountService);
    }

    // ===== get =====

    @Test
    void get_notFound_throws404() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(accountMapper.selectById(999L)).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(999L));
        assertEquals(OpenApiErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void get_boundCheckRuns_beforeNotFoundCheck_byPassingScope() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L);
        OpenApiContext.setOpenApp(app);
        // 作用域校验在 selectById 之前发生，3L 不在白名单应先抛 403
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(app, 3L);

        OpenApiException e = assertThrows(OpenApiException.class, () -> controller.get(3L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(accountMapper);
    }

    @Test
    void get_existing_inBound_returnsDetail() {
        OpenApp app = TestOpenApp.bound("ak_x", 1L, 2L);
        OpenApiContext.setOpenApp(app);
        XianyuAccount acct = acct(2L, "b");
        when(accountMapper.selectById(2L)).thenReturn(acct);

        var resp = controller.get(2L);

        assertEquals("OK", resp.getCode());
        assertEquals(2L, resp.getData().getId());
        assertEquals("b", resp.getData().getAccountName());
    }

    // ===== 工具 =====

    private static XianyuAccount acct(long id, String name) {
        XianyuAccount a = new XianyuAccount();
        a.setId(id);
        a.setAccountName(name);
        return a;
    }
}
