package cn.net.rjnetwork.xianyu.manager.openapi.dto;

import lombok.Data;

@Data
public class TokenResponse {

    /** Bearer 令牌 */
    private String accessToken;

    private String tokenType = "Bearer";

    /** 有效期（秒） */
    private long expiresIn;
}
