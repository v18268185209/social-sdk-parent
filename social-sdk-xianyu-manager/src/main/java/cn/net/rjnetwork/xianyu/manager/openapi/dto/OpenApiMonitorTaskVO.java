package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外监控任务视图对象：不含 cronExpression / aiPrompt 等敏感运营配置。
 */
@Data
public class OpenApiMonitorTaskVO {

    private Long id;

    private Long accountId;

    private String name;

    private String taskType;

    private String status;

    private String keyword;

    private String categoryId;

    private Double minPrice;

    private Double maxPrice;

    private String itemCondition;

    private String locationProvince;

    private String locationCity;

    private String locationDistrict;

    private Integer freeShipping;

    private Integer maxAgeHours;

    private Boolean aiEnabled;

    private Long aiModelId;

    private LocalDateTime nextRunAt;

    private LocalDateTime lastRunAt;

    private String lastResultSummary;

    private Integer runCount;

    private Integer consecutiveFailures;

    private Boolean circuitOpen;

    private Boolean notifyOnMatch;

    private Long notifyChannelId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
