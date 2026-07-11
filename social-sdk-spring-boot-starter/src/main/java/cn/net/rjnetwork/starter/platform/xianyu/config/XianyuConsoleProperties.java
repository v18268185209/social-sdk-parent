package cn.net.rjnetwork.starter.platform.xianyu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 闲鱼控制台能力配置。
 * <p>说明：放在 social-sdk-spring-boot-starter 中，后续可并行增加 wechat/dingding 等平台能力。</p>
 */
@ConfigurationProperties(prefix = "social-sdk.console.xianyu")
public class XianyuConsoleProperties {

    /**
     * 是否启用闲鱼控制台能力。
     */
    private boolean enabled = false;

    /**
     * SQLite 文件路径。
     */
    private String sqlitePath = "./data/social-sdk-xianyu.db";

    /**
     * 启动时自动建表。
     */
    private boolean autoInitSchema = true;

    /**
     * 风控挑战时是否尝试自动在本机打开验证URL。
     */
    private boolean autoOpenVerificationUrl = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSqlitePath() {
        return sqlitePath;
    }

    public void setSqlitePath(String sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    public boolean isAutoInitSchema() {
        return autoInitSchema;
    }

    public void setAutoInitSchema(boolean autoInitSchema) {
        this.autoInitSchema = autoInitSchema;
    }

    public boolean isAutoOpenVerificationUrl() {
        return autoOpenVerificationUrl;
    }

    public void setAutoOpenVerificationUrl(boolean autoOpenVerificationUrl) {
        this.autoOpenVerificationUrl = autoOpenVerificationUrl;
    }
}
