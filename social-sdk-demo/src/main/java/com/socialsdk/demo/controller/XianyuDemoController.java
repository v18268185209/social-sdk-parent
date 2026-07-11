package com.socialsdk.demo.controller;

import com.socialsdk.demo.model.ApiResponse;
import com.socialsdk.demo.model.CookieLoginRequest;
import com.socialsdk.demo.model.QrAuthenticateRequest;
import com.socialsdk.demo.model.RealtimeControlRequest;
import com.socialsdk.demo.model.SendMessageRequest;
import com.socialsdk.demo.service.XianyuDemoService;
import com.socialsdk.xianyu.model.XianyuQrLoginSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/xianyu")
public class XianyuDemoController {

    private final XianyuDemoService demoService;

    public XianyuDemoController(XianyuDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "social-sdk-demo");
        data.put("platform", "xianyu");
        data.put("status", "up");
        return ApiResponse.ok(data);
    }

    @PostMapping("/login/cookies")
    public ApiResponse<?> loginWithCookies(@RequestBody(required = false) CookieLoginRequest request) {
        try {
            return ApiResponse.ok(demoService.loginWithCookies(request));
        } catch (Exception e) {
            return ApiResponse.fail("cookies login failed: " + e.getMessage());
        }
    }

    @PostMapping("/login/qr/create")
    public ApiResponse<?> createQrSession() {
        try {
            XianyuQrLoginSession session = demoService.createQrLoginSession();
            return ApiResponse.ok(session);
        } catch (Exception e) {
            return ApiResponse.fail("create qr session failed: " + e.getMessage());
        }
    }

    @GetMapping("/login/qr/status/{qrSessionId}")
    public ApiResponse<?> getQrStatus(@PathVariable("qrSessionId") String qrSessionId) {
        try {
            return ApiResponse.ok(demoService.getQrLoginSessionStatus(qrSessionId));
        } catch (Exception e) {
            return ApiResponse.fail("query qr session status failed: " + e.getMessage());
        }
    }

    @PostMapping("/login/qr/authenticate")
    public ApiResponse<?> authenticateByQr(@RequestBody QrAuthenticateRequest request) {
        try {
            return ApiResponse.ok(demoService.authenticateByQr(request));
        } catch (Exception e) {
            return ApiResponse.fail("qr authenticate failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/login/qr/{qrSessionId}")
    public ApiResponse<?> invalidateQr(@PathVariable("qrSessionId") String qrSessionId) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("qrSessionId", qrSessionId);
            data.put("invalidated", demoService.invalidateQrLoginSession(qrSessionId));
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("invalidate qr session failed: " + e.getMessage());
        }
    }

    @PostMapping("/message/send")
    public ApiResponse<?> sendMessage(@RequestBody SendMessageRequest request) {
        try {
            return ApiResponse.ok(demoService.sendMessage(request));
        } catch (Exception e) {
            return ApiResponse.fail("send message failed: " + e.getMessage());
        }
    }

    @GetMapping("/timeline/{demoSessionId}")
    public ApiResponse<?> timeline(@PathVariable("demoSessionId") String demoSessionId,
                                   @RequestParam(name = "limit", defaultValue = "20") int limit) {
        try {
            return ApiResponse.ok(demoService.getTimeline(demoSessionId, limit));
        } catch (Exception e) {
            return ApiResponse.fail("get timeline failed: " + e.getMessage());
        }
    }

    @PostMapping("/realtime/start")
    public ApiResponse<?> startRealtime(@RequestBody RealtimeControlRequest request) {
        try {
            return ApiResponse.ok(demoService.startRealtime(request.getDemoSessionId()));
        } catch (Exception e) {
            return ApiResponse.fail("start realtime failed: " + e.getMessage());
        }
    }

    @PostMapping("/realtime/stop")
    public ApiResponse<?> stopRealtime(@RequestBody RealtimeControlRequest request) {
        try {
            return ApiResponse.ok(demoService.stopRealtime(request.getDemoSessionId()));
        } catch (Exception e) {
            return ApiResponse.fail("stop realtime failed: " + e.getMessage());
        }
    }

    @GetMapping("/realtime/messages/{demoSessionId}")
    public ApiResponse<?> listRealtimeMessages(@PathVariable("demoSessionId") String demoSessionId) {
        try {
            return ApiResponse.ok(demoService.listRealtimeMessages(demoSessionId));
        } catch (Exception e) {
            return ApiResponse.fail("list realtime messages failed: " + e.getMessage());
        }
    }

    @GetMapping(value = "/realtime/stream/{demoSessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRealtimeMessages(@PathVariable("demoSessionId") String demoSessionId) {
        try {
            return demoService.openRealtimeStream(demoSessionId);
        } catch (Exception e) {
            SseEmitter emitter = new SseEmitter(1L);
            try {
                Map<String, Object> errorPayload = new LinkedHashMap<>();
                errorPayload.put("type", "error");
                errorPayload.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errorPayload));
                emitter.complete();
            } catch (IOException ioException) {
                emitter.completeWithError(ioException);
            }
            return emitter;
        }
    }

    @GetMapping("/session/{demoSessionId}")
    public ApiResponse<?> getSession(@PathVariable("demoSessionId") String demoSessionId) {
        try {
            return ApiResponse.ok(demoService.getSession(demoSessionId));
        } catch (Exception e) {
            return ApiResponse.fail("get session failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/session/{demoSessionId}")
    public ApiResponse<?> logout(@PathVariable("demoSessionId") String demoSessionId) {
        try {
            return ApiResponse.ok(demoService.logout(demoSessionId));
        } catch (Exception e) {
            return ApiResponse.fail("logout failed: " + e.getMessage());
        }
    }
}
