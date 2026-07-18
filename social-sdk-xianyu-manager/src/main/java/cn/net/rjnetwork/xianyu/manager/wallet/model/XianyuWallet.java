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
    /** 账户余额（总额，单位：元） */
    private BigDecimal balance;
    /** 冻结金额 */
    private BigDecimal frozenAmount;
    /** 可用余额（余额 - 冻结，部分接口区分） */
    private BigDecimal availableBalance;
    /** 总资产（含在途/理财等，视接口返回） */
    private BigDecimal totalAssets;
    /** 可提现金额 */
    private BigDecimal withdrawableAmount;
    /** 绑定支付宝账号（脱敏，如 138****0000） */
    private String alipayAccount;
    /** 支付宝实名（如 张*三） */
    private String alipayRealName;
    /** 绑定银行卡摘要（如 招商银行(1234)），多卡时取主卡 */
    private String bankCard;
}
