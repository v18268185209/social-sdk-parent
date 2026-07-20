^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.util.List;

@Data
public class OpenAppCreateRequest {

    private String appName;

    /** 绑定账号白名单；空=不限制（可访问所有账号） */
    private List<Long> boundAccountIds;

    /** 单应用每分钟请求上限；0=不限制 */
    private Integer rateLimitPerMinute;
}
