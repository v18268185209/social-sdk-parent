^package cn.net.rjnetwork.xianyu.manager.cs.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * AI 客服知识库实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_cs_knowledge")
public class AiCsKnowledge extends BaseEntity {

    /** NULL = 全局共享 */
    private Long accountId;

    /** NULL = 通用知识 */
    private Long productId;

    private String question;

    private String answer;

    /** PRICE / SHIPPING / AFTERSALES / GENERAL / PRODUCT */
    private String category;

    private Integer priority;

    private Boolean isActive;
}
