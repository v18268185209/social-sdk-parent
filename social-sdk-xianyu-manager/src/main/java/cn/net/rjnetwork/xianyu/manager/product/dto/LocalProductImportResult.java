package cn.net.rjnetwork.xianyu.manager.product.dto;

import lombok.Data;
import java.util.List;

@Data
public class LocalProductImportResult {
    /** 总处理行数 */
    private int totalRows;
    /** 成功导入数 */
    private int imported;
    /** 跳过数（重复/无效） */
    private int skipped;
    /** 失败数 */
    private int failed;
    /** 失败详情 */
    private List<String> errors;

    public LocalProductImportResult(int totalRows, int imported, int skipped, int failed, List<String> errors) {
        this.totalRows = totalRows;
        this.imported = imported;
        this.skipped = skipped;
        this.failed = failed;
        this.errors = errors;
    }
}
