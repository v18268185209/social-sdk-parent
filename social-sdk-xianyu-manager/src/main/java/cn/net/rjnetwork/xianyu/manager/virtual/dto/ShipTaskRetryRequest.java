^package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;

/**
 * 发货任务重试请求
 */
@Data
public class ShipTaskRetryRequest {
    private Long taskId;
}
