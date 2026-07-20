package cn.net.rjnetwork.xianyu.manager.notify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通道级速率限制（令牌桶）。每个通道一个桶，按每分钟令牌数均匀补充。
 * limit <= 0 表示不限制。令牌数上限 = 每分钟限额，避免突发。
 */
@Component
public class SendRateLimiter {

    @Value("${notify.rate-limit-per-minute:60}")
    private int defaultPerMinute;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试获取一个发送令牌。
     * @param channelId    通道 ID
     * @param channelLimit 通道级每分钟上限（0 或未配置则用全局默认）
     */
    public boolean tryAcquire(long channelId, int channelLimit) {
        int limit = channelLimit > 0 ? channelLimit : defaultPerMinute;
        if (limit <= 0) return true; // 不限制
        TokenBucket b = buckets.computeIfAbsent("ch:" + channelId, k -> new TokenBucket(limit));
        return b.tryAcquire();
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastNanos;

        TokenBucket(int perMinute) {
            this.capacity = perMinute;
            this.refillPerSecond = perMinute / 60.0;
            this.tokens = capacity;
            this.lastNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            double elapsedSec = (now - lastNanos) / 1_000_000_000.0;
            if (elapsedSec > 0) {
                tokens = Math.min(capacity, tokens + elapsedSec * refillPerSecond);
                lastNanos = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
