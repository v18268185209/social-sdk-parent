package cn.net.rjnetwork.xianyu.manager.collect.controller;

import cn.net.rjnetwork.xianyu.manager.collect.model.XianyuCollect;
import cn.net.rjnetwork.xianyu.manager.collect.service.CollectService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collect")
public class CollectController {

    private final CollectService collectService;

    public CollectController(CollectService collectService) {
        this.collectService = collectService;
    }

    @GetMapping
    public ApiResponse<List<XianyuCollect>> list(
            @RequestParam Long accountId,
            @RequestParam(required = false) String targetType) {
        return ApiResponse.ok(collectService.list(accountId, targetType));
    }

    @PostMapping
    public ApiResponse<XianyuCollect> add(@RequestBody XianyuCollect collect) {
        collectService.add(collect);
        return ApiResponse.ok(collect);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        collectService.remove(id);
        return ApiResponse.ok(null);
    }
}
