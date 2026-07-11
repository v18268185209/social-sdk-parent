package com.socialsdk.starter.platform.common.web;

import com.socialsdk.starter.platform.common.exception.StarterApiException;
import com.socialsdk.starter.platform.common.model.StarterApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterGlobalExceptionHandlerTest {

    private final StarterGlobalExceptionHandler handler = new StarterGlobalExceptionHandler();

    @Test
    void shouldMapStarterApiExceptionWithData() {
        StarterApiException ex = new StarterApiException(
                "VERIFICATION_REQUIRED",
                "need captcha",
                Map.of("risk", true),
                null);

        StarterApiResponse<?> response = handler.handleStarterApiException(ex);

        assertFalse(response.isSuccess());
        assertEquals("VERIFICATION_REQUIRED", response.getCode());
        assertEquals("need captcha", response.getMessage());
        assertTrue(response.getData() instanceof Map<?, ?>);
    }

    @Test
    void shouldMapIllegalArgumentToBadRequest() {
        StarterApiResponse<?> response = handler.handleIllegalArgument(
                new IllegalArgumentException("invalid request"));

        assertFalse(response.isSuccess());
        assertEquals("BAD_REQUEST", response.getCode());
        assertEquals("invalid request", response.getMessage());
    }
}
