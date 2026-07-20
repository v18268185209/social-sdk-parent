^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiVirtualShipTaskVO {

    /** 由 productId / orderId 反查得到，便于外部按账号过滤与展示 */
    private Long accountId;
    private Long id;
    private Long orderId;
    private Long productId;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
