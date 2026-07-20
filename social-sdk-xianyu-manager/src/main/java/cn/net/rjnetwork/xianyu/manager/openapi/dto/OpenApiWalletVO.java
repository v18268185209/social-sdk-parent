^package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外钱包视图对象。alipayAccount / alipayRealName / bankCard 在存储层已脱敏（如 138****0000 / 张*三 / 招商银行(1234)），可安全透出。
 */
@Data
public class OpenApiWalletVO {

    private Long id;

    private Long accountId;

    private BigDecimal balance;

    private BigDecimal frozenAmount;

    private BigDecimal availableBalance;

    private BigDecimal totalAssets;

    private BigDecimal withdrawableAmount;

    private String alipayAccount;

    private String alipayRealName;

    private String bankCard;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
