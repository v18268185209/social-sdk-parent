package cn.net.rjnetwork.xianyu.manager.cs.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * AI 客服会话实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_cs_session")
public class AiCsSession extends BaseEntity {

    private Long accountId;

    private String buyerId;

    private String buyerNickname;

    private Long productId;

    private Long orderId;

    /** ACTIVE / CLOSED / BLOCKED */
    private String status;

    private LocalDateTime lastMessageAt;
}
