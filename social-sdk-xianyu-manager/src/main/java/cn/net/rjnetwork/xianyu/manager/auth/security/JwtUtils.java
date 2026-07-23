package cn.net.rjnetwork.xianyu.manager.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    /** HS256 起码 256 bits = 32 字节；HS512 起 64 字节。兜底用 64 字节稳。 */
    private static final int MIN_KEY_BYTES = 32;
    private static final int SAFE_KEY_BYTES = 64;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 构造签名密钥。jjwt 0.12+ 强制 HMAC-SHA key 必须 >= 256 bits，
     * 否则抛 WeakKeyException。开发默认值或线上配的短 secret 都可能不足 32 字节，
     * 这里兜底：短了就用 SHA-512 派生到 64 字节，够长直接用原字节。
     * 改 secret 后已签发的旧 token 会失效（业务可接受：等于强制重新登录）。
     */
    private SecretKey getSigningKey() {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = raw.length >= MIN_KEY_BYTES ? raw : deriveTo(raw, SAFE_KEY_BYTES);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 用 SHA-512 把任意长度 seed 派生到至少 len 字节（HS512 用 64 字节） */
    private static byte[] deriveTo(byte[] seed, int len) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-512");
            byte[] out = new byte[len];
            int offset = 0;
            byte[] block = seed;
            while (offset < len) {
                byte[] digest = sha.digest(block);
                int copy = Math.min(digest.length, len - offset);
                System.arraycopy(digest, 0, out, offset, copy);
                offset += copy;
                // 续块：hash(hash) 迭代，避免短 seed 周期回环
                block = sha.digest(digest);
            }
            return out;
        } catch (Exception e) {
            // SHA-512 是 JRE 必备算法，不会丢；兜底用 seed 复制到够长
            byte[] out = new byte[len];
            for (int i = 0; i < len; i++) {
                out[i] = seed[i % seed.length];
            }
            return out;
        }
    }

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "admin");
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpiration() {
        return expiration;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
