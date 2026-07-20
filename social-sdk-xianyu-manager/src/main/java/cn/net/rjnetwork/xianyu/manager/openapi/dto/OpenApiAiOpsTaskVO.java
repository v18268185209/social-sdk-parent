^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiAiOpsTaskVO {

    private Long id;
    private Long accountId;
    private String taskType;       // BATCH_CREATE / MULTI_ACCOUNT_SYNC / AUTO_REFRESH
    private String status;         // PENDING / RUNNING / COMPLETED / FAILED
    private String payload;        // JSON 任务参数
    private String resultSummary;  // AI 生成摘要
    private String errorMessage;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
