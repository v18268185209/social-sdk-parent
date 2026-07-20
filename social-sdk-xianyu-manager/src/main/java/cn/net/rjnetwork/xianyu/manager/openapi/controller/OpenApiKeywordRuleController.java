^package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiKeywordRuleVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.rule.mapper.RuleMapper;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuKeywordRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/openapi/v1/keyword-rules")
public class OpenApiKeywordRuleController {

    private final RuleMapper ruleMapper;
    private final OpenAppService openAppService;

    public OpenApiKeywordRuleController(RuleMapper ruleMapper, OpenAppService openAppService) {
        this.ruleMapper = ruleMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiKeywordRuleVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiKeywordRuleVO> result = ruleMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || e.getAccountId() == null || bound.contains(e.getAccountId()))
                .filter(e -> accountId == null || (e.getAccountId() != null && e.getAccountId().equals(accountId)))
                .map(e -> {
                    OpenApiKeywordRuleVO vo = new OpenApiKeywordRuleVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiKeywordRuleVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        XianyuKeywordRule e = ruleMapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "规则不存在");
        if (e.getAccountId() != null) openAppService.assertAccountAccessible(app, e.getAccountId());
        OpenApiKeywordRuleVO vo = new OpenApiKeywordRuleVO();
        BeanUtils.copyProperties(e, vo);
        return OpenApiResponse.ok(vo);
    }
}
