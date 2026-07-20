^package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.TokenRequest;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.TokenResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外令牌签发（公开，无需 Bearer）。该端点由拦截器注册处排除。
 */
@RestController
@RequestMapping("/openapi/v1/oauth")
public class OpenApiAuthController {

    private final OpenAppService openAppService;

    public OpenApiAuthController(OpenAppService openAppService) {
        this.openAppService = openAppService;
    }

    @PostMapping("/token")
    public OpenApiResponse<TokenResponse> token(@RequestBody TokenRequest request) {
        return OpenApiResponse.ok(openAppService.issueToken(request));
    }
}
