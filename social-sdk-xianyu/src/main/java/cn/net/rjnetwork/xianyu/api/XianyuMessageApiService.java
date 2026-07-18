package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼消息 API 服务
 * 封装会话列表、发消息、收消息等 MTOP 接口调用
 */
public class XianyuMessageApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuMessageApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 获取会话列表 — mtop.taobao.idleim.chatw.list.get */
    public JsonNode getSessionList() {
        return apiClient.callMtop("mtop.taobao.idleim.chatw.list.get", "{}");
    }

    /** 发送消息 — mtop.taobao.idleim.message.send */
    public JsonNode sendMessage(String sessionId, String content, String receiverId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId != null ? sessionId : "");
        data.put("content", content != null ? content : "");
        data.put("receiverId", receiverId != null ? receiverId : "");
        return apiClient.callMtop("mtop.taobao.idleim.message.send", toJson(data));
    }

    /** 获取消息历史 — mtop.taobao.idleim.chatw.history.get */
    public JsonNode getMessageHistory(String sessionId, String page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId != null ? sessionId : "");
        data.put("page", page != null ? page : "1");
        return apiClient.callMtop("mtop.taobao.idleim.chatw.history.get", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
