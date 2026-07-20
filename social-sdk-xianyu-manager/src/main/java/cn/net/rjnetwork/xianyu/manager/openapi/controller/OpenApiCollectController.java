package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiCollectVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.collect.mapper.CollectMapper;
import cn.net.rjnetwork.xianyu.manager.collect.model.XianyuCollect;
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
@RequestMapping("/openapi/v1/collects")
public class OpenApiCollectController {

    private final CollectMapper mapper;
    private final OpenAppService openAppService;

    public OpenApiCollectController(CollectMapper mapper, OpenAppService openAppService) {
        this.mapper = mapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiCollectVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiCollectVO> result = mapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(e -> bound.isEmpty() || e.getAccountId() == null || bound.contains(e.getAccountId()))
                .filter(e -> accountId == null || (e.getAccountId() != null && e.getAccountId().equals(accountId)))
                .map(e -> {
                    OpenApiCollectVO vo = new OpenApiCollectVO();
                    BeanUtils.copyProperties(e, vo);
                    return vo;
                })
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiCollectVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        XianyuCollect e = mapper.selectById(id);
        if (e == null) throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "收藏不存在");
        if (e.getAccountId() != null) openAppService.assertAccountAccessible(app, e.getAccountId());
        OpenApiCollectVO vo = new OpenApiCollectVO();
        BeanUtils.copyProperties(e, vo);
        return OpenApiResponse.ok(vo);
    }
}
