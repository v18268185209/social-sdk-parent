package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼收藏/关注 API 服务
 * 封装收藏管理、关注管理、足迹等 MTOP 接口调用
 *
 * <p>这些接口在 CDP 分析中通过页面导航（goofish.com/collect、goofish.com/follow）触发时被捕获。</p>
 */
public class XianyuCollectApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuCollectApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取我的收藏列表
     * API: mtop.taobao.idlecollect.my.collect.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 收藏列表 JSON
     */
    public JsonNode getMyCollectList(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecollect.my.collect.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 收藏商品
     * API: mtop.taobao.idlecollect.item.collect
     *
     * @param itemId 商品 ID
     * @return 收藏结果 JSON
     */
    public JsonNode collectItem(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecollect.item.collect")
                .addParam("itemId", itemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 取消收藏
     * API: mtop.taobao.idlecollect.item.uncollect
     *
     * @param itemId 商品 ID
     * @return 取消收藏结果 JSON
     */
    public JsonNode uncollectItem(String itemId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecollect.item.uncollect")
                .addParam("itemId", itemId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取我的关注列表（关注的人/店铺）
     * API: mtop.taobao.idlefollow.follow.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 关注列表 JSON
     */
    public JsonNode getMyFollowList(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlefollow.follow.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 关注用户/店铺
     * API: mtop.taobao.idlefollow.follow.add
     *
     * @param targetId 目标用户/店铺 ID
     * @return 关注结果 JSON
     */
    public JsonNode followTarget(String targetId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlefollow.follow.add")
                .addParam("targetId", targetId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 取消关注
     * API: mtop.taobao.idlefollow.follow.remove
     *
     * @param targetId 目标用户/店铺 ID
     * @return 取消关注结果 JSON
     */
    public JsonNode unfollowTarget(String targetId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlefollow.follow.remove")
                .addParam("targetId", targetId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取我的浏览足迹
     * API: mtop.taobao.idlefootprint.my.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 足迹列表 JSON
     */
    public JsonNode getMyFootprint(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlefootprint.my.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
