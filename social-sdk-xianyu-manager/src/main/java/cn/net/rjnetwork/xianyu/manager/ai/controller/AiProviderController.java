^package cn.net.rjnetwork.xianyu.manager.ai.controller;

import cn.net.rjnetwork.xianyu.manager.ai.dto.AiProviderRequest;
import cn.net.rjnetwork.xianyu.manager.ai.dto.AiProviderUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiModel;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiProvider;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiModelService;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiProviderService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/providers")
public class AiProviderController {

    private final AiProviderService providerService;
    private final AiModelService modelService;

    public AiProviderController(AiProviderService providerService, AiModelService modelService) {
        this.providerService = providerService;
        this.modelService = modelService;
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

    /**
     * 根据厂商获取所有模型列表
     * GET /api/ai/providers/{id}/models
     */
    @GetMapping("/{id}/models")
    public ApiResponse<List<cn.net.rjnetwork.xianyu.manager.ai.model.AiModel>> getModelsByProvider(
            @PathVariable Long id,
            @RequestParam(required = false) String modelType) {
        if (providerService.getById(id) == null) {
            return ApiResponse.fail("NOT_FOUND", "Provider not found");
        }
        List<cn.net.rjnetwork.xianyu.manager.ai.model.AiModel> models = modelService.listByProvider(id, modelType);
        return ApiResponse.ok(models);
    }

    /**
     * 按 OpenAI 标准规范从远端厂商拉取可用模型列表
     * GET /api/ai/providers/{id}/remote-models
     */
    @GetMapping("/{id}/remote-models")
    public ApiResponse<List<Map<String, Object>>> listRemoteModels(@PathVariable Long id) {
        return ApiResponse.ok(providerService.listRemoteModels(id));
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
