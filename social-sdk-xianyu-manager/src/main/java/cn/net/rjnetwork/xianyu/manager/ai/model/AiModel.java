^package cn.net.rjnetwork.xianyu.manager.ai.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * AI 模型实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model")
public class AiModel extends BaseEntity {

    private Long providerId;

    /** 模型标识（如 agnes-2.0-flash） */
    private String modelName;

    /** 展示名 */
    private String displayName;

    /** 模型类型：TEXT / IMAGE / VIDEO */
    private String modelType;

    /** JSON 能力标签（streaming / tools / thinking / image_input） */
    private String capabilities;

    /** 默认温度 */
    private Double defaultTemperature;

    /** 默认最大输出 token 数 */
    private Integer defaultMaxTokens;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}
