package cn.net.rjnetwork.xianyu.manager.market.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 卖家画像（非自有账号）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seller_profile")
public class SellerProfile extends BaseEntity {

    private String userId;
    private String nickname;
    private String avatar;
    private String shopLevel;
    private Integer creditScore;
    private Integer followers;
    private Integer following;
    private Integer soldCount;
    private Integer onSaleCount;
    private String introduction;
    private String ipLocation;
    private LocalDateTime lastActiveAt;
    private LocalDateTime profileSyncedAt;
}
