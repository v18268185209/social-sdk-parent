package cn.net.rjnetwork.xianyu.manager.product.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.service.ProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ApiResponse<XianyuProduct> create(@RequestBody ProductCreateRequest request) {
        return ApiResponse.ok(productService.create(request));
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

    @PostMapping("/{id}/shelf-on")
    public ApiResponse<XianyuProduct> shelfOn(@PathVariable Long id) {
        return ApiResponse.ok(productService.shelfOn(id));
    }

    @PostMapping("/{id}/shelf-off")
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
