package cn.net.rjnetwork.xianyu.manager.monitor.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_task")
public class MonitorTask extends BaseEntity {

    private Long accountId;
    private String name;
    private String taskType;            // KEYWORD / AI / CATEGORY
    private String status;              // ACTIVE / PAUSED / DELETED

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
    private String aiPrompt;
    private Long aiModelId;

    private String cronExpression;
    private Integer intervalMinutes;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private String lastResultSummary;
    private Integer runCount = 0;
    private Integer consecutiveFailures = 0;
    private Boolean circuitOpen = false;
    private LocalDateTime circuitOpenUntil;

    private Boolean notifyOnMatch;
    private Long notifyChannelId;
}
