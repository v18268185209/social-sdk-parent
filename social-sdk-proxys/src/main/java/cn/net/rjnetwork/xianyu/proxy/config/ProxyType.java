package cn.net.rjnetwork.xianyu.proxy.config;

/**
 * 代理 IP 类型枚举。不同类型对应不同的风控等级和成本。
 *
 * <ul>
 *   <li>{@link #RESIDENTIAL} - 住宅 IP，最安全，封号风险最低</li>
 *   <li>{@link #MOBILE_4G} - 4G 移动 CGNAT，IP 信誉最好，最贵</li>
 *   <li>{@link #DATACENTER} - 数据中心 IP，最危险，仅作兜底</li>
 * </ul>
 */
public enum ProxyType {

    /** 住宅宽带 IP，风控最友好 */
    RESIDENTIAL,

    /** 4G/5G 移动 CGNAT IP，信誉最好但最贵 */
    MOBILE_4G,

    /** 数据中心 IP，仅作最后兜底，闲鱼大概率拉黑 */
    DATACENTER;

    /**
     * 根据字符串值解析，兼容大小写，默认为 RESIDENTIAL。
     */
    public static ProxyType fromString(String value) {
        if (value == null || value.isBlank()) {
            return RESIDENTIAL;
        }
        for (ProxyType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return RESIDENTIAL;
    }
}
