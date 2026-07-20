package cn.net.rjnetwork.xianyu.manager.openapi.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 对外 OpenAPI 应用（调用方凭证）。
 * appSecret 明文仅在创建时返回一次，入库为 AES 加密串（appSecretEnc）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_app")
public class OpenApp extends BaseEntity {

    private String appName;

    private String appKey;

    /** AES 加密后的 appSecret（明文不落库） */
    private String appSecretEnc;

    /** ENABLED / DISABLED */
    private String status;

    /** 绑定账号白名单（JSON 数组字符串，如 "[1,2,3]"；空=不限制） */
    private String boundAccountIds;

    /** 单应用每分钟请求上限（0=不限制） */
    private Integer rateLimitPerMinute;

    /** 凭证过期时间（NULL=不过期） */
    private LocalDateTime expireAt;

    /** 最近一次成功调用时间 */
    private LocalDateTime lastUsedAt;
}
