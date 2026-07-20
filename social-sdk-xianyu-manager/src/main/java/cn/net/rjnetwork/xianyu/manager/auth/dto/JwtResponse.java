package cn.net.rjnetwork.xianyu.manager.auth.dto;

import lombok.Data;

@Data
public class JwtResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private AdminUserInfo user;

    @Data
    public static class AdminUserInfo {
        private Long id;
        private String username;
        private String displayName;
        private Integer roleLevel;
    }

    public static JwtResponse of(String token, long expiresIn, AdminUserInfo user) {
        JwtResponse response = new JwtResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(expiresIn);
        response.setUser(user);
        return response;
    }
}
