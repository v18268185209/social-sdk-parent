^package cn.net.rjnetwork.xianyu.manager.ops.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * AI 运营任务实体（批量上品、多账号同步等）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ops_task")
public class AiOpsTask extends BaseEntity {

    private Long accountId;

    /** BATCH_CREATE / MULTI_ACCOUNT_SYNC / AUTO_REFRESH */
    private String taskType;

    /** PENDING / RUNNING / COMPLETED / FAILED */
    private String status;

    private String payload;

    private String resultSummary;

    private String errorMessage;

    private LocalDateTime completedAt;
}
