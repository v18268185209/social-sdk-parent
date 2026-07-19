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
}
