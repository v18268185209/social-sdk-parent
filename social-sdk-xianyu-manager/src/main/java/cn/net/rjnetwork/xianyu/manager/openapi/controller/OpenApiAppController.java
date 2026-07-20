package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenAppCreateRequest;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenAppResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外应用管理（内部管理员接口，需管理员 JWT）。用于创建/查询对外应用凭证。
 */
@RestController
@RequestMapping("/api/openapi/apps")
@Hidden
public class OpenApiAppController {

    private final OpenAppService openAppService;

    public OpenApiAppController(OpenAppService openAppService) {
        this.openAppService = openAppService;
    }

    @PostMapping
    public OpenApiResponse<OpenAppResponse> create(@RequestBody OpenAppCreateRequest request) {
        return OpenApiResponse.ok(openAppService.createApp(request));
    }

    @GetMapping
    public OpenApiResponse<List<OpenAppResponse>> list() {
        return OpenApiResponse.ok(openAppService.listApps());
    }
}
