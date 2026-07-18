package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外钱包流水视图对象。
 */
@Data
public class OpenApiWalletTransactionVO {

    private Long id;

    private Long accountId;

    private String transactionId;

    private String type;

    private String bizType;

    private BigDecimal amount;

    private BigDecimal balanceAfter;

    private String description;

    private String status;

    private String tradeNo;

    private LocalDateTime transactionTime;

    private LocalDateTime createdAt;
}
