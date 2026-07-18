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

    // ===== 个人信息（从闲鱼 API 获取） =====

    /** 头像 URL */
    private String avatar;

    /** 个人简介 */
    private String introduction;

    /** IP 属地 */
    private String ipLocation;

    /** 粉丝数 */
    private Integer followers;

    /** 关注数 */
    private Integer following;

    /** 卖出数 */
    private Integer soldCount;

    /** 买过数 */
    private Integer purchaseCount;

    /** 收藏数 */
    private Integer collectionCount;

    /** 在售宝贝数 */
    private Integer onSaleCount;

    /** 店铺等级 */
    private String shopLevel;

    /** 信用分 */
    private Integer creditScore;

    /** 信用评价数 */
    private Integer reviewNum;

    /** 上次 profile 同步时间 */
    private LocalDateTime profileSyncedAt;
}
