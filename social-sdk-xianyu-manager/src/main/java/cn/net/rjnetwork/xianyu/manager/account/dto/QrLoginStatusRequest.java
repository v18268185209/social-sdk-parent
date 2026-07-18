package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 二维码登录状态查询请求
 */
@Data
public class QrLoginStatusRequest {

    private String sessionId;
}
