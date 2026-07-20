^package cn.net.rjnetwork.xianyu.manager.virtual.dto;

import lombok.Data;

/**
 * 自动发货配置更新请求
 */
@Data
public class VirtualShipConfigUpdateRequest {
    private Long id;
    private Boolean enabled;
    private Integer delaySeconds;
    private Integer autoConfirmDays;
    private Boolean notifyAfterShip;
}
