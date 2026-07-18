package cn.net.rjnetwork.xianyu.manager.rule.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_auto_reply_config")
public class XianyuAutoReplyConfig extends BaseEntity {

    private Long accountId;

    // ===== AI 配置 =====
    private Boolean aiEnabled;           // 是否启用 AI 接管
    private String aiProvider;           // OPENAI, CLAUDE, CUSTOM 等
    private String aiApiKey;             // API Key（加密存储）
    private String aiApiUrl;             // 自定义 API 地址
    private String aiModel;              // 模型名称
    private String aiSystemPrompt;       // 系统提示词
    private Double aiTemperature;        // 温度参数

    // ===== 自动回复配置 =====
    private Boolean autoReplyEnabled;    // 是否启用兜底自动回复
    private String welcomeMessage;       // 欢迎语（首次对话触发）
    private String fallbackReply;        // 兜底回复话术
    private Integer idleTimeoutMinutes;  // 超时自动回复时间（分钟）
    private String idleReply;            // 超时回复话术
    private Boolean offlineReplyEnabled; // 离线自动回复
    private String offlineReply;         // 离线回复话术

    // ===== 全局配置 =====
    private Boolean notifyOnNewMessage;  // 新消息通知管理员
    private Boolean includeChatHistory;  // AI 回复时是否带上历史上下文
}
