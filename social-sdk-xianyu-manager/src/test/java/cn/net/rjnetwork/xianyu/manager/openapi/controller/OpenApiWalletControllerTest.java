package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.TestOpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletTransactionMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWallet;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
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
 * OpenApiWalletController 单测：钱包列表 / 按账号详情 / 流水 / 404 / 403。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiWalletControllerTest {

    @Mock private WalletMapper walletMapper;
    @Mock private WalletTransactionMapper walletTransactionMapper;
    @Mock private OpenAppService openAppService;

    @InjectMocks private OpenApiWalletController controller;

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
        when(walletMapper.selectList(any())).thenReturn(List.of(wallet(1L, 10L), wallet(2L, 20L)));

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
        when(walletMapper.selectList(any())).thenReturn(List.of(
                wallet(1L, 10L), wallet(2L, 20L)));

        var resp = controller.list(null);

        assertEquals(1, resp.getData().size());
        assertEquals(10L, resp.getData().get(0).getAccountId());
    }

    // ===== getByAccount =====

    @Test
    void getByAccount_boundForbidden_throws403() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(30L));

        OpenApiException e = assertThrows(OpenApiException.class,
                () -> controller.getByAccount(30L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(walletMapper);
    }

    @Test
    void getByAccount_notFound_throws404() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(walletMapper.selectOne(any())).thenReturn(null);

        OpenApiException e = assertThrows(OpenApiException.class,
                () -> controller.getByAccount(30L));
        assertEquals(OpenApiErrorCode.NOT_FOUND, e.getErrorCode());
    }

    @Test
    void getByAccount_existing_returnsDetail() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        XianyuWallet w = wallet(7L, 30L);
        when(walletMapper.selectOne(any())).thenReturn(w);

        var resp = controller.getByAccount(30L);

        assertEquals("OK", resp.getCode());
        assertEquals(7L, resp.getData().getId());
        assertEquals(30L, resp.getData().getAccountId());
    }

    // ===== transactions =====

    @Test
    void transactions_boundForbidden_throws403() {
        OpenApp app = TestOpenApp.bound("ak_x", 10L);
        OpenApiContext.setOpenApp(app);
        doThrow(new OpenApiException(OpenApiErrorCode.ACCOUNT_FORBIDDEN))
                .when(openAppService).assertAccountAccessible(eq(app), eq(30L));

        OpenApiException e = assertThrows(OpenApiException.class,
                () -> controller.transactions(30L));
        assertEquals(OpenApiErrorCode.ACCOUNT_FORBIDDEN, e.getErrorCode());
        verifyNoInteractions(walletTransactionMapper);
    }

    @Test
    void transactions_returnsAllForAccount() {
        OpenApp app = TestOpenApp.enabled("ak_x");
        OpenApiContext.setOpenApp(app);
        when(walletTransactionMapper.selectList(any())).thenReturn(List.of(
                tx(101L, 30L), tx(102L, 30L)));

        var resp = controller.transactions(30L);

        assertEquals("OK", resp.getCode());
        assertEquals(2, resp.getData().size());
        assertTrue(resp.getData().stream().allMatch(vo -> vo.getAccountId() == 30L));
    }

    private static XianyuWallet wallet(long id, long accountId) {
        XianyuWallet w = new XianyuWallet();
        w.setId(id);
        w.setAccountId(accountId);
        return w;
    }

    private static XianyuWalletTransaction tx(long id, long accountId) {
        XianyuWalletTransaction t = new XianyuWalletTransaction();
        t.setId(id);
        t.setAccountId(accountId);
        return t;
    }

    private static <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
