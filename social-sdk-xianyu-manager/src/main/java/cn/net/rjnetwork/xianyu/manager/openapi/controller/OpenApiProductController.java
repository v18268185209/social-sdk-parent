package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiProductVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 商品域对外接口：列表按绑定白名单过滤，详情做账号作用域校验，响应脱敏。
 */
@RestController
@RequestMapping("/openapi/v1/products")
public class OpenApiProductController {

    private final ProductMapper productMapper;
    private final OpenAppService openAppService;

    public OpenApiProductController(ProductMapper productMapper, OpenAppService openAppService) {
        this.productMapper = productMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiProductVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiProductVO> result = productMapper.selectList(new LambdaQueryWrapper<XianyuProduct>()).stream()
                .filter(p -> bound.isEmpty() || bound.contains(p.getAccountId()))
                .filter(p -> accountId == null || p.getAccountId().equals(accountId))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiProductVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "商品不存在");
        }
        openAppService.assertAccountAccessible(app, product.getAccountId());
        return OpenApiResponse.ok(toVo(product));
    }

    private OpenApiProductVO toVo(XianyuProduct p) {
        OpenApiProductVO vo = new OpenApiProductVO();
        vo.setId(p.getId());
        vo.setAccountId(p.getAccountId());
        vo.setItemId(p.getItemId());
        vo.setTitle(p.getTitle());
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setStock(p.getStock());
        vo.setStatus(p.getStatus());
        vo.setCategoryId(p.getCategoryId());
        vo.setImages(p.getImages());
        vo.setDescription(p.getDescription());
        vo.setVideos(p.getVideos());
        vo.setGoodsType(p.getGoodsType());
        vo.setDeliverType(p.getDeliverType());
        vo.setDetailUrl(p.getDetailUrl());
        vo.setImageUrl(p.getImageUrl());
        vo.setViewCount(p.getViewCount());
        vo.setFavoriteCount(p.getFavoriteCount());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
