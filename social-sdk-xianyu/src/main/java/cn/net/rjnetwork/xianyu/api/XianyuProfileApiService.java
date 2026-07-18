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

    /** 获取用户页面导航信息 — mtop.idle.web.user.page.nav */
    public JsonNode getUserPageNav() {
        return apiClient.callMtop("mtop.idle.web.user.page.nav", "{}");
    }

    /** 获取用户信用分 — mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get (带 credit 参数) */
    public JsonNode getUserCredit(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId != null ? userId : "");
        data.put("scene", "credit");
        return apiClient.callMtop("mtop.gaia.nodejs.gaia.idle.data.gw.v2.index.get", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
