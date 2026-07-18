package cn.net.rjnetwork.starter.platform.xianyu.controller;

import cn.net.rjnetwork.starter.platform.common.model.StarterApiResponse;
import cn.net.rjnetwork.starter.platform.common.exception.StarterApiException;
import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountCookieLoginRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountCookieUpdateRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.KeywordRuleUpsertRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.MessageSendRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.ProductUpsertRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.RuleMatchRequest;
import cn.net.rjnetwork.starter.platform.xianyu.service.XianyuConsoleService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/social-sdk/xianyu")
public class XianyuConsoleController {

    private final XianyuConsoleService service;

    public XianyuConsoleController(XianyuConsoleService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public StarterApiResponse<?> health() {
        return StarterApiResponse.ok(service.health());
    }

    @GetMapping("/accounts")
    public StarterApiResponse<?> listAccounts() {
        return StarterApiResponse.ok(service.listAccounts());
    }

    @GetMapping("/accounts/{id}")
    public StarterApiResponse<?> getAccount(@PathVariable("id") long id) {
        return service.getAccount(id)
                .<StarterApiResponse<?>>map(StarterApiResponse::ok)
                .orElseGet(() -> StarterApiResponse.fail("NOT_FOUND", "account not found: " + id));
    }

    @PostMapping("/accounts/login")
    public StarterApiResponse<?> login(@RequestBody AccountCookieLoginRequest request) {
        try {
            return StarterApiResponse.ok(service.loginWithCookies(request));
        } catch (Exception e) {
            throw new StarterApiException("LOGIN_FAILED", e.getMessage(), e);
        }
    }

    @PutMapping("/accounts/{id}/cookies")
    public StarterApiResponse<?> updateCookies(
            @PathVariable("id") long id,
            @RequestBody(required = false) AccountCookieUpdateRequest request) {
        return execute("UPDATE_COOKIES_FAILED", () -> service.updateAccountCookies(id, request));
    }

    @GetMapping("/accounts/{id}/profile")
    public StarterApiResponse<?> refreshProfile(@PathVariable("id") long id) {
        return execute("PROFILE_REFRESH_FAILED", () -> service.refreshAccountProfile(id));
    }

    @PutMapping("/accounts/{id}/status")
    public StarterApiResponse<?> updateAccountStatus(
            @PathVariable("id") long id,
            @RequestBody AccountStatusUpdateRequest request) {
        return execute("UPDATE_STATUS_FAILED", () -> service.updateAccountStatus(id, request));
    }

    @DeleteMapping("/accounts/{id}")
    public StarterApiResponse<?> deleteAccount(@PathVariable("id") long id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deleted", service.deleteAccount(id));
        payload.put("id", id);
        return StarterApiResponse.ok(payload);
    }

    @PostMapping("/messages/send")
    public StarterApiResponse<?> sendMessage(@RequestBody MessageSendRequest request) {
        return execute("SEND_MESSAGE_FAILED", () -> service.sendMessage(request));
    }

    @GetMapping("/messages/timeline/{accountId}")
    public StarterApiResponse<?> timeline(
            @PathVariable("accountId") long accountId,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return execute("TIMELINE_FAILED", () -> service.getTimeline(accountId, limit));
    }

    @GetMapping("/products")
    public StarterApiResponse<?> listProducts(@RequestParam(value = "accountId", required = false) Long accountId) {
        return StarterApiResponse.ok(service.listProducts(accountId));
    }

    @PostMapping("/products")
    public StarterApiResponse<?> createProduct(@RequestBody ProductUpsertRequest request) {
        return execute("CREATE_PRODUCT_FAILED", () -> service.createProduct(request));
    }

    @PutMapping("/products/{id}")
    public StarterApiResponse<?> updateProduct(
            @PathVariable("id") long id,
            @RequestBody ProductUpsertRequest request) {
        return execute("UPDATE_PRODUCT_FAILED", () -> service.updateProduct(id, request));
    }

    @DeleteMapping("/products/{id}")
    public StarterApiResponse<?> deleteProduct(@PathVariable("id") long id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deleted", service.deleteProduct(id));
        payload.put("id", id);
        return StarterApiResponse.ok(payload);
    }

    @GetMapping("/rules")
    public StarterApiResponse<?> listRules(@RequestParam(value = "accountId", required = false) Long accountId) {
        return StarterApiResponse.ok(service.listRules(accountId));
    }

    @PostMapping("/rules")
    public StarterApiResponse<?> createRule(@RequestBody KeywordRuleUpsertRequest request) {
        return execute("CREATE_RULE_FAILED", () -> service.createRule(request));
    }

    @PutMapping("/rules/{id}")
    public StarterApiResponse<?> updateRule(
            @PathVariable("id") long id,
            @RequestBody KeywordRuleUpsertRequest request) {
        return execute("UPDATE_RULE_FAILED", () -> service.updateRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public StarterApiResponse<?> deleteRule(@PathVariable("id") long id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deleted", service.deleteRule(id));
        payload.put("id", id);
        return StarterApiResponse.ok(payload);
    }

    @PostMapping("/rules/match")
    public StarterApiResponse<?> matchRule(@RequestBody RuleMatchRequest request) {
        return execute("RULE_MATCH_FAILED", () -> service.matchRule(request));
    }

    private StarterApiResponse<?> execute(String code, CheckedSupplier supplier) {
        try {
            return StarterApiResponse.ok(supplier.get());
        } catch (Exception e) {
            throw new StarterApiException(code, e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier {
        Object get() throws Exception;
    }
}
