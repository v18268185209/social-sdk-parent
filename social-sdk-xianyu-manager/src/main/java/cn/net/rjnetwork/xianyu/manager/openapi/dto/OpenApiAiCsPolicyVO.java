^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAiCsPolicyVO {

    private Long id;
    private Long accountId;
    private String mode;
    private Boolean autoReplyEnabled;
    private Double priceFloorPct;
    private Double priceStepPct;
    private Integer maxDiscountSteps;
    private Integer maxAutoRepliesPerHour;
    private String transferToIntents;
    private String tone;
    private String enabledFrom;
    private String enabledTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
