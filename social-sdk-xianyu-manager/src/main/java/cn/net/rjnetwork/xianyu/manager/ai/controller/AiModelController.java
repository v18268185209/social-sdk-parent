package cn.net.rjnetwork.xianyu.manager.ai.controller;

import cn.net.rjnetwork.xianyu.manager.ai.dto.AiModelRequest;
import cn.net.rjnetwork.xianyu.manager.ai.dto.AiModelUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiModel;
import cn.net.rjnetwork.xianyu.manager.ai.service.AiModelService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/models")
public class AiModelController {

    private final AiModelService modelService;

    public AiModelController(AiModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public ApiResponse<Page<AiModel>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(modelService.listPage(page, size, providerId, modelType, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AiModel> getById(@PathVariable Long id) {
        AiModel model = modelService.getById(id);
        if (model == null) return ApiResponse.fail("NOT_FOUND", "Model not found");
        return ApiResponse.ok(model);
    }

    @PostMapping
    public ApiResponse<AiModel> create(@RequestBody AiModelRequest request) {
        return ApiResponse.ok(modelService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AiModel> update(@PathVariable Long id, @RequestBody AiModelUpdateRequest request) {
        request.setId(id);
        return ApiResponse.ok(modelService.update(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return ApiResponse.ok(null);
    }
}
