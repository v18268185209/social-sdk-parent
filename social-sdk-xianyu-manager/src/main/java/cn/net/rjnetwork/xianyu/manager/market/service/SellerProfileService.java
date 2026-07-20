package cn.net.rjnetwork.xianyu.manager.market.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProfileApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.market.mapper.SellerProfileMapper;
import cn.net.rjnetwork.xianyu.manager.market.model.SellerProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 卖家画像服务 — 抓取非自有卖家信息
 */
@Service
public class SellerProfileService {

    private static final Logger logger = LoggerFactory.getLogger(SellerProfileService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SellerProfileMapper mapper;
    private final AccountMapper accountMapper;

    public SellerProfileService(SellerProfileMapper mapper, AccountMapper accountMapper) {
        this.mapper = mapper;
        this.accountMapper = accountMapper;
    }

    /** 抓取卖家画像（通过任一可用账号的 cookie） */
    public SellerProfile fetchSellerProfile(String userId) {
        // 找一个可用账号
        List<XianyuAccount> accounts = accountMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuAccount>()
                        .eq(XianyuAccount::getDeleted, 0)
                        .isNotNull(XianyuAccount::getCookieHeader)
                        .ne(XianyuAccount::getCookieHeader, "")
                        .last("LIMIT 1"));

        if (accounts.isEmpty()) {
            logger.warn("无可用账号抓取卖家画像");
            return null;
        }

        XianyuAccount account = accounts.get(0);
        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuProfileApiService profileApi = new XianyuProfileApiService(mtopClient);

            // 用 getUserPageHead 获取卖家信息（传 userId）
            JsonNode headData = profileApi.getUserPageHead(true);

            SellerProfile profile = new SellerProfile();
            profile.setUserId(userId);

            if (headData != null && headData.has("data")) {
                JsonNode data = headData.get("data");
                if (data.has("module")) {
                    JsonNode module = data.get("module");
                    if (module.has("base")) {
                        JsonNode base = module.get("base");
                        profile.setNickname(getText(base, "displayName"));
                        profile.setAvatar(getText(base, "avatar"));
                        profile.setIntroduction(getText(base, "introduction"));
                        profile.setIpLocation(getText(base, "ipLocation"));
                    }
                    if (module.has("shop")) {
                        JsonNode shop = module.get("shop");
                        profile.setShopLevel(getText(shop, "level"));
                        profile.setCreditScore(getInt(shop, "score"));
                    }
                    if (module.has("social")) {
                        JsonNode social = module.get("social");
                        profile.setFollowers(getInt(social, "followers"));
                        profile.setFollowing(getInt(social, "following"));
                    }
                    if (module.has("tabs")) {
                        JsonNode tabs = module.get("tabs");
                        if (tabs.has("item")) {
                            profile.setOnSaleCount(getInt(tabs.get("item"), "number"));
                        }
                    }
                }
            }

            profile.setProfileSyncedAt(LocalDateTime.now());
            profile.setDeleted(0);

            // upsert
            SellerProfile existing = mapper.selectByUserId(userId);
            if (existing != null) {
                profile.setId(existing.getId());
                profile.setCreatedAt(existing.getCreatedAt());
                profile.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(profile);
            } else {
                mapper.insert(profile);
            }

            return profile;
        } catch (Exception e) {
            logger.error("抓取卖家画像失败 userId={}", userId, e);
            return null;
        }
    }

    public SellerProfile getByUserId(String userId) {
        return mapper.selectByUserId(userId);
    }

    public List<SellerProfile> search(String keyword) {
        return mapper.search(keyword);
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        return value.isNull() ? null : value.asText();
    }

    private Integer getInt(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        return value.isNull() ? null : value.asInt();
    }
}
