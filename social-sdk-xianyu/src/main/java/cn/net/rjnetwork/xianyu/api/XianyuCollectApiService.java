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

    /** 获取我的收藏列表 — mtop.taobao.idlecollect.my.collect.list */
    public JsonNode getMyCollectList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlecollect.my.collect.list", toJson(data));
    }

    /** 收藏商品 — mtop.taobao.idlecollect.item.collect */
    public JsonNode collectItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlecollect.item.collect", toJson(data));
    }

    /** 取消收藏 — mtop.taobao.idlecollect.item.uncollect */
    public JsonNode uncollectItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlecollect.item.uncollect", toJson(data));
    }

    /** 获取我的关注列表 — mtop.taobao.idlefollow.follow.list */
    public JsonNode getMyFollowList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlefollow.follow.list", toJson(data));
    }

    /** 关注用户/店铺 — mtop.taobao.idlefollow.follow.add */
    public JsonNode followTarget(String targetId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetId", targetId != null ? targetId : "");
        return apiClient.callMtop("mtop.taobao.idlefollow.follow.add", toJson(data));
    }

    /** 取消关注 — mtop.taobao.idlefollow.follow.remove */
    public JsonNode unfollowTarget(String targetId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetId", targetId != null ? targetId : "");
        return apiClient.callMtop("mtop.taobao.idlefollow.follow.remove", toJson(data));
    }

    /** 获取我的浏览足迹 — mtop.taobao.idlefootprint.my.list */
    public JsonNode getMyFootprint(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlefootprint.my.list", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
