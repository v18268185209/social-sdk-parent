package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiBuyerProfileVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.buyer.mapper.BuyerProfileMapper;
import cn.net.rjnetwork.xianyu.manager.buyer.model.BuyerProfile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 买家画像域对外接口：列表按绑定白名单过滤（firstAccountId），详情做账号作用域校验。
 */
@RestController
@RequestMapping("/openapi/v1/buyer")
public class OpenApiBuyerController {

    private final BuyerProfileMapper buyerProfileMapper;
    private final OpenAppService openAppService;

    public OpenApiBuyerController(BuyerProfileMapper buyerProfileMapper, OpenAppService openAppService) {
        this.buyerProfileMapper = buyerProfileMapper;
        this.openAppService = openAppService;
    }

    @GetMapping("/profiles")
    public OpenApiResponse<List<OpenApiBuyerProfileVO>> listProfiles(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiBuyerProfileVO> result = buyerProfileMapper.selectList(new LambdaQueryWrapper<BuyerProfile>()
                        .orderByDesc(BuyerProfile::getUpdatedAt))
                .stream()
                .filter(p -> bound.isEmpty() || bound.contains(p.getFirstAccountId()))
                .filter(p -> accountId == null || accountId.equals(p.getFirstAccountId()))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/profiles/{id}")
    public OpenApiResponse<OpenApiBuyerProfileVO> getProfile(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        BuyerProfile profile = buyerProfileMapper.selectById(id);
        if (profile == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "买家画像不存在");
        }
        openAppService.assertAccountAccessible(app, profile.getFirstAccountId());
        return OpenApiResponse.ok(toVo(profile));
    }

    private OpenApiBuyerProfileVO toVo(BuyerProfile p) {
        OpenApiBuyerProfileVO vo = new OpenApiBuyerProfileVO();
        vo.setId(p.getId());
        vo.setBuyerId(p.getBuyerId());
        vo.setFirstAccountId(p.getFirstAccountId());
        vo.setNickname(p.getNickname());
        vo.setAvatar(p.getAvatar());
        vo.setTotalSessions(p.getTotalSessions());
        vo.setTotalMessages(p.getTotalMessages());
        vo.setTotalOrders(p.getTotalOrders());
        vo.setTotalSpent(p.getTotalSpent());
        vo.setBargainCount(p.getBargainCount());
        vo.setAvgResponseSeconds(p.getAvgResponseSeconds());
        vo.setCredibilityScore(p.getCredibilityScore());
        vo.setTags(p.getTags());
        vo.setNotes(p.getNotes());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
