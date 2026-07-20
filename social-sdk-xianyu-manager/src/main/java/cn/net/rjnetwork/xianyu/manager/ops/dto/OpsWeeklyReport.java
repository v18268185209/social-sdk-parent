^package cn.net.rjnetwork.xianyu.manager.ops.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 运营周报 DTO（被 AiOpsService.generateWeeklyReport 使用）。
 */
@Data
public class OpsWeeklyReport {
    private Long accountId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int totalProducts;
    private int onSaleProducts;
    private int offSaleProducts;
    private int draftProducts;
    private int totalViews;
    private int totalFavorites;
    private int completedOrders;
    private int pendingOrders;
    private BigDecimal totalRevenue;
    private List<String> suggestions;
    private LocalDateTime generatedAt;
}
