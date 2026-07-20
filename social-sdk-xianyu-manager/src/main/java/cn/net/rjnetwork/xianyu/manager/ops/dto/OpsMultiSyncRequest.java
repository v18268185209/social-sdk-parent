^package cn.net.rjnetwork.xianyu.manager.ops.dto;

import lombok.Data;
import java.util.List;

/**
 * 多账号同步请求 DTO（被 AiOpsService.startMultiAccountSync / doMultiAccountSync 使用）。
 * 把主账号的某个商品智能改写到多个目标账号（避免平台判重）。
 */
@Data
public class OpsMultiSyncRequest {
    /** 源账号 id */
    private Long sourceAccountId;
    /** 源商品 id */
    private Long productId;
    /** 目标账号 id 列表 */
    private List<Long> targetAccountIds;
    /** 每个账号间隔分钟数（避免高频被判风控），可空 */
    private Integer delayMinutesPerAccount;
}
