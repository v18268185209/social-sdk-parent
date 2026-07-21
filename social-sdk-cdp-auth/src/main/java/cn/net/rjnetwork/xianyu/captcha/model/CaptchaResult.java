package cn.net.rjnetwork.xianyu.captcha.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 滑块验证结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResult {

    /** 是否成功 */
    private boolean success;

    /** 验证状态消息 */
    private String message;

    /** 新 cookie（如果验证成功） */
    private String newCookie;

    /** 错误信息（如果失败） */
    private String error;

    /**
     * 登录态失效标记。
     * <p>true 表示已注入账号登录 cookie 但 IM 页仍跳到登录页，
     * 上层应推送「网页端重新登录」通知，不要再尝试滑块。</p>
     */
    private boolean loginExpired;

    public static CaptchaResult ok(String cookie) {
        return CaptchaResult.builder()
                .success(true)
                .message("验证码已通过")
                .newCookie(cookie)
                .build();
    }

    public static CaptchaResult fail(String error) {
        return CaptchaResult.builder()
                .success(false)
                .error(error)
                .build();
    }

    /** 登录态失效：已注入登录 cookie 但 IM 页仍跳登录页，需人工重新登录。 */
    public static CaptchaResult loginExpired() {
        return CaptchaResult.builder()
                .success(false)
                .loginExpired(true)
                .error("LOGIN_EXPIRED: 账号登录 Cookie 已失效，请在网页端重新登录")
                .build();
    }
}
