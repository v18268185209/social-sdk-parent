package cn.net.rjnetwork.xianyu.manager.product.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
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
     * 从闲鱼同步指定账号的在线商品到本地。
     * 需要账号已扫码登录且 cookie 有效。
     */
    @PostMapping("/sync")
    public ApiResponse<ProductService.SyncResult> syncFromXianyu(@RequestParam Long accountId) {
        try {
            ProductService.SyncResult result = productService.syncFromXianyu(accountId);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("INVALID_ARGUMENT", e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail("COOKIE_EXPIRED", e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("SYNC_FAILED", "Sync failed: " + e.getMessage());
        }
    }
}
