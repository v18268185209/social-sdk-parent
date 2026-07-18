package cn.net.rjnetwork.xianyu.manager.account.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 闲鱼账号实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_account")
public class XianyuAccount extends BaseEntity {

    private String accountName;

    private String userId;

    private String displayName;

    private String cookieHeader;

    private String cookiesJson;

    private String status; // ACTIVE, DISABLED, FROZEN, COOKIE_EXPIRED

    private String remark;

    private String lastError;

    private LocalDateTime lastLoginAt;

    private LocalDateTime cookieExpiresAt;
}
