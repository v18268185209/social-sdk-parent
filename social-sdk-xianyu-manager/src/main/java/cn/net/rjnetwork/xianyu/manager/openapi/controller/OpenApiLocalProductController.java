package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiLocalProductVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.product.mapper.LocalProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.LocalProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 本地商品域对外接口：列表按绑定白名单过滤，详情做账号作用域校验。
 */
@RestController
@RequestMapping("/openapi/v1/local-products")
public class OpenApiLocalProductController {

    private final LocalProductMapper productMapper;
    private final OpenAppService openAppService;

    public OpenApiLocalProductController(LocalProductMapper productMapper, OpenAppService openAppService) {
        this.productMapper = productMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiLocalProductVO>> list(@RequestParam(required = false) Long accountId,
                                                             @RequestParam(required = false) String status) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);
        Set<Long> bound = openAppService.getBoundAccountIds(app);

        LambdaQueryWrapper<LocalProduct> qw = new LambdaQueryWrapper<LocalProduct>()
                .orderByDesc(LocalProduct::getUpdatedAt);
        if (status != null && !status.isBlank()) qw.eq(LocalProduct::getStatus, status);

        List<OpenApiLocalProductVO> result = productMapper.selectList(qw).stream()
                .filter(p -> bound.isEmpty() || bound.contains(p.getAccountId()))
                .filter(p -> accountId == null || accountId.equals(p.getAccountId()))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiLocalProductVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        LocalProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "本地商品不存在");
        }
        openAppService.assertAccountAccessible(app, product.getAccountId());
        return OpenApiResponse.ok(toVo(product));
    }

    private OpenApiLocalProductVO toVo(LocalProduct p) {
        OpenApiLocalProductVO vo = new OpenApiLocalProductVO();
        vo.setId(p.getId());
        vo.setAccountId(p.getAccountId());
        vo.setTitle(p.getTitle());
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setStock(p.getStock());
        vo.setCategoryId(p.getCategoryId());
        vo.setDescription(p.getDescription());
        vo.setImages(p.getImages());
        vo.setVideos(p.getVideos());
        vo.setGoodsType(p.getGoodsType());
        vo.setDeliverType(p.getDeliverType());
        vo.setImageUrl(p.getImageUrl());
        vo.setStatus(p.getStatus());
        vo.setPublishError(p.getPublishError());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
