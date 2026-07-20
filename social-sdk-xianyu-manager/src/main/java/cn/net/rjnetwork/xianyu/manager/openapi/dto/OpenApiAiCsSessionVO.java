package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAiCsSessionVO {

    private Long id;
    private Long accountId;
    private String buyerId;
    private String buyerNickname;
    private Long productId;
    private Long orderId;
    private String status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
