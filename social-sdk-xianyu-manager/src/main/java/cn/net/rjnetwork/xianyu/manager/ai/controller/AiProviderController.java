package cn.net.rjnetwork.xianyu.manager.ai.controller;

import cn.net.rjnetwork.xianyu.manager.ai.dto.AiProviderRequest;
import cn.net.rjnetwork.xianyu.manager.ai.dto.AiProviderUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiProvider;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiProviderService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/providers")
public class AiProviderController {

    private final AiProviderService providerService;

    public AiProviderController(AiProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public ApiResponse<Page<AiProvider>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(providerService.listPage(page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AiProvider> getById(@PathVariable Long id) {
        AiProvider provider = providerService.getById(id);
        if (provider == null) return ApiResponse.fail("NOT_FOUND", "Provider not found");
        return ApiResponse.ok(provider);
    }

    @PostMapping
    public ApiResponse<AiProvider> create(@RequestBody AiProviderRequest request) {
        return ApiResponse.ok(providerService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiProvider> update(@PathVariable Long id, @RequestBody AiProviderUpdateRequest request) {
        request.setId(id);
        return ApiResponse.ok(providerService.update(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ApiResponse.ok(null);
    }
}
