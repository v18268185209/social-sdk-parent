package cn.net.rjnetwork.xianyu.proxy.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 账号-IP 绑定持久化实体。SQLite 表 {@code proxy_account_binding} 的 PO。
 *
 * <p>落库字段与内存 {@link cn.net.rjnetwork.xianyu.proxy.core.DefaultProxyPoolManager} 的 BindingInfo 对齐，
 * 保证应用重启后可还原绑定关系。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyAccountBinding {

    /** 主键 ID */
    private Long id;

    /** 闲鱼账号 ID */
    private Long accountId;

    /** 供应商类型 */
    private String providerType;

    /** 代理主机 */
    private String host;

    /** 代理端口 */
    private Integer port;

    /** 代理用户名 */
    private String username;

    /** 代理密码 */
    private String password;

    /** 出口 IP */
    private String exitIp;

    /** 出口城市 */
    private String city;

    /** 绑定时间 */
    private LocalDateTime boundAt;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;

    /** 累计使用次数 */
    private Integer useCount;

    /** 是否 captcha 已通过 */
    private Boolean captchaPassed;

    /** 逻辑删除 */
    private Integer deleted;
}
