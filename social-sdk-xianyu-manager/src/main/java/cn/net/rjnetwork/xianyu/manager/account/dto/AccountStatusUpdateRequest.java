package cn.net.rjnetwork.xianyu.manager.account.dto;

import lombok.Data;

/**
 * 账号状态更新请求
 */
@Data
public class AccountStatusUpdateRequest {

    private String status;

    private String remark;
}
