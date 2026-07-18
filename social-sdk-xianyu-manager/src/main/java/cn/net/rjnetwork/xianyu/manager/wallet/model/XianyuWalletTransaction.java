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
    private String type; // INCOME, EXPENSE, TRANSFER
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime transactionTime;
}
