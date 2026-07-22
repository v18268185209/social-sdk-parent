package cn.net.rjnetwork.xianyu.manager.openapi.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenApiResponse 信封契约：code/message/data/timestamp 四字段。
 * 成功恒为 OK + Success；失败 code 由 OpenApiErrorCode 决定，data 不被 setter 自动置空。
 */
class OpenApiResponseTest {

    @Test
    void ok_setsCodeMessageAndData() {
        OpenApiResponse<List<String>> r = OpenApiResponse.ok(List.of("a", "b"));

        assertEquals("OK", r.getCode());
        assertEquals("Success", r.getMessage());
        assertNotNull(r.getData());
        assertEquals(2, r.getData().size());
        assertTrue(r.getTimestamp() > 0, "timestamp 应被默认初始化为 Unix 秒");
    }

    @Test
    void ok_withNullData_stillSucceeds() {
        OpenApiResponse<Object> r = OpenApiResponse.ok(null);

        assertEquals("OK", r.getCode());
        assertNull(r.getData(), "ok 接受 null data，调用方自行处理");
    }

    @Test
    void fail_setsCodeAndMessage_leavesDataNull() {
        OpenApiResponse<Void> r = OpenApiResponse.fail(OpenApiErrorCode.INVALID_TOKEN.code, "令牌已过期");

        assertEquals(OpenApiErrorCode.INVALID_TOKEN.code, r.getCode());
        assertEquals("令牌已过期", r.getMessage());
        assertNull(r.getData(), "失败响应不应携带业务数据");
        assertTrue(r.getTimestamp() > 0);
    }

    @Test
    void timestamp_defaultsToEpochSecond() {
        long before = Instant.now().getEpochSecond();

        OpenApiResponse<String> r = OpenApiResponse.ok("x");

        long after = Instant.now().getEpochSecond();
        assertTrue(r.getTimestamp() >= before && r.getTimestamp() <= after,
                "timestamp 默认应为构造时的 Unix 秒");
    }

    @Test
    void setters_areMutable_forEdgeCases() {
        OpenApiResponse<String> r = new OpenApiResponse<>();
        r.setCode("OPEN_INTERNAL");
        r.setMessage("boom");
        r.setData("payload");
        r.setTimestamp(0L);

        assertEquals("OPEN_INTERNAL", r.getCode());
        assertEquals("boom", r.getMessage());
        assertEquals("payload", r.getData());
        assertEquals(0L, r.getTimestamp());
    }
}
