package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 账号编辑请求（用于更换 Cookie / 备注等）
 */
@Data
public class AccountEditRequest {

    /** 账号名称 */
    private String accountName;

    /** 登录 Cookie（更换 Cookie 时传入） */
    private String cookieHeader;

    /** 备注 */
    private String remark;

    /** 状态（可选，传入则一并更新） */
    private String status;
}
