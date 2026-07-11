package com.socialsdk.starter.platform.xianyu.repository;

import java.time.Instant;

final class SqliteTime {

    private SqliteTime() {
    }

    static String nowText() {
        return Instant.now().toString();
    }

    static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
