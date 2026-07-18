package cn.net.rjnetwork.xianyu.manager.wallet.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_wallet")
public class XianyuWallet extends BaseEntity {

    private Long accountId;
    private BigDecimal balance;
    private BigDecimal frozenAmount;
    private String alipayAccount;
    private String bankCard;
}
