package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 二维码登录请求
 */
@Data
public class QrLoginRequest {

    private String accountName;

    private String remark;

    /**
     * 重新登录场景：指定要更新 Cookie 的账号 ID。
     * 为 null 表示新建账号；非 null 表示扫码成功后更新该账号的 Cookie。
     */
    private Long accountId;
}
