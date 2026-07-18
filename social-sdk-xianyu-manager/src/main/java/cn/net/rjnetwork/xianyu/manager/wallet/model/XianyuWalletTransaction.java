package cn.net.rjnetwork.xianyu.manager.wallet.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_wallet_transaction")
public class XianyuWalletTransaction extends BaseEntity {

    private Long accountId;
    private String transactionId;
    private String type; // INCOME, EXPENSE, TRANSFER（业务大类的兜底映射）
    private String bizType; // 接口返回的原始业务类型（如 提现/收款/退款）
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private String status; // 交易状态（SUCCESS/PROCESSING/FAILED...）
    private String tradeNo; // 流水号/交易单号（与 transactionId 区分，部分接口二者不同）
    private LocalDateTime transactionTime;
}
