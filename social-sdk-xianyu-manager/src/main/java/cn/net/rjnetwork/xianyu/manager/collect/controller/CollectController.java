package cn.net.rjnetwork.xianyu.manager.collect.controller;

import cn.net.rjnetwork.xianyu.manager.collect.model.XianyuCollect;
import cn.net.rjnetwork.xianyu.manager.collect.service.CollectService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collect")
public class CollectController {

    private static final Logger log = LoggerFactory.getLogger(CollectController.class);

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

    /**
     * 搜索闲鱼商品 — 用于"添加收藏"弹窗里的关键词搜索
     */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, String>>> search(
            @RequestParam Long accountId,
            @RequestParam String keyword) {
        try {
            List<Map<String, String>> results = collectService.searchItems(accountId, keyword);
            return ApiResponse.ok(results);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[COLLECT] search failed", e);
            return ApiResponse.error("SEARCH_ERROR", "搜索失败: " + e.getMessage());
        }
    }

    /**
     * 添加收藏：调闲鱼 SDK 真正收藏，同时落本地记录
     */
    @PostMapping
    public ApiResponse<?> add(@RequestBody XianyuCollect collect) {
        if (collect.getAccountId() == null) {
            return ApiResponse.badRequest("accountId 不能为空");
        }
        if (collect.getTargetId() == null || collect.getTargetId().isBlank()) {
            return ApiResponse.badRequest("targetId 不能为空");
        }
        try {
            XianyuCollect result = collectService.collectAndSync(
                    collect.getAccountId(), collect.getTargetId(),
                    collect.getTargetName() != null ? collect.getTargetName() : "");
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error("COLLECT_FAIL", e.getMessage());
        } catch (Exception e) {
            log.error("[COLLECT] add failed", e);
            return ApiResponse.error("COLLECT_ERROR", "收藏失败: " + e.getMessage());
        }
    }

    /**
     * 移除收藏：调闲鱼 SDK 取消收藏，同时删本地记录
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        try {
            XianyuCollect collect = collectService.getById(id);
            if (collect == null) {
                return ApiResponse.badRequest("收藏记录不存在");
            }
            collectService.uncollectAndSync(collect.getAccountId(), collect.getTargetId());
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error("UNCOLLECT_FAIL", e.getMessage());
        } catch (Exception e) {
            log.error("[COLLECT] remove failed, id={}", id, e);
            return ApiResponse.error("UNCOLLECT_ERROR", "取消收藏失败: " + e.getMessage());
        }
    }

    /**
     * 从闲鱼同步收藏列表（按账号拉取最新收藏）
     */
    @PostMapping("/sync")
    public ApiResponse<Integer> sync(@RequestParam Long accountId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "50") int pageSize) {
        try {
            int synced = collectService.syncFromXianyu(accountId, page, pageSize);
            return ApiResponse.ok(synced);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[COLLECT] sync failed, accountId={}", accountId, e);
            // 通过返回值传递错误信息
            ApiResponse<Integer> resp = ApiResponse.error("SYNC_ERROR", "同步失败: " + e.getMessage());
            resp.setData(0);
            return resp;
        }
    }
}
