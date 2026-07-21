package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class LocalProductImportPreview {
    /** 预览商品（最多 10 条） */
    private List<Map<String, Object>> items;
    /** 总行数 */
    private int totalRows;
    /** 有效行数 */
    private int validRows;
    /** 错误行 */
    private List<ImportError> errors;
    /** 重复行（按标题） */
    private int duplicateCount;
    /** 警告 */
    private List<String> warnings;

    @Data
    public static class ImportError {
        private int row;
        private String field;
        private String message;
    }
}
