package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼消息 API 服务
 * <p>真实抓包验证（2026-07-18 CDP 导航到 https://www.goofish.com/im）：</p>
 * <ul>
 *   <li>闲鱼消息系统走的是 mtop.taobao.idlemessage.pc.* 接口组 + IM 长连接（accs token）</li>
 *   <li>轻量元数据接口（纯 MTOP 可拉）：loginuser.get / redpoint.query / login.token / session.sync / user.query</li>
 *   <li>完整会话列表 + 收发消息需建立 IM 长连接（accs），不在本类范围</li>
 * </ul>
 */
public class XianyuMessageApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuMessageApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取当前登录用户 id — mtop.taobao.idlemessage.pc.loginuser.get
     * <p>返回 data.userId（数字形式闲鱼 uid）</p>
     */
    public JsonNode getLoginUser() {
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.loginuser.get", "{}");
    }

    /**
     * 查询红点未读消息总数 — mtop.taobao.idlemessage.pc.redpoint.query
     * <p>返回 data.total（未读消息计数）</p>
     */
    public JsonNode getRedPointCount() {
        Map<String, Object> data = new LinkedHashMap<>();
        // 真实抓包参数：sessionTypes=1,19,15,32,3,44,51,52,24 / fetch=50
        data.put("sessionTypes", "1,19,15,32,3,44,51,52,24");
        data.put("fetch", 50);
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.redpoint.query", toJson(data));
    }

    /**
     * 获取 IM 长连接 accessToken — mtop.taobao.idlemessage.pc.login.token
     * <p>返回 data.accessToken / refreshToken / accessTokenExpiredTime。
     * 后续建立 accs 长连接拉真实会话列表 + 收发消息需要这个 token。</p>
     */
    public JsonNode getImLoginToken() {
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.login.token", "{}");
    }

    /**
     * 同步会话列表（轻量元数据）— mtop.taobao.idlemessage.pc.session.sync
     * <p>真实抓包返回会话快照（最近会话摘要）。完整会话历史需建立 accs 长连接，
     * 超出纯 HTTP MTOP 范围。</p>
     */
    public JsonNode getSessionList() {
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.session.sync", "{}");
    }

    /**
     * 查询用户信息 — mtop.taobao.idlemessage.pc.user.query
     * <p>真实抓包参数：用户 id 列表，返回昵称/头像等。用于把对话对方 uid 解析成展示信息。</p>
     *
     * @param userIds 逗号分隔的用户 id 列表，如 "2220955938506,2219062705934"
     */
    public JsonNode queryUsers(String userIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        // 真实抓包：pc.user.query 的 data 是用户 id 数组
        data.put("userIds", userIds != null ? userIds : "");
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.user.query", toJson(data));
    }

    /**
     * 发送消息 — TODO 需 accs 长连接
     * <p>闲鱼 IM 发消息走 accs 长连接协议（不是简单 MTOP），超出纯 HTTP 范围。
     * 后续如需自动回复，要另起 accs 客户端模块。</p>
     */
    public JsonNode sendMessage(String sessionId, String content, String receiverId) {
        throw new UnsupportedOperationException(
                "Sending IM messages requires accs long connection, not supported in pure HTTP MTOP SDK");
    }

    /**
     * 获取消息历史 — TODO 需 accs 长连接
     * <p>同 sendMessage，消息历史走 accs 长连接协议。</p>
     */
    public JsonNode getMessageHistory(String sessionId, String page) {
        throw new UnsupportedOperationException(
                "IM history requires accs long connection, not supported in pure HTTP MTOP SDK");
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
