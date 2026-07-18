package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiVirtualShipConfigVO {

    private Long id;
    private Long accountId;
    private Boolean enabled;
    private Integer delaySeconds;
    private Integer autoConfirmDays;
    private Boolean notifyAfterShip;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
