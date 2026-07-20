^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAiCsKnowledgeVO {

    private Long id;
    private Long accountId;
    private Long productId;
    private String question;
    private String answer;
    private String category;
    private Integer priority;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
