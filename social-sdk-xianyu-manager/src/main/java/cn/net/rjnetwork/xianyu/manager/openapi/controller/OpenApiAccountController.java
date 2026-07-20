^package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiAccountVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 账号域对外接口（闭环示例）：列表按绑定白名单过滤，详情做账号作用域校验，响应脱敏。
 */
@RestController
@RequestMapping("/openapi/v1/accounts")
public class OpenApiAccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final OpenAppService openAppService;

    public OpenApiAccountController(AccountService accountService,
                                    AccountMapper accountMapper,
                                    OpenAppService openAppService) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiAccountVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        Set<Long> bound = openAppService.getBoundAccountIds(app);
        List<OpenApiAccountVO> result = accountService.listAll().stream()
                .filter(a -> bound.isEmpty() || bound.contains(a.getId()))
                .filter(a -> accountId == null || a.getId().equals(accountId))
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiAccountVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, id);

        XianyuAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "账号不存在");
        }
        return OpenApiResponse.ok(toVo(account));
    }

    private OpenApiAccountVO toVo(XianyuAccount a) {
        OpenApiAccountVO vo = new OpenApiAccountVO();
        vo.setId(a.getId());
        vo.setAccountName(a.getAccountName());
        vo.setUserId(a.getUserId());
        vo.setDisplayName(a.getDisplayName());
        vo.setStatus(a.getStatus());
        vo.setRemark(a.getRemark());
        vo.setAvatar(a.getAvatar());
        vo.setIpLocation(a.getIpLocation());
        vo.setFollowers(a.getFollowers());
        vo.setFollowing(a.getFollowing());
        vo.setSoldCount(a.getSoldCount());
        vo.setPurchaseCount(a.getPurchaseCount());
        vo.setCollectionCount(a.getCollectionCount());
        vo.setOnSaleCount(a.getOnSaleCount());
        vo.setShopLevel(a.getShopLevel());
        vo.setCreditScore(a.getCreditScore());
        vo.setReviewNum(a.getReviewNum());
        vo.setLastLoginAt(a.getLastLoginAt());
        vo.setProfileSyncedAt(a.getProfileSyncedAt());
        return vo;
    }
}
