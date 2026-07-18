package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAiOpsSuggestionVO {

    private Long id;
    private Long accountId;
    private String suggestionType; // PRICE_ADJUST / REFRESH_TIME / LISTING_OPTIMIZE
    private Long productId;
    private String suggestionContent; // JSON AI 建议详情
    private Double confidence;
    private Boolean adopted;
    private LocalDateTime adoptedAt;
    private String expectedImpact;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
