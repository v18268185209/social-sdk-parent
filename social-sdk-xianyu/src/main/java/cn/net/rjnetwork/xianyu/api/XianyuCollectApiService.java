package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼收藏/关注 API 服务
 * 封装收藏管理、关注管理、足迹等 MTOP 接口调用
 */
public class XianyuCollectApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuCollectApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取我的收藏列表 — 真实接口 mtop.taobao.idle.web.favor.item.list
     * <p>真实抓包验证（2026-07-18 CDP 导航到 https://www.goofish.com/collection）：</p>
     * <ul>
     *   <li>POST https://h5api.m.goofish.com/h5/mtop.taobao.idle.web.favor.item.list/1.0/</li>
     *   <li>data: {} （无业务参数，按 cookie 解身份）</li>
     *   <li>返回 data.items[] → 每项含 id/longItemId/price/originalPrice/itemStatus/categoryId/city/province/picUrl/imageUrls[]/favorTime/favorNum/collectNum/browseCount/commentNum/title</li>
     * </ul>
     */
    public JsonNode getMyCollectList(String pageNumber, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (pageNumber != null && !pageNumber.isBlank()) data.put("pageNumber", pageNumber);
        if (pageSize != null && !pageSize.isBlank()) data.put("pageSize", pageSize);
        return apiClient.callMtop("mtop.taobao.idle.web.favor.item.list", toJson(data));
    }

    /** 收藏商品 — 推测接口 mtop.taobao.idle.web.favor.item.add（命名规律候选） */
    public JsonNode collectItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.web.favor.item.add", toJson(data));
    }

    /** 取消收藏 — 推测接口 mtop.taobao.idle.web.favor.item.delete（命名规律候选） */
    public JsonNode uncollectItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.web.favor.item.delete", toJson(data));
    }

    /** 获取我的关注列表 — 推测接口 mtop.idle.web.user.follow.list（命名规律候选，未直接抓包验证） */
    public JsonNode getMyFollowList(String pageNumber, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (pageNumber != null && !pageNumber.isBlank()) data.put("pageNumber", pageNumber);
        if (pageSize != null && !pageSize.isBlank()) data.put("pageSize", pageSize);
        return apiClient.callMtop("mtop.idle.web.user.follow.list", toJson(data));
    }

    /** 关注用户/店铺 — 推测接口 mtop.idle.web.user.follow.add */
    public JsonNode followTarget(String targetId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetId", targetId != null ? targetId : "");
        return apiClient.callMtop("mtop.idle.web.user.follow.add", toJson(data));
    }

    /** 取消关注 — 推测接口 mtop.idle.web.user.follow.remove */
    public JsonNode unfollowTarget(String targetId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetId", targetId != null ? targetId : "");
        return apiClient.callMtop("mtop.idle.web.user.follow.remove", toJson(data));
    }

    /** 获取我的浏览足迹 — 推测接口 mtop.idle.web.user.footprint.list */
    public JsonNode getMyFootprint(String pageNumber, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (pageNumber != null && !pageNumber.isBlank()) data.put("pageNumber", pageNumber);
        if (pageSize != null && !pageSize.isBlank()) data.put("pageSize", pageSize);
        return apiClient.callMtop("mtop.idle.web.user.footprint.list", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
