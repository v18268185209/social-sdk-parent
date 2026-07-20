^package cn.net.rjnetwork.xianyu.manager.ai.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * AI 厂商实体（OpenAI 兼容协议）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider")
public class AiProvider extends BaseEntity {

    /** 展示名（如 Agnes AI / OpenAI / DeepSeek） */
    private String name;

    /** API 端点（如 https://apihub.agnes-ai.com/v1） */
    private String apiBaseUrl;

    /** API Key（明文存储，生产可加对称加密） */
    private String apiKey;

    /** 厂商类型（OPENAI_COMPATIBLE 等） */
    private String providerType;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}
