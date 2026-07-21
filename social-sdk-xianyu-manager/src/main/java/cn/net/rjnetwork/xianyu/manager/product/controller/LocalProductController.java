package cn.net.rjnetwork.xianyu.manager.product.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductBatchPublishRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductRequest;
import cn.net.rjnetwork.xianyu.manager.product.model.LocalProduct;
import cn.net.rjnetwork.xianyu.manager.product.service.LocalProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地商品 API：自建商品（待上架闲鱼）。
 * 发布成功后从本地表物理删除。
 */
@RestController
@RequestMapping("/api/local-products")
public class LocalProductController {

    private final LocalProductService localProductService;

    public LocalProductController(LocalProductService localProductService) {
        this.localProductService = localProductService;
    }

    @GetMapping
    public ApiResponse<Page<LocalProduct>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(localProductService.listPage(page, size, accountId, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<LocalProduct> getById(@PathVariable Long id) {
        LocalProduct p = localProductService.getById(id);
        if (p == null) return ApiResponse.fail("NOT_FOUND", "本地商品不存在");
        return ApiResponse.ok(p);
    }

    /** 保存草稿 / 提交待发布（action=DRAFT 或 SUBMIT） */
    @PostMapping
    public ApiResponse<LocalProduct> save(@RequestBody LocalProductRequest request) {
        try {
            return ApiResponse.ok(localProductService.saveDraft(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("SAVE_FAILED", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<LocalProduct> update(@PathVariable Long id, @RequestBody LocalProductRequest request) {
        request.setId(id);
        try {
            return ApiResponse.ok(localProductService.update(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        localProductService.delete(id);
        return ApiResponse.ok(null);
    }

    /** 单条立即发布 */
    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publishOne(@PathVariable Long id) {
        try {
            LocalProduct result = localProductService.publishOne(id);
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("published", true);
            data.put("message", "发布成功，本地商品已清理");
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail("PUBLISH_FAILED", e.getMessage());
        }
    }

    /** 批量发布 */
    @PostMapping("/batch-publish")
    public ApiResponse<LocalProductService.BatchPublishResult> batchPublish(
            @RequestBody LocalProductBatchPublishRequest request) {
        try {
            return ApiResponse.ok(localProductService.batchPublish(request));
        } catch (Exception e) {
            return ApiResponse.fail("BATCH_PUBLISH_FAILED", e.getMessage());
        }
    }
}
