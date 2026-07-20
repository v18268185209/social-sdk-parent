package cn.net.rjnetwork.xianyu.manager.message.controller;

import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.message.dto.MessageSendRequest;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import cn.net.rjnetwork.xianyu.manager.message.service.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(@RequestParam Long accountId) {
        return ApiResponse.ok(messageService.listSessionSummaries(accountId));
    }

    @GetMapping("/history")
    public ApiResponse<List<XianyuMessage>> history(
            @RequestParam Long accountId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        messageService.pullHistoryIfEmpty(accountId, sessionId, limit);
        return ApiResponse.ok(messageService.getHistory(accountId, sessionId, limit));
    }

    @PostMapping("/send")
    @Audit("发送消息")
    public ApiResponse<XianyuMessage> send(@RequestBody MessageSendRequest request) {
        try {
            XianyuMessage sent = messageService.sendMessage(request);
            return ApiResponse.ok(sent);
        } catch (IllegalStateException ie) {
            // 风控拦截 / cookie 失效 / userId 缺失 等业务异常：带友好摘要回前端，不出 500
            // MessageService.sendMessage 已在摘要里包含 punish URL 关键信息
            String msg = ie.getMessage() != null ? ie.getMessage() : "发送失败";
            String code = msg.contains("风控") || msg.contains("punish") || msg.contains("FAIL_SYS_USER_VALIDATE")
                    || msg.contains("RGV587") || msg.contains("滑块") ? "RISK_CONTROL" : "SEND_FAILED";
            return ApiResponse.fail(code, msg);
        } catch (Exception e) {
            return ApiResponse.fail("SEND_FAILED", "发送失败: " + e.getMessage());
        }
    }

    @PostMapping("/syncNow")
    public ApiResponse<String> syncNow() {
        messageService.syncAllAccounts();
        return ApiResponse.ok("OK");
    }

    /**
     * 单账号手动同步消息 — 真调闲鱼按 accountId 拉最新会话推入本地
     * <p>前端消息页面的「手动同步」按钮调这个，比 syncNow（全账号）更轻量。
     * 同步完成后前端按 accountId 分组渲染会话列表。</p>
     *
     * @param accountId 账号 id
     */
    @PostMapping("/sync")
    public ApiResponse<String> syncByAccount(@RequestParam Long accountId) {
        try {
            // 复用 MessageService 已有的单账号拉取逻辑
            // syncAllAccounts 内部循环所有账号，这里只调单账号那一段
            messageService.syncSingleAccount(accountId);
            return ApiResponse.ok("OK");
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("INVALID_ARGUMENT", e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail("COOKIE_EXPIRED", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("SYNC_FAILED", "Sync failed: " + e.getMessage());
        }
    }

    /**
     * 按账号分组拉本地已同步的消息列表 — 前端消息页面渲染用
     * <p>不再只按 sessionId 拉单会话，而是按 accountId 拉该账号所有消息，
     * 前端按 sessionId 二次分组渲染会话卡片，每个账号一个 tab。</p>
     *
     * @param accountId 账号 id
     * @param limit     每账号最多返回多少条（默认 100）
     */
    @GetMapping("/list")
    public ApiResponse<List<XianyuMessage>> listByAccount(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(messageService.listByAccount(accountId, limit));
    }
}
