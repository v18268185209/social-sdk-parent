package cn.net.rjnetwork.xianyu.manager.auth.controller;

import cn.net.rjnetwork.xianyu.manager.auth.dto.JwtResponse;
import cn.net.rjnetwork.xianyu.manager.auth.dto.LoginRequest;
import cn.net.rjnetwork.xianyu.manager.auth.model.AdminUser;
import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtResponse response = authService.login(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile(@RequestHeader("Authorization") String authHeader) {
        // 从 JWT 中解析用户信息，这里简化处理
        String username = authHeader.substring(7); // 去掉 "Bearer " 前缀
        AdminUser user = authService.findByUsername(username).orElse(null);
        if (user == null) {
            return ApiResponse.fail("USER_NOT_FOUND", "User not found");
        }
        Map<String, Object> data = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "roleLevel", user.getRoleLevel()
        );
        return ApiResponse.ok(data);
    }
}
