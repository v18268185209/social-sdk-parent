^package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 二维码登录请求
 */
@Data
public class QrLoginRequest {

    private String accountName;

    private String remark;
}
