package cn.net.rjnetwork.xianyu.manager.proxy.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理池配置持久化实体。对应 DB 表 {@code proxy_config}。
 *
 * <p>一条记录 = 一个供应商（或 'global' 全局配置）。
 * credential 等敏感字段在 API 返回时会脱敏。</p>
 */
@Data
@TableName("proxy_config")
public class ProxyConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商类型或 'global' */
    private String providerType;

    /** JSON 序列化的该供应商配置 */
    private String configJson;

    /** 0=禁用 1=启用 */
    private Integer enabled = 1;

    /** 排序优先级，数字越小优先级越高 */
    private Integer sortOrder = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
