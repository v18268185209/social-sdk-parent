package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼个人信息 API 服务
 * 封装个人信息相关的 MTOP 接口调用
 */
public class XianyuProfileApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuProfileApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取当前登录用户信息
     * API: mtop.taobao.idleuser.info.get
     */
    public JsonNode getUserInfo() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleuser.info.get")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取登录用户 IM 信息
     * API: mtop.taobao.idlemessage.pc.loginuser.get
     */
    public JsonNode getLoginUserInfo() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlemessage.pc.loginuser.get")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取用户主页数据
     * API: mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get
     */
    public JsonNode getUserHomePage(String userId) {
        String url = new XianyuMtopRequestBuilder("mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get")
                .addParam("userId", userId != null ? userId : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取用户页面导航信息
     * API: mtop.idle.web.user.page.nav
     */
    public JsonNode getUserPageNav() {
        String url = new XianyuMtopRequestBuilder("mtop.idle.web.user.page.nav")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取用户信用分
     * API: mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get (带 credit 参数)
     */
    public JsonNode getUserCredit(String userId) {
        String url = new XianyuMtopRequestBuilder("mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get")
                .addParam("userId", userId != null ? userId : "")
                .addParam("scene", "credit")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
