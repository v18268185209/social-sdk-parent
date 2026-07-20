package cn.net.rjnetwork.xianyu.proxy.config;

/**
 * 代理供应商类型枚举。每新增一个供应商，在此枚举注册即可。
 *
 * <p>已有的适配器：</p>
 * <ul>
 *   <li>{@link #ABUYUN}     - 阿布云 (abuyun.com)    — 国内住宅，按请求计费</li>
 *   <li>{@link #KUAILAILI}  - 快代理 (kuaidaili.com) — 国内住宅，隧道代理</li>
 *   <li>{@link #SMARTPROXY}- Smartproxy            — 国际住宅，按流量计费</li>
 *   <li>{@link #CUSTOM}    - 自定义实现，业务方自己写适配器</li>
 * </ul>
 */
public enum ProviderType {

    /** 阿布云 (abuyun.com) — 国内住宅代理 */
    ABUYUN("Abuyun", "阿布云"),

    /** 快代理 (kuaidaili.com) — 国内住宅隧道代理 */
    KUAILAILI("Kuaidaili", "快代理"),

    /** Smartproxy — 国际住宅代理 */
    SMARTPROXY("Smartproxy", "Smartproxy"),

    /** 青果网络 (qg.net) — 国内短效/隧道代理 */
    QG("QG", "青果网络"),

    /** GoLogin / AdsPower 等反指纹浏览器内置代理 */
    ANTIDETECT_BROWSER("AntiDetectBrowser", "反指纹浏览器"),

    /** 自建宽带 / frp 穿透 */
    SELF_HOSTED("SelfHosted", "自建代理"),

    /** 自定义实现 */
    CUSTOM("Custom", "自定义");

    private final String code;
    private final String displayName;

    ProviderType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProviderType fromString(String value) {
        if (value == null || value.isBlank()) {
            return SELF_HOSTED;
        }
        for (ProviderType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim()) || t.code.equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return SELF_HOSTED;
    }
}
