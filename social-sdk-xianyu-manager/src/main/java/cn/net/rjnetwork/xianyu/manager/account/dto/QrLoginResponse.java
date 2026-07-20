package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 二维码登录结果响应
 */
@Data
public class QrLoginResponse {

    private boolean success;

    private String sessionId;

    /** WAITING / SCANNED / SUCCESS / EXPIRED / CANCELLED / ERROR / VERIFICATION_REQUIRED */
    private String status;

    /** base64 PNG 二维码图片 */
    private String qrCodeDataUrl;

    /** 登录成功后 Cookie */
    private String cookieHeader;

    /** 登录成功后 unb */
    private String unb;

    /** 需要验证时的跳转 URL */
    private String verificationUrl;

    private String message;

    /** 账号信息（登录成功时返回） */
    private AccountInfo account;

    @Data
    public static class AccountInfo {
        private Long id;
        private String accountName;
        private String userId;
        private String displayName;
        private String status;
    }
}
