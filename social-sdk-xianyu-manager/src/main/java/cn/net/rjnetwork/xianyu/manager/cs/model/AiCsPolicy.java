package cn.net.rjnetwork.xianyu.manager.cs.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * AI 客服策略配置实体（按账号独立）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_cs_policy")
public class AiCsPolicy extends BaseEntity {

    private Long accountId;

    /** AUTO / ASSIST / HYBRID */
    private String mode;

    private Boolean autoReplyEnabled;

    /** 底价比例（如 0.8 = 8 折是底价） */
    private Double priceFloorPct;

    /** 每次降价幅度（如 0.05 = 5%） */
    private Double priceStepPct;

    /** 最多降价几次 */
    private Integer maxDiscountSteps;

    /** 单会话每小时最大自动回复数 */
    private Integer maxAutoRepliesPerHour;

    /** 转人工意图 JSON: ["售后", "投诉"] */
    private String transferToIntents;

    /** FRIENDLY / PROFESSIONAL / CASUAL / HUMOROUS */
    private String tone;

    /** 自动回复生效时间（NULL 表示全天） */
    private String enabledFrom;

    private String enabledTo;
}
