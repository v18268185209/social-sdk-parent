package cn.net.rjnetwork.xianyu.manager.virtual.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * 自动发货任务实体（定时扫描执行）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("virtual_ship_task")
public class VirtualShipTask extends BaseEntity {

    private Long orderId;

    private Long productId;

    /** PENDING / PROCESSING / SHIPPED / FAILED / SKIPPED */
    private String status;

    private Integer retryCount;

    private String errorMessage;

    private java.time.LocalDateTime processedAt;
}
