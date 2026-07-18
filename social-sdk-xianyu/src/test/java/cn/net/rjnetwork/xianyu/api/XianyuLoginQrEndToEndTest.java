package cn.net.rjnetwork.xianyu.api;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService.QrLoginResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二维码登录端到端集成测试。
 *
 * <p>该测试会真实调用 passport.goofish.com 的接口，需要外网连通。</p>
 *
 * <p>验证目标：</p>
 * <ol>
 *   <li>能成功生成二维码（拿到 qrCodeDataUrl + t + ck）</li>
 *   <li>轮询接口能正常返回，不再因为缺少风控参数而被服务端直接判定为 EXPIRED</li>
 * </ol>
 */
class XianyuLoginQrEndToEndTest {

    @Test
    void generateQrCode_andPollOnce_shouldNotReturnExpiredImmediately() {
        XianyuLoginApiService service = new XianyuLoginApiService("");
        QrLoginResult result = service.createQrLoginSession();

        System.out.println("[E2E] createQrLoginSession.success=" + result.success
                + ", status=" + result.status
                + ", message=" + result.message
                + ", hasQrImage=" + (result.qrCodeDataUrl != null)
                + ", sessionId=" + result.sessionId);

        // 刚生成的会话状态是 WAITING，success 字段按 SUCCESS 判定，所以不能直接断言 success=true
        // 真正的"生成成功"信号是 sessionId 和 qrCodeDataUrl 都不为空
        assertNotNull(result.sessionId, "应返回 sessionId");
        assertNotNull(result.qrCodeDataUrl, "应返回 base64 二维码图片");
        assertEquals("WAITING", result.status,
                "刚生成的二维码状态应为 WAITING，实际: " + result.status);

        // 第一次轮询：刚生成的二维码还没扫码，应该返回 NEW/WAITING，而不是 EXPIRED
        QrLoginResult poll = service.pollQrStatus(result.sessionId);
        System.out.println("[E2E] first poll.status=" + poll.status + ", message=" + poll.message);

        // 关键断言：修复前会立即返回 EXPIRED，修复后应返回 WAITING（NEW）
        assertEquals("WAITING", poll.status,
                "刚生成的二维码首次轮询应返回 WAITING，实际: " + poll.status
                        + "（如果返回 EXPIRED 说明风控参数仍有问题）");

        // 再轮询一次，确保状态稳定
        QrLoginResult poll2 = service.pollQrStatus(result.sessionId);
        System.out.println("[E2E] second poll.status=" + poll2.status + ", message=" + poll2.message);
        assertTrue("WAITING".equals(poll2.status) || "SCANNED".equals(poll2.status),
                "第二次轮询应保持 WAITING 或变 SCANNED，实际: " + poll2.status);
    }
}
