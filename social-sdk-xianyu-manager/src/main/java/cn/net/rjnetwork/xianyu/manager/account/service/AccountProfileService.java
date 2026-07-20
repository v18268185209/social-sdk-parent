^package cn.net.rjnetwork.xianyu.manager.account.service;

import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProfileApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账号个人信息服务
 * 通过闲鱼 SDK 获取实时个人信息并同步到数据库
 */
@Service
public class AccountProfileService {

    private final AccountMapper accountMapper;

    public AccountProfileService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 获取实时个人信息（每次都调用 API，不使用缓存）
     */
    public ProfileResult fetchRealTimeProfile(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return ProfileResult.error("账号不存在");
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            return ProfileResult.error("账号未设置 Cookie");
        }

        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuProfileApiService profileApi = new XianyuProfileApiService(mtopClient);
            XianyuLoginApiService loginApi = new XianyuLoginApiService(account.getCookieHeader());

            // 先通过 checkLoginStatus 获取 userId（这是拿到 userId 的最可靠方式）
            XianyuLoginApiService.LoginStatusResult loginResult = loginApi.checkLoginStatus(account.getCookieHeader());

            // 调用多个接口获取完整信息
            JsonNode navData = profileApi.getUserPageNav();
            JsonNode headData = profileApi.getUserPageHead(true);

            ProfileResult result = new ProfileResult();
            result.success = true;
            result.accountId = accountId;

            // 从登录状态获取 userId
            if (loginResult != null && loginResult.loggedIn) {
                result.userId = loginResult.userId;
                // 如果 checkLoginStatus 没返回头像和昵称，用这里的
                if (result.displayName == null && loginResult.nickname != null) {
                    result.displayName = loginResult.nickname;
                }
                if (result.avatar == null && loginResult.avatar != null) {
                    result.avatar = loginResult.avatar;
                }
            }

            // 解析 getUserPageNav 数据
            if (navData != null && navData.has("data")) {
                JsonNode data = navData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        result.displayName = getText(base, "displayName");
                        result.avatar = getText(base, "avatar");
                        result.purchaseCount = getInt(base, "purchaseCount");
                        result.soldCount = getInt(base, "soldCount");
                        result.followers = getInt(base, "followers");
                        result.following = getInt(base, "following");
                        result.collectionCount = getInt(base, "collectionCount");
                    }
                }
            }

            // 解析 getUserPageHead 数据
            if (headData != null && headData.has("data")) {
                JsonNode data = headData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        if (result.displayName == null) {
                            result.displayName = getText(base, "displayName");
                        }
                        if (result.avatar == null) {
                            result.avatar = getText(base, "avatar");
                        }
                        result.introduction = getText(base, "introduction");
                        result.ipLocation = getText(base, "ipLocation");
                    }
                    if (module.has("shop")) {
                        JsonNode shop = module.get("shop");
                        result.shopLevel = getText(shop, "level");
                        result.creditScore = getInt(shop, "score");
                        result.reviewNum = getInt(shop, "reviewNum");
                    }
                    if (module.has("tabs")) {
                        JsonNode tabs = module.get("tabs");
                        if (tabs.has("item")) {
                            result.onSaleCount = getInt(tabs.get("item"), "number");
                        }
                    }
                    if (module.has("social")) {
                        JsonNode social = module.get("social");
                        if (result.followers == null) {
                            result.followers = getInt(social, "followers");
                        }
                        if (result.following == null) {
                            result.following = getInt(social, "following");
                        }
                    }
                }
            }

            return result;
        } catch (Exception e) {
            return ProfileResult.error("获取个人信息失败: " + e.getMessage());
        }
    }

    /**
     * 同步个人信息到数据库
     */
    @Transactional
    public ProfileResult syncProfile(Long accountId) {
        ProfileResult result = fetchRealTimeProfile(accountId);
        if (!result.success) {
            return result;
        }

        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return ProfileResult.error("账号不存在");
        }

        // 更新字段
        if (result.userId != null) account.setUserId(result.userId);
        if (result.displayName != null) account.setDisplayName(result.displayName);
        if (result.avatar != null) account.setAvatar(result.avatar);
        if (result.introduction != null) account.setIntroduction(result.introduction);
        if (result.ipLocation != null) account.setIpLocation(result.ipLocation);
        if (result.followers != null) account.setFollowers(result.followers);
        if (result.following != null) account.setFollowing(result.following);
        if (result.soldCount != null) account.setSoldCount(result.soldCount);
        if (result.purchaseCount != null) account.setPurchaseCount(result.purchaseCount);
        if (result.collectionCount != null) account.setCollectionCount(result.collectionCount);
        if (result.onSaleCount != null) account.setOnSaleCount(result.onSaleCount);
        if (result.shopLevel != null) account.setShopLevel(result.shopLevel);
        if (result.creditScore != null) account.setCreditScore(result.creditScore);
        if (result.reviewNum != null) account.setReviewNum(result.reviewNum);
        account.setProfileSyncedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());

        accountMapper.updateById(account);

        result.syncedAt = account.getProfileSyncedAt();
        return result;
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asText();
    }

    private Integer getInt(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        return value.asInt();
    }

    /**
     * 个人信息结果
     */
    public static class ProfileResult {
        public boolean success;
        public String message;
        public Long accountId;
        public String userId;
        public String displayName;
        public String avatar;
        public String introduction;
        public String ipLocation;
        public Integer followers;
        public Integer following;
        public Integer soldCount;
        public Integer purchaseCount;
        public Integer collectionCount;
        public Integer onSaleCount;
        public String shopLevel;
        public Integer creditScore;
        public Integer reviewNum;
        public LocalDateTime syncedAt;

        public ProfileResult() {}

        public static ProfileResult error(String message) {
            ProfileResult r = new ProfileResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
