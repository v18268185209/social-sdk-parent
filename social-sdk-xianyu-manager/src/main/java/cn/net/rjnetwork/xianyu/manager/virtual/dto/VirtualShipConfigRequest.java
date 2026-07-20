^package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;

/**
 * 自动发货配置创建请求
 */
@Data
public class VirtualShipConfigRequest {
    private Long accountId;
    private Boolean enabled;
    private Integer delaySeconds;
    private Integer autoConfirmDays;
    private Boolean notifyAfterShip;
}
