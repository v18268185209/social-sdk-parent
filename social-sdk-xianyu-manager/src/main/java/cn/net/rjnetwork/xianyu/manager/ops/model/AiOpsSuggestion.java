package cn.net.rjnetwork.xianyu.manager.ops.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * AI 运营建议记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ops_suggestion")
public class AiOpsSuggestion extends BaseEntity {

    private Long accountId;

    /** PRICE_ADJUST / REFRESH_TIME / LISTING_OPTIMIZE */
    private String suggestionType;

    private Long productId;

    private String suggestionContent;

    private Double confidence;

    private Boolean adopted;

    private LocalDateTime adoptedAt;

    private String expectedImpact;
}
