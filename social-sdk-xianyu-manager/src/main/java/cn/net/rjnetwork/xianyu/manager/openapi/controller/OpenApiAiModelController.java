package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAiModelVO;
import cn.net.rjnetwork.xianyu.manager.ai.mapper.AiModelMapper;
import cn.net.rjnetwork.xianyu.manager.ai.model.AiModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/openapi/v1/ai/models")
public class OpenApiAiModelController {

    private final AiModelMapper mapper;

    public OpenApiAiModelController(AiModelMapper mapper) {
        this.mapper = mapper;
    }

    /** 全局模型目录（不含 apiKey 等凭据），任意合法 App 可读 */
    @GetMapping
    public OpenApiResponse<List<OpenApiAiModelVO>> list() {
        List<OpenApiAiModelVO> result = mapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(e -> {
                    OpenApiAiModelVO vo = new OpenApiAiModelVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }
}
