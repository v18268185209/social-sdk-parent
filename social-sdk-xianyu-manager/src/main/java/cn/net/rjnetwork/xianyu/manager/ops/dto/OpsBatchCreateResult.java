package cn.net.rjnetwork.xianyu.manager.ops.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量上品进度/结果 DTO（被 AiOpsService.doBatchCreate / getBatchProgress 使用）。
 */
@Data
public class OpsBatchCreateResult {
    private int total;
    private int success;
    private int failed;
    private List<Long> createdProductIds = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
