package cn.net.rjnetwork.xianyu.manager.monitor.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_result")
public class MonitorResult extends BaseEntity {

    private Long taskId;
    private String itemId;
    private String itemTitle;
    private Double price;
    private String imageUrl;
    private String sellerNickname;
    private Integer sellerCreditScore;
    private String itemUrl;
    private Double aiScore;
    private String aiReason;
    private String matchedKeywords;
    private Boolean notified;
    private Long snapshotId;
    private LocalDateTime createdAt;
}
