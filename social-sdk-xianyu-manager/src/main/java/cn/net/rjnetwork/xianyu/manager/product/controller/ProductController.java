package cn.net.rjnetwork.xianyu.manager.product.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.config.AsyncConfig;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductService;
import cn.net.rjnetwork.xianyu.manager.product.service.SyncProgressService;
import cn.net.rjnetwork.xianyu.manager.product.service.PolishService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final PolishService polishService;
    private final SyncProgressService syncProgressService;
    private final Executor syncTaskExecutor;

    public ProductController(ProductService productService, PolishService polishService,
                             SyncProgressService syncProgressService,
                             Executor syncTaskExecutor) {
        this.productService = productService;
        this.polishService = polishService;
        this.syncProgressService = syncProgressService;
        this.syncTaskExecutor = syncTaskExecutor;
    }

    @GetMapping
    public ApiResponse<Page<XianyuProduct>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(productService.listPage(page, size, accountId, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<XianyuProduct> getById(@PathVariable Long id) {
        XianyuProduct product = productService.getById(id);
        if (product == null) return ApiResponse.fail("NOT_FOUND", "Product not found");
        return ApiResponse.ok(product);
    }

    /**
     * 上传图片/视频文件到本地存储，返回可访问 URL。
     * 真实场景可替换为 OSS / 网盘直传，当前落盘到 data/uploads/ 对外开放 /uploads/**
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(@RequestParam MultipartFile file) {
        try {
            String url = productService.storeFile(file);
            return ApiResponse.ok(Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("UPLOAD_FAILED", e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<XianyuProduct> create(@RequestBody ProductCreateRequest request) {
        try {
            return ApiResponse.ok(productService.create(request));
        } catch (IllegalArgumentException e) {
            // 业务校验失败（缺图片 / 缺账号等）→ 友好提示，带上具体原因
            return ApiResponse.fail("BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            // 闲鱼接口失败（cookie 过期 / 发布被拒 等）→ 带上闲鱼返回的 msg
            return ApiResponse.fail("PUBLISH_FAILED", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<XianyuProduct> update(@PathVariable Long id, @RequestBody ProductUpdateRequest request) {
        request.setId(id);
        return ApiResponse.ok(productService.update(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * 拉闲鱼商品分类树 — 供前端创建商品表单的下拉选择
     * <p>不让用户手输 catId，从闲鱼拉分类树按 children[] 嵌套渲染下拉。
     * 需要账号已扫码登录且 cookie 有效。</p>
     *
     * @param accountId 账号 id
     * @return 闲鱼分类树 JSON（节点含 catId/catName/channelCatId/tbCatId/children[]）
     */
    @GetMapping("/category-tree")
    public ApiResponse<Object> getCategoryTree(@RequestParam Long accountId) {
        try {
            return ApiResponse.ok(productService.getCategoryTree(accountId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("INVALID_ARGUMENT", e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail("COOKIE_EXPIRED", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("CATEGORY_TREE_FAILED", "Failed to fetch category tree: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/shelf-on")
    @Audit("商品上架")
    public ApiResponse<XianyuProduct> shelfOn(@PathVariable Long id) {
        return ApiResponse.ok(productService.shelfOn(id));
    }

    @PostMapping("/{id}/shelf-off")
    @Audit("商品下架")
    public ApiResponse<XianyuProduct> shelfOff(@PathVariable Long id) {
        return ApiResponse.ok(productService.shelfOff(id));
    }

    @PutMapping("/{id}/price")
    public ApiResponse<XianyuProduct> updatePrice(@PathVariable Long id, @RequestParam String price) {
        return ApiResponse.ok(productService.updatePrice(id, new java.math.BigDecimal(price)));
    }

    @PutMapping("/{id}/stock")
    public ApiResponse<XianyuProduct> updateStock(@PathVariable Long id, @RequestParam int stock) {
        return ApiResponse.ok(productService.updateStock(id, stock));
    }

    /**
     * 启动同步任务（异步执行），返回 syncId 供前端轮询进度。
     * 需要账号已扫码登录且 cookie 有效。
     *
     * @param accountId 账号 id
     * @return {syncId, message}
     */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncFromXianyu(@RequestParam Long accountId) {
        try {
            // 防重复提交：先查有没有正在跑的（只查不改）
            String runningSyncId = syncProgressService.getRunningSyncId(accountId);
            if (runningSyncId != null) {
                return ApiResponse.ok(Map.of("syncId", runningSyncId, "message", "同步任务已在进行中"));
            }
            // 确认无在跑任务后再新建
            String syncId = syncProgressService.createNewSyncId(accountId);
            // 手动提交到线程池
            syncTaskExecutor.execute(() -> productService.syncFromXianyuAsync(accountId, syncId));
            return ApiResponse.ok(Map.of("syncId", syncId, "message", "同步任务已启动"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("INVALID_ARGUMENT", e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail("COOKIE_EXPIRED", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("SYNC_FAILED", "Sync failed: " + e.getMessage());
        }
    }

    /**
     * 查询同步进度。前端每 1-2 秒轮询一次，直到 phase=COMPLETED 或 phase=FAILED。
     *
     * @param syncId 同步任务 id
     * @return 进度详情（phase / total / current / inserted / updated / failed / message）
     */
    @GetMapping("/sync/progress")
    public ApiResponse<SyncProgressService.Progress> getSyncProgress(@RequestParam String syncId) {
        SyncProgressService.Progress progress = syncProgressService.getProgress(syncId);
        if (progress == null) {
            return ApiResponse.fail("NOT_FOUND", "同步任务不存在或已过期");
        }
        return ApiResponse.ok(progress);
    }

    // ======================== 商品擦亮（对齐前端 /api/products/polish）========================

    @PostMapping("/polish")
    public ApiResponse<Map<String, Object>> polish(@RequestParam Long accountId, @RequestParam String itemId) {
        try {
            return ApiResponse.ok(polishService.polish(accountId, itemId));
        } catch (IllegalStateException e) {
            return ApiResponse.fail("RISK_CONTROL", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("POLISH_FAILED", "擦亮失败: " + e.getMessage());
        }
    }

    @PostMapping("/polish/batch")
    public ApiResponse<Map<String, Object>> polishBatch(@RequestBody Map<String, Object> body) {
        try {
            Long accountId = Long.valueOf(String.valueOf(body.get("accountId")));
            @SuppressWarnings("unchecked")
            java.util.List<String> itemIds = (java.util.List<String>) body.get("itemIds");
            return ApiResponse.ok(polishService.batchPolish(accountId, itemIds));
        } catch (IllegalStateException e) {
            return ApiResponse.fail("RISK_CONTROL", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("POLISH_FAILED", "批量擦亮失败: " + e.getMessage());
        }
    }

    @PostMapping("/polish/super")
    public ApiResponse<Map<String, Object>> polishSuper(@RequestParam Long accountId, @RequestParam String itemId,
                                                        @RequestParam(required = false) Integer times) {
        try {
            return ApiResponse.ok(polishService.superPolish(accountId, itemId, times));
        } catch (IllegalStateException e) {
            return ApiResponse.fail("RISK_CONTROL", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("POLISH_FAILED", "超级擦亮失败: " + e.getMessage());
        }
    }

    // ==================== 虚拟发货配置（商品级） ====================

    /**
     * 查询全部商品，供虚拟发货页商品列表展示。
     * GET /api/products/for-virtual-ship?accountId=1
     */
    @GetMapping("/for-virtual-ship")
    public ApiResponse<java.util.List<XianyuProduct>> listForVirtualShip(
            @RequestParam(required = false) Long accountId) {
        return ApiResponse.ok(productService.listAllProducts(accountId));
    }

    /**
     * 保存商品级虚拟发货配置（deliver_type + deliver_content_template + goods_type）。
     * PUT /api/products/{id}/virtual-ship-config
     * {
     *   "productId": 1,
     *   "goodsType": "VIRTUAL",
     *   "deliverType": "CARD",        // CARD / ACCOUNT / LINK / FILE
     *   "deliverContentTemplate": "卡号：${cardCode}\n密码：${cardPassword}"
     * }
     * <p>模板支持占位符：
     * <ul>
     *   <li>CARD/ACCOUNT: ${cardCode} ${cardPassword}</li>
     *   <li>FILE(网盘): ${link} ${extractCode} ${fileName}</li>
     *   <li>通用: ${itemTitle} ${orderId}</li>
     * </ul></p>
     */
    @PutMapping("/{id}/virtual-ship-config")
    @Audit("保存虚拟发货配置")
    public ApiResponse<XianyuProduct> saveVirtualShipConfig(
            @PathVariable Long id,
            @RequestBody cn.net.rjnetwork.xianyu.manager.virtual.dto.VirtualShipProductConfigRequest request) {
        request.setProductId(id);
        return ApiResponse.ok(productService.saveVirtualShipConfig(
                request.getProductId(),
                request.getGoodsType(),
                request.getDeliverType(),
                request.getDeliverContentTemplate()
        ));
    }
}
