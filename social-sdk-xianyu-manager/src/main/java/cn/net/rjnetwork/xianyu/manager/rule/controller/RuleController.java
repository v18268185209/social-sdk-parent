package cn.net.rjnetwork.xianyu.manager.rule.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleCreateRequest;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleTestRequest;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuAutoReplyConfig;
import cn.net.rjnetwork.xianyu.manager.rule.model.XianyuKeywordRule;
import cn.net.rjnetwork.xianyu.manager.rule.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public ApiResponse<List<XianyuKeywordRule>> list(
            @RequestParam Long accountId,
            @RequestParam(required = false) String replyType) {
        List<XianyuKeywordRule> rules = ruleService.listRules(accountId);
        // 按 replyType 过滤
        if (replyType != null && !replyType.isEmpty()) {
            rules.removeIf(r -> !replyType.equals(r.getReplyType()));
        }
        return ApiResponse.ok(rules);
    }

    @PostMapping
    @Audit("创建规则")
    public ApiResponse<XianyuKeywordRule> create(@RequestBody RuleCreateRequest request) {
        return ApiResponse.ok(ruleService.create(request));
    }

    @PutMapping("/{id}")
    @Audit("更新规则")
    public ApiResponse<XianyuKeywordRule> update(@PathVariable Long id, @RequestBody RuleCreateRequest request) {
        return ApiResponse.ok(ruleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Audit("删除规则")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        ruleService.toggleEnabled(id, enabled);
        return ApiResponse.ok(null);
    }

    @PostMapping("/test")
    public ApiResponse<Boolean> test(@RequestBody RuleTestRequest request) {
        return ApiResponse.ok(ruleService.testMatch(request));
    }

    @PostMapping("/auto-reply")
    public ApiResponse<Map<String, Object>> autoReply(@RequestParam Long accountId, @RequestParam String message) {
        String reply = ruleService.autoReply(accountId, message);
        if (reply != null) {
            return ApiResponse.ok(Map.of("matched", true, "reply", reply));
        }
        return ApiResponse.ok(Map.of("matched", false));
    }

    /**
     * 获取账号的 AI/自动回复配置
     */
    @GetMapping("/config/{accountId}")
    public ApiResponse<XianyuAutoReplyConfig> getConfig(@PathVariable Long accountId) {
        return ApiResponse.ok(ruleService.getAutoReplyConfig(accountId));
    }

    /**
     * 保存账号的 AI/自动回复配置
     */
    @PostMapping("/config/{accountId}")
    public ApiResponse<XianyuAutoReplyConfig> saveConfig(
            @PathVariable Long accountId,
            @RequestBody XianyuAutoReplyConfig config) {
        return ApiResponse.ok(ruleService.saveAutoReplyConfig(accountId, config));
    }
}
