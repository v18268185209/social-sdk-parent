package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼消息 API 服务
 * 封装会话列表、发消息、收消息等 MTOP 接口调用
 */
public class XianyuMessageApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuMessageApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取会话列表
     * API: mtop.taobao.idleim.chatw.list.get
     */
    public JsonNode getSessionList() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleim.chatw.list.get")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 发送消息
     * API: mtop.taobao.idleim.message.send
     */
    public JsonNode sendMessage(String sessionId, String content, String receiverId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleim.message.send")
                .addParam("sessionId", sessionId)
                .addParam("content", content)
                .addParam("receiverId", receiverId != null ? receiverId : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取消息历史
     * API: mtop.taobao.idleim.chatw.history.get
     */
    public JsonNode getMessageHistory(String sessionId, String page) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleim.chatw.history.get")
                .addParam("sessionId", sessionId)
                .addParam("page", page != null ? page : "1")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
