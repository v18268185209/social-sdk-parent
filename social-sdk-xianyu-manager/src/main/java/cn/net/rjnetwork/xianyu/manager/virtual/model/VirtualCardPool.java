package cn.net.rjnetwork.xianyu.manager.virtual.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * 卡密池实体（Card / Account 类虚拟商品共用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("virtual_card_pool")
public class VirtualCardPool extends BaseEntity {

    private Long productId;

    /** 卡号 / 账号 */
    private String cardCode;

    /** 密码 / 密保 */
    private String cardPassword;

    /** AVAILABLE / USED / EXPIRED */
    private String status;

    private Long usedOrderId;

    private java.time.LocalDateTime usedAt;
}
