package cn.net.rjnetwork.xianyu.manager.rule.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleCreateRequest;
import cn.net.rjnetwork.xianyu.manager.rule.dto.RuleTestRequest;
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
    public ApiResponse<List<XianyuKeywordRule>> list(@RequestParam Long accountId) {
        return ApiResponse.ok(ruleService.listRules(accountId));
    }

    @PostMapping
    public ApiResponse<XianyuKeywordRule> create(@RequestBody RuleCreateRequest request) {
        return ApiResponse.ok(ruleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<XianyuKeywordRule> update(@PathVariable Long id, @RequestBody RuleCreateRequest request) {
        return ApiResponse.ok(ruleService.update(id, request));
    }

    @DeleteMapping("/{id}")
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
}
