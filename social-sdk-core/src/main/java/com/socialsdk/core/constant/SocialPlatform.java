package com.socialsdk.core.constant;

/**
 * 社交平台枚举
 */
public enum SocialPlatform {
    /**
     * Chrome浏览器平台
     */
    CHROME("chrome", "Chrome Browser"),

    /**
     * Firefox浏览器平台
     */
    FIREFOX("firefox", "Firefox Browser"),

    /**
     * 微信
     */
    WECHAT("wechat", "WeChat"),

    /**
     * 闲鱼
     */
    XIANYU("xianyu", "Xianyu / Goofish"),

    /**
     * 微博
     */
    WEIBO("weibo", "Sina Weibo"),

    /**
     * Twitter
     */
    TWITTER("twitter", "Twitter/X"),

    /**
     * Facebook
     */
    FACEBOOK("facebook", "Facebook"),

    /**
     * Instagram
     */
    INSTAGRAM("instagram", "Instagram"),

    /**
     * LinkedIn
     */
    LINKEDIN("linkedin", "LinkedIn"),

    /**
     * GitHub
     */
    GITHUB("github", "GitHub");

    private final String code;
    private final String description;

    SocialPlatform(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取平台枚举
     */
    public static SocialPlatform fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SocialPlatform platform : values()) {
            if (platform.code.equalsIgnoreCase(code)) {
                return platform;
            }
        }
        return null;
    }
}
