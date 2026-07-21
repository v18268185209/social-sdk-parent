package cn.net.rjnetwork.xianyu.manager.product.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportPreview;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportResult;
import cn.net.rjnetwork.xianyu.manager.product.service.LocalProductService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地商品批量导入 API：预览 + 确认写入
 */
@RestController
@RequestMapping("/api/local-products/import")
public class LocalProductImportController {

    private final LocalProductService localProductService;

    public LocalProductImportController(LocalProductService localProductService) {
        this.localProductService = localProductService;
    }

    /** 预览导入（解析 CSV，返回前 10 条 + 错误明细，不写入 DB） */
    @PostMapping("/preview")
    public ApiResponse<LocalProductImportPreview> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deduplicate", required = false, defaultValue = "false") boolean deduplicate,
            @RequestParam(value = "overwriteDuplicate", required = false, defaultValue = "false") boolean overwriteDuplicate,
            @RequestParam(value = "defaultGoodsType", required = false, defaultValue = "PHYSICAL") String defaultGoodsType,
            @RequestParam(value = "imageStoragePath", required = false, defaultValue = "/uploads/local-products") String imageStoragePath,
            @RequestParam(value = "deliverContentSeparator", required = false, defaultValue = "\\|\\|\\|") String separator) {
        try {
            LocalProductImportRequest req = new LocalProductImportRequest();
            req.setFile(file);
            req.setDeduplicate(deduplicate);
            req.setOverwriteDuplicate(overwriteDuplicate);
            req.setDefaultGoodsType(defaultGoodsType);
            req.setImageStoragePath(imageStoragePath);
            req.setDeliverContentSeparator(separator);
            return ApiResponse.ok(localProductService.previewImport(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("PARSE_ERROR", "CSV 解析失败: " + e.getMessage());
        }
    }

    /** 确认导入（解析 CSV 写入 local_product 表，返回统计） */
    @PostMapping("/confirm")
    public ApiResponse<LocalProductImportResult> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deduplicate", required = false, defaultValue = "false") boolean deduplicate,
            @RequestParam(value = "overwriteDuplicate", required = false, defaultValue = "false") boolean overwriteDuplicate,
            @RequestParam(value = "defaultGoodsType", required = false, defaultValue = "PHYSICAL") String defaultGoodsType,
            @RequestParam(value = "imageStoragePath", required = false, defaultValue = "/uploads/local-products") String imageStoragePath,
            @RequestParam(value = "deliverContentSeparator", required = false, defaultValue = "\\|\\|\\|") String separator) {
        try {
            LocalProductImportRequest req = new LocalProductImportRequest();
            req.setFile(file);
            req.setDeduplicate(deduplicate);
            req.setOverwriteDuplicate(overwriteDuplicate);
            req.setDefaultGoodsType(defaultGoodsType);
            req.setImageStoragePath(imageStoragePath);
            req.setDeliverContentSeparator(separator);
            return ApiResponse.ok(localProductService.confirmImport(req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("IMPORT_FAILED", "导入失败: " + e.getMessage());
        }
    }
}
