package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼个人信息 API 服务
 * 封装个人信息相关的 MTOP 接口调用
 */
public class XianyuProfileApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuProfileApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 获取当前登录用户信息 — mtop.taobao.idleuser.info.get */
    public JsonNode getUserInfo() {
        return apiClient.callMtop("mtop.taobao.idleuser.info.get", "{}");
    }

    /** 获取登录用户 IM 信息 — mtop.taobao.idlemessage.pc.loginuser.get */
    public JsonNode getLoginUserInfo() {
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.loginuser.get", "{}");
    }

    /** 获取用户主页数据 — mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get */
    public JsonNode getUserHomePage(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId != null ? userId : "");
        return apiClient.callMtop("mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get", toJson(data));
    }

    /**
     * 获取用户页面导航（轻量元数据）— 真实接口 mtop.idle.web.user.page.nav
     * <p>真实抓包验证（2026-07-18 CDP）：</p>
     * <ul>
     *   <li>data.module.base.purchaseCount / soldCount / followers / following / collectionCount / displayName / avatar</li>
     *   <li>data.needDecryptKeys 含 baseInfo.encryptedUserId（服务器端解密字段，HTTP 客户端不可见明文）</li>
     * </ul>
     */
    public JsonNode getUserPageNav() {
        return apiClient.callMtop("mtop.idle.web.user.page.nav", "{}");
    }

    /**
     * 获取用户主页详细数据 — 真实接口 mtop.idle.web.user.page.head
     * <p>真实抓包验证（2026-07-18 CDP）：</p>
     * <ul>
     *   <li>data: {"self":true}（self=true 拉自己主页，false 拉他人主页需带 userId）</li>
     *   <li>data.baseInfo.{encryptedUserId,kcUserId,self,tags,userType}（kcUserId 是数字 uid，可明文）</li>
     *   <li>data.module.shop.{level,score,reviewNum,businessQuality,nextLevelNeedScore,itemToppingLimit,superShow}</li>
     *   <li>data.module.social.{followStatus,followers,following}</li>
     *   <li>data.module.tabs.{item.number(在售宝贝数),rate.number(信用及评价数)}</li>
     *   <li>data.module.base.{displayName,avatar,ipLocation,introduction,ylzTags[]}</li>
     * </ul>
     *
     * @param self true 拉自己主页，false 拉他人主页（后者需在 data 里带 userId）
     */
    public JsonNode getUserPageHead(boolean self) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("self", self);
        return apiClient.callMtop("mtop.idle.web.user.page.head", toJson(data));
    }

    /**
     * 获取用户信用画像 — 真实接口 mtop.idle.web.user.page.head
     * <p>真实抓包验证（TempE2EVerifyTest 第10项验证通过）：
     * 返回 data.module.shop.{level, score, reviewNum, businessQuality, nextLevelNeedScore}
     * 和 data.module.tabs.rate.number(信用及评价数)。
     * 之前用的 mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get 是首页数据预热接口，
     * scene:"credit" 参数不被闲鱼识别，返回空数据。</p>
     *
     * @param userId 用户 ID（null 或空时查自己，self=true）
     */
    public JsonNode getUserCredit(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean isSelf = userId == null || userId.isBlank();
        data.put("self", isSelf);
        if (!isSelf) {
            data.put("userId", userId);
        }
        return apiClient.callMtop("mtop.idle.web.user.page.head", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
