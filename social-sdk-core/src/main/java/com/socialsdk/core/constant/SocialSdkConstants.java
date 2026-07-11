package com.socialsdk.core.constant;

/**
 * SDK常量定义
 */
public final class SocialSdkConstants {

    private SocialSdkConstants() {
        // Prevent instantiation
    }

    // Configuration Keys
    public static final String CONFIG_PREFIX = "social-sdk";
    public static final String CONFIG_ENABLED_KEY = CONFIG_PREFIX + ".enabled";
    public static final String CONFIG_DEFAULT_PLATFORM_KEY = CONFIG_PREFIX + ".default-platform";
    public static final String CONFIG_TIMEOUT_KEY = CONFIG_PREFIX + ".timeout";
    public static final String CONFIG_PROXY_KEY = CONFIG_PREFIX + ".proxy";

    // Session Keys
    public static final String SESSION_USER_ID = "social:user:id";
    public static final String SESSION_USER_TOKEN = "social:user:token";
    public static final String SESSION_EXPIRY_TIME = "social:user:expiry";

    // HTTP Headers
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_COOKIE = "Cookie";

    // Content Types
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded;charset=UTF-8";
    public static final String CONTENT_TYPE_MULTIPART = "multipart/form-data";

    // Default Values
    public static final int DEFAULT_TIMEOUT = 30000;
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    public static final int DEFAULT_READ_TIMEOUT = 30000;

    // File Names
    public static final String CONFIG_FILE_NAME = "social-sdk.yaml";
    public static final String CONFIG_FILE_NAME_ALT = "social-sdk.yml";
}
