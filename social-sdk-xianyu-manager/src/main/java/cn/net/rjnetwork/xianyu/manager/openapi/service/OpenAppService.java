^package cn.net.rjnetwork.xianyu.manager.openapi.service;

import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiTokenCache;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenAppCreateRequest;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenAppResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.TokenRequest;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.TokenResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.mapper.OpenAppMapper;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 对外应用凭证管理 + 令牌签发/校验 + 账号作用域校验。
 */
@Service
public class OpenAppService {

    private final OpenAppMapper openAppMapper;
    private final CryptoUtil cryptoUtil;
    private final OpenApiTokenCache tokenCache;
    private final ObjectMapper objectMapper;

    public OpenAppService(OpenAppMapper openAppMapper,
                          CryptoUtil cryptoUtil,
                          OpenApiTokenCache tokenCache,
                          ObjectMapper objectMapper) {
        this.openAppMapper = openAppMapper;
        this.cryptoUtil = cryptoUtil;
        this.tokenCache = tokenCache;
        this.objectMapper = objectMapper;
    }

    /** 创建对外应用，返回明文 appSecret（仅此一次） */
    public OpenAppResponse createApp(OpenAppCreateRequest req) {
        if (req.getAppName() == null || req.getAppName().isBlank()) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.INVALID_PARAM, "appName 必填");
        }
        String appKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
        String appSecret = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");

        OpenApp app = new OpenApp();
        app.setAppName(req.getAppName());
        app.setAppKey(appKey);
        app.setAppSecretEnc(cryptoUtil.encrypt(appSecret));
        app.setStatus("ENABLED");
        app.setBoundAccountIds(toJsonArray(req.getBoundAccountIds()));
        app.setRateLimitPerMinute(req.getRateLimitPerMinute() != null ? req.getRateLimitPerMinute() : 60);
        openAppMapper.insert(app);

        OpenAppResponse resp = toResponse(app);
        resp.setAppSecret(appSecret); // 明文仅此刻返回
        return resp;
    }

    public List<OpenAppResponse> listApps() {
        List<OpenApp> apps = openAppMapper.selectList(new LambdaQueryWrapper<OpenApp>()
                .orderByDesc(OpenApp::getCreatedAt));
        return apps.stream().map(this::toResponse).toList();
    }

    public Optional<OpenApp> getByAppKey(String appKey) {
        return Optional.ofNullable(openAppMapper.selectOne(new LambdaQueryWrapper<OpenApp>()
                .eq(OpenApp::getAppKey, appKey)));
    }

    /** 校验 appKey + appSecret，签发 Bearer 令牌 */
    public TokenResponse issueToken(TokenRequest req) {
        OpenApp app = getByAppKey(req.getAppKey())
                .orElseThrow(() -> new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.UNAUTHORIZED));
        checkAppUsable(app);

        String plain = cryptoUtil.decrypt(app.getAppSecretEnc());
        if (!plain.equals(req.getAppSecret())) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.UNAUTHORIZED);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenCache.put(token, app.getId(), app.getAppKey());

        app.setLastUsedAt(LocalDateTime.now());
        openAppMapper.updateById(app);

        TokenResponse resp = new TokenResponse();
        resp.setAccessToken(token);
        resp.setExpiresIn(OpenApiTokenCache.TOKEN_TTL_SECONDS);
        return resp;
    }

    /** 令牌换应用（拦截器调用）。失败抛 OpenApiException。 */
    public OpenApp resolveToken(String token) {
        OpenApiTokenCache.TokenEntry entry = tokenCache.get(token);
        if (entry == null) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.INVALID_TOKEN);
        }
        OpenApp app = openAppMapper.selectById(entry.appId());
        if (app == null) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.INVALID_TOKEN);
        }
        checkAppUsable(app);
        return app;
    }

    public Set<Long> getBoundAccountIds(OpenApp app) {
        if (app.getBoundAccountIds() == null || app.getBoundAccountIds().isBlank()) {
            return Collections.emptySet();
        }
        try {
            List<Long> list = objectMapper.readValue(app.getBoundAccountIds(), new TypeReference<List<Long>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /** 账号作用域校验：accountId 必须 ∈ 绑定白名单（空名单=不限制） */
    public void assertAccountAccessible(OpenApp app, Long accountId) {
        if (accountId == null) {
            return;
        }
        Set<Long> bound = getBoundAccountIds(app);
        if (bound.isEmpty()) {
            return;
        }
        if (!bound.contains(accountId)) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.ACCOUNT_FORBIDDEN);
        }
    }

    private void checkAppUsable(OpenApp app) {
        if (!"ENABLED".equals(app.getStatus())) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.APP_DISABLED);
        }
        if (app.getExpireAt() != null && app.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new OpenApiException(cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode.APP_EXPIRED);
        }
    }

    private String toJsonArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return null;
        }
    }

    private OpenAppResponse toResponse(OpenApp app) {
        OpenAppResponse resp = new OpenAppResponse();
        resp.setId(app.getId());
        resp.setAppName(app.getAppName());
        resp.setAppKey(app.getAppKey());
        resp.setStatus(app.getStatus());
        resp.setBoundAccountIds(app.getBoundAccountIds() == null || app.getBoundAccountIds().isBlank()
                ? Collections.emptyList()
                : safeParse(app.getBoundAccountIds()));
        resp.setRateLimitPerMinute(app.getRateLimitPerMinute());
        resp.setExpireAt(app.getExpireAt());
        resp.setCreatedAt(app.getCreatedAt());
        return resp;
    }

    private List<Long> safeParse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
