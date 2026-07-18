package cn.net.rjnetwork.xianyu.manager.audit.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class AuditLog extends BaseEntity {

    private Long operatorId;
    private String operatorName;
    private String action;
    private String resourceType;
    private String resourceId;
    private String detail;
    private String ipAddress;
    private LocalDateTime actionTime;
}
