^package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 账号登录请求
 */
@Data
public class AccountLoginRequest {

    private String accountName;

    private String cookieHeader;

    private String remark;
}
