^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiCollectVO {

    private Long id;
    private Long accountId;
    private String targetType;  // ITEM / USER / SHOP
    private String targetId;
    private String targetName;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
