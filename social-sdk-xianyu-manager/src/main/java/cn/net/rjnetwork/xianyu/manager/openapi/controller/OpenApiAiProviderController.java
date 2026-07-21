package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiProviderVO;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiProviderMapper;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 厂商配置对外接口：全局目录，不含 apiKey 密文。
 */
@RestController
@RequestMapping("/openapi/v1/ai/providers")
public class OpenApiAiProviderController {

    private final AiProviderMapper providerMapper;
    private final OpenAppService openAppService;

    public OpenApiAiProviderController(AiProviderMapper providerMapper, OpenAppService openAppService) {
        this.providerMapper = providerMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiAiProviderVO>> list(@RequestParam(required = false) Boolean enabled) {
        LambdaQueryWrapper<AiProvider> qw = new LambdaQueryWrapper<AiProvider>()
                .orderByDesc(AiProvider::getUpdatedAt);
        if (enabled != null) qw.eq(AiProvider::getEnabled, enabled);

        List<OpenApiAiProviderVO> result = providerMapper.selectList(qw).stream()
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    private OpenApiAiProviderVO toVo(AiProvider p) {
        OpenApiAiProviderVO vo = new OpenApiAiProviderVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setApiBaseUrl(p.getApiBaseUrl());
        vo.setProviderType(p.getProviderType());
        vo.setEnabled(p.getEnabled());
        vo.setRemark(p.getRemark());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
