package cn.net.rjnetwork.xianyu.manager.ops.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量上品请求 DTO（被 AiOpsService.startBatchCreate 使用）。
 */
@Data
public class OpsBatchCreateRequest {
    /** 目标账号 id */
    private Long accountId;
    /** 商品类目 id */
    private String category;
    /** 待生成的商品种子列表 */
    private List<ProductSeed> products;

    /**
     * 商品种子 — AI 根据它生成标题/描述/价格，再调 ProductService.create 上架。
     */
    @Data
    public static class ProductSeed {
        /** 来源描述（如原商品链接、关键词） */
        private String source;
        /** 关键词列表（AI 生成标题用） */
        private List<String> keywords;
        /** 图片 URL 列表 */
        private List<String> imageUrls;
        /** 建议价格（可空，AI 兜底生成） */
        private BigDecimal suggestedPrice;
        /** 成色描述 */
        private String condition;
    }
}
