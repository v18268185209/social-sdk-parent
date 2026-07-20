^package cn.net.rjnetwork.xianyu.manager.openapi.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 对外访问令牌缓存（Caffeine）。
 * token -> (appId, appKey)，写入后按 TTL 自动过期。单机内存实现，足够单实例对外 API 使用。
 */
@Component
public class OpenApiTokenCache {

    /** token 有效期（秒），2 小时 */
    public static final long TOKEN_TTL_SECONDS = 7200;

    private final Cache<String, TokenEntry> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(TOKEN_TTL_SECONDS))
            .maximumSize(10_000)
            .build();

    public void put(String token, long appId, String appKey) {
        cache.put(token, new TokenEntry(appId, appKey));
    }

    public TokenEntry get(String token) {
        return cache.getIfPresent(token);
    }

    public void invalidate(String token) {
        cache.invalidate(token);
    }

    public record TokenEntry(long appId, String appKey) {
    }
}
