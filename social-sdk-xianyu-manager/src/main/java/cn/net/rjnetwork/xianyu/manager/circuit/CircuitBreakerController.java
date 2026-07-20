package cn.net.rjnetwork.xianyu.manager.circuit;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 熔断器管理 API
 */
@RestController
@RequestMapping("/api/circuit-breaker")
public class CircuitBreakerController {

    private final CircuitBreakerService service;

    public CircuitBreakerController(CircuitBreakerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CircuitBreaker>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/{accountId}/{serviceName}")
    public ApiResponse<CircuitBreaker> get(@PathVariable Long accountId, @PathVariable String serviceName) {
        return ApiResponse.ok(service.get(accountId, serviceName));
    }

    @PostMapping("/{accountId}/{serviceName}/reset")
    public ApiResponse<String> reset(@PathVariable Long accountId, @PathVariable String serviceName) {
        service.reset(accountId, serviceName);
        return ApiResponse.ok("Circuit breaker reset");
    }

    @PostMapping("/global/{serviceName}/reset")
    public ApiResponse<String> resetGlobal(@PathVariable String serviceName) {
        service.reset(null, serviceName);
        return ApiResponse.ok("Global circuit breaker reset");
    }
}
