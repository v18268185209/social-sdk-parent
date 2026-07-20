package cn.net.rjnetwork.xianyu.manager.cs.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * AI 客服消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_cs_message")
public class AiCsMessage extends BaseEntity {

    private Long sessionId;

    /** INCOMING(买家) / OUTGOING(AI/运营) */
    private String direction;

    private String content;

    /** 意图分类：议价 / 确认商品 / 物流 / 售后 / 闲聊 */
    private String intent;

    private Double intentConfidence;

    private Boolean aiGenerated;

    /** AUTO / AI_ASSIST / HUMAN */
    private String sentBy;

    private String rawAiResponse;

    private LocalDateTime createdAt;
}
