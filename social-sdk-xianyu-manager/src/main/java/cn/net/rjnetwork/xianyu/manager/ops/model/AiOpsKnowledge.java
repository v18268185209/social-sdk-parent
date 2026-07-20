package cn.net.rjnetwork.xianyu.manager.ops.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * AI 运营知识库实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ops_knowledge")
public class AiOpsKnowledge extends BaseEntity {

    private String category;

    /** PRICING / DESCRIPTION_STYLE / POSTING_TIME / KEYWORD */
    private String knowledgeType;

    private String content;

    /** AI_GENERATED / MANUAL / PLATFORM_RULE */
    private String source;

    private LocalDateTime updatedAt;
}
