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

    // ===== IM / 滑块验证 cookie（与登录 cookie 分开存储） =====

    /** IM 滑块验证后获取的 cookie（x5sec 等），不覆盖登录 cookie */
    private String imCookieHeader;

    /** IM 设备 ID */
    private String imDeviceId;

    /** IM accessToken（缓存） */
    private String imAccessToken;

    /** IM token 过期时间 */
    private LocalDateTime imTokenExpiresAt;

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

    // ===== Chrome 容器隔离字段 =====

    /** 账号独占 Chrome user-data-dir 路径 */
    private String chromeProfilePath;

    /** 账号独占 Chrome CDP 端口 */
    private Integer cdpPort;

    /** 账号绑定的代理 URL（http://host:port 或 socks5://host:port） */
    private String proxyUrl;

    /** Chrome 容器当前状态（RUNNING/CRASHED/STOPPED/LAUNCHING 等） */
    private String chromeStatus;

    /** Chrome 容器崩溃次数 */
    private Integer chromeCrashCount;

    /** Chrome 容器指纹 seed（用于派生反检测噪声） */
    private Long chromeSeed;

    /** Chrome 容器启动时间 */
    private LocalDateTime chromeLaunchedAt;
}
