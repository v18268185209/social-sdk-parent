//package cn.net.rjnetwork.starter.platform.xianyu.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import cn.net.rjnetwork.starter.platform.xianyu.config.XianyuConsoleAutoConfiguration;
//import cn.net.rjnetwork.starter.platform.xianyu.config.XianyuConsoleProperties;
//import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountCookieLoginRequest;
//import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountStatusUpdateRequest;
//import cn.net.rjnetwork.starter.platform.xianyu.dto.KeywordRuleUpsertRequest;
//import cn.net.rjnetwork.starter.platform.xianyu.dto.MessageSendRequest;
//import cn.net.rjnetwork.starter.platform.xianyu.dto.RuleMatchRequest;
//import cn.net.rjnetwork.starter.platform.xianyu.model.XianyuAccountEntity;
//import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuAccountRepository;
//import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuKeywordRuleRepository;
//import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuLoginSnapshotRepository;
//import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuProductRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.sqlite.SQLiteDataSource;
//
//import java.nio.file.Path;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.junit.jupiter.api.Assertions.fail;
//
//class XianyuConsoleServiceTest {
//
//    @TempDir
//    Path tempDir;
//
//    private XianyuConsoleService service;
//
//    @BeforeEach
//    void setUp() {
//        Path sqlitePath = tempDir.resolve("xianyu-console-test.db");
//
//        SQLiteDataSource dataSource = new SQLiteDataSource();
//        dataSource.setUrl("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
//        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
//
//        new XianyuConsoleAutoConfiguration.XianyuConsoleSchemaInitializer(jdbcTemplate, true);
//
//        XianyuConsoleProperties properties = new XianyuConsoleProperties();
//        properties.setEnabled(true);
//        properties.setSqlitePath(sqlitePath.toString());
//
//        service = new XianyuConsoleService(
//                properties,
//                new ObjectMapper(),
//                new XianyuAccountRepository(jdbcTemplate),
//                new XianyuProductRepository(jdbcTemplate),
//                new XianyuKeywordRuleRepository(jdbcTemplate),
//                new XianyuLoginSnapshotRepository(jdbcTemplate),
//                null);
//    }
//
//    @Test
//    void shouldMatchRulesByPriority() {
//        KeywordRuleUpsertRequest highPriority = new KeywordRuleUpsertRequest();
//        highPriority.setRuleName("包邮优先");
//        highPriority.setKeyword("包邮");
//        highPriority.setMatchType("CONTAINS");
//        highPriority.setReplyText("可以包邮");
//        highPriority.setPriority(10);
//        service.createRule(highPriority);
//
//        KeywordRuleUpsertRequest lowPriority = new KeywordRuleUpsertRequest();
//        lowPriority.setRuleName("问候语");
//        lowPriority.setKeyword("你好");
//        lowPriority.setMatchType("CONTAINS");
//        lowPriority.setReplyText("你好，在的");
//        lowPriority.setPriority(100);
//        service.createRule(lowPriority);
//
//        RuleMatchRequest request = new RuleMatchRequest();
//        request.setText("你好，请问支持包邮吗？");
//
//        Map<String, Object> data = service.matchRule(request);
//        assertTrue(Boolean.TRUE.equals(data.get("matched")));
//        assertEquals("可以包邮", data.get("suggestedReply"));
//        assertTrue(data.get("matches") instanceof List<?>);
//        assertEquals(2, ((List<?>) data.get("matches")).size());
//    }
//
//    @Test
//    void shouldExtractVerificationHintFromError() {
//        Map<String, Object> hint = service.extractVerificationHint(
//                "Realtime token API failed: {\"ret\":[\"FAIL_SYS_USER_VALIDATE\"],\"data\":{\"url\":\"https://h5api.m.goofish.com/h5/xx/punish?action=captcha\"}}");
//
//        assertTrue(Boolean.TRUE.equals(hint.get("risk")));
//        assertTrue(hint.get("verificationUrl").toString().contains("punish"));
//    }
//
//    @Test
//    void shouldRejectEmptyMatchText() {
//        RuleMatchRequest request = new RuleMatchRequest();
//        request.setText(" ");
//
//        try {
//            service.matchRule(request);
//            fail("expected IllegalArgumentException");
//        } catch (IllegalArgumentException ex) {
//            assertEquals("text is required", ex.getMessage());
//        }
//    }
//
//    @Test
//    void shouldRecordLoginFailureAsPendingVerifyAndUpsertByAccountName() {
//        AccountCookieLoginRequest request = new AccountCookieLoginRequest();
//        request.setAccountName("店铺A");
//        request.setCookieHeader("_m_h5_tk=abc; unb=10001; tracknick=%E5%BA%97%E9%93%BAA");
//
//        Map<String, Object> verificationHint = service.extractVerificationHint(
//                "FAIL_SYS_USER_VALIDATE https://h5api.m.goofish.com/h5/x/punish?action=captcha");
//        Optional<XianyuAccountEntity> first = service.recordLoginFailure(request, "need captcha", verificationHint);
//        Optional<XianyuAccountEntity> second = service.recordLoginFailure(request, "still blocked", verificationHint);
//
//        assertTrue(first.isPresent());
//        assertTrue(second.isPresent());
//        assertEquals(first.get().getId(), second.get().getId());
//        assertEquals(XianyuConsoleService.ACCOUNT_STATUS_PENDING_VERIFY, second.get().getStatus());
//        assertEquals("still blocked", second.get().getLastError());
//    }
//
//    @Test
//    void shouldUpdateAccountStatusToActive() {
//        AccountCookieLoginRequest request = new AccountCookieLoginRequest();
//        request.setAccountName("店铺B");
//        request.setCookieHeader("_m_h5_tk=abc; unb=20002");
//        Map<String, Object> failureHint = new LinkedHashMap<>();
//        failureHint.put("risk", false);
//        failureHint.put("verificationUrl", null);
//        Optional<XianyuAccountEntity> created = service.recordLoginFailure(
//                request,
//                "token expired",
//                failureHint);
//        assertTrue(created.isPresent());
//        assertEquals(XianyuConsoleService.ACCOUNT_STATUS_FAILED, created.get().getStatus());
//
//        AccountStatusUpdateRequest statusUpdate = new AccountStatusUpdateRequest();
//        statusUpdate.setStatus("ACTIVE");
//        statusUpdate.setLastError("manual cleared");
//        XianyuAccountEntity updated = service.updateAccountStatus(created.get().getId(), statusUpdate);
//
//        assertEquals(XianyuConsoleService.ACCOUNT_STATUS_ACTIVE, updated.getStatus());
//        assertNull(updated.getLastError());
//        assertNotNull(updated.getLastLoginAt());
//    }
//
//    @Test
//    void shouldRejectMessageSendWithoutPayload() {
//        MessageSendRequest request = new MessageSendRequest();
//        request.setAccountId(1L);
//        request.setToUserId("buyer");
//
//        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.sendMessage(request));
//
//        assertEquals("text or imageUrl/imagePath is required", ex.getMessage());
//    }
//
//    @Test
//    void shouldRejectMessageSendWithoutReceiver() {
//        MessageSendRequest request = new MessageSendRequest();
//        request.setAccountId(1L);
//        request.setText("hello");
//
//        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.sendMessage(request));
//
//        assertEquals("toUserId is required", ex.getMessage());
//    }
//
//    @Test
//    void shouldReturnNotRunningChatTakeoverStatus() {
//        Map<String, Object> stopped = service.stopChatTakeover(99L);
//        Map<String, Object> status = service.getChatTakeoverStatus(99L);
//
//        assertEquals(99L, stopped.get("accountId"));
//        assertFalse(Boolean.TRUE.equals(stopped.get("running")));
//        assertEquals("chat takeover not found", stopped.get("message"));
//        assertFalse(Boolean.TRUE.equals(status.get("running")));
//        assertEquals(List.of(), service.listChatEvents(99L));
//    }
//}
