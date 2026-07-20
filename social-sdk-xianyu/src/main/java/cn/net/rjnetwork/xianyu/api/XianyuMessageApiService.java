package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(XianyuMessageApiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;
    /** accs IM 长连接客户端（懒初始化，首次发消息/拉历史时建立） */
    private XianyuImAccsClient accsClient;

    public XianyuMessageApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 取得（必要时建立）IM 长连接客户端。
     * <p>供外部挂推送监听器（{@code addPushListener}）使用，让实时帧直接落库。</p>
     */
    public XianyuImAccsClient getAccsClient() throws Exception {
        ensureAccs();
        return accsClient;
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
     * 获取 IM 长连接会话列表 — WSS LWP 协议。
     * <p>真实帧：</p>
     * <ul>
     *   <li>wss://wss-goofish.dingtalk.com/ POST JSON</li>
     *   <li>{"lwp":"/r/Conversation/listNewestPagination","headers":{"mid":"..."},
     *       "body":[9007199254740991,50]}</li>
     *   <li>body[0]=startTimestamp, [1]=limit</li>
     * </ul>
     *
     * @param limit 每页数量，默认 50
     */
    public JsonNode getConversationList(int limit) throws Exception {
        ensureAccs();
        int pageSize = Math.max(1, Math.min(limit, 100));
        long now = System.currentTimeMillis();

        // 闲鱼 IM 当前实现对 startTimeStamp 边界很敏感：真实页面首次分页用当前毫秒时间戳，
        // 过大的 Number.MAX_SAFE_INTEGER 会导致 WSS 业务层直接返回 code=400。
        JsonNode resp = accsClient.sendFrame("/r/Conversation/listNewestPagination", new Object[]{now, pageSize});
        if (isLwpBadRequest(resp)) {
            log.warn("[MESSAGE] conversation list with [now, limit] returned code=400, retry with object body");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("startTimeStamp", now);
            body.put("limitNum", pageSize);
            resp = accsClient.sendFrame("/r/Conversation/listNewestPagination", body);
        }
        if (isLwpBadRequest(resp)) {
            log.warn("[MESSAGE] conversation list object body returned code=400, retry with max timestamp array");
            resp = accsClient.sendFrame("/r/Conversation/listNewestPagination", new Object[]{9007199254740991L, pageSize});
        }
        log.info("[MESSAGE] conversation list response: {}", resp != null ? resp.toString() : "null");
        return resp;
    }

    private boolean isLwpBadRequest(JsonNode resp) {
        return resp != null && resp.path("code").asInt(200) == 400;
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
     * 发送消息 — accs 长连接协议（真实抓包验证 2026-07-19 CDP）。
     * <p>真实帧：</p>
     * <ul>
     *   <li>wss://wss-goofish.dingtalk.com/ POST JSON</li>
     *   <li>{"lwp":"/r/MessageSend/sendByReceiverScope","headers":{"mid":"..."},"body":[
     *       {"uuid":"...","cid":"60985230429@goofish","conversationType":1,
     *        "content":{"contentType":101,"custom":{"type":1,"data":"<base64 内容>"}},
     *        "redPointPolicy":0,"extension":{"extJson":"{}"},
     *        "ctx":{"appVersion":"1.0","platform":"web"},"mtags":{},"msgReadStatusSetting":1},
     *       {"actualReceivers":["2215024781926@goofish","2215280568736@goofish"]}]}</li>
     *   <li>content.custom.data 是 base64 编码的 JSON {"contentType":1,"text":{"text":"消息内容"}}</li>
     * </ul>
     *
     * @param cid        会话 id，形如 "60985230429@goofish"
     * @param content    消息明文内容
     * @param receiverIds 接收者 id 列表，每个形如 "2215024781926@goofish"（至少 1 个）
     */
    public JsonNode sendMessage(String cid, String content, String... receiverIds) throws Exception {
        ensureAccs();

        // 1. 构造 content.custom.data = base64({"contentType":1,"text":{"text":"内容"}})
        String plainJson = MAPPER.writeValueAsString(Map.of(
                "contentType", 1,
                "text", Map.of("text", content != null ? content : "")
        ));
        String base64Data = java.util.Base64.getEncoder().encodeToString(plainJson.getBytes(StandardCharsets.UTF_8));

        // 2. 构造 body 数组（第 0 项是消息体，第 1 项是 actualReceivers）
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("uuid", "-" + System.currentTimeMillis());
        msg.put("cid", cid != null ? cid : "");
        msg.put("conversationType", 1);
        msg.put("content", Map.of(
                "contentType", 101,
                "custom", Map.of("type", 1, "data", base64Data)
        ));
        msg.put("redPointPolicy", 0);
        msg.put("extension", Map.of("extJson", "{}"));
        msg.put("ctx", Map.of("appVersion", "1.0", "platform", "web"));
        msg.put("mtags", Map.of());
        msg.put("msgReadStatusSetting", 1);

        // actualReceivers 至少含一个；调用方没传则用 cid 兜底
        String[] receivers = (receiverIds == null || receiverIds.length == 0)
                ? new String[]{cid} : receiverIds;
        Map<String, Object> recv = new LinkedHashMap<>();
        recv.put("actualReceivers", receivers);

        return accsClient.sendFrameAsync("/r/MessageSend/sendByReceiverScope", new Object[]{msg, recv});
    }

    /**
     * 获取单会话消息历史 — accs 长连接协议。
     * <p>真实帧：</p>
     * <ul>
     *   <li>wss://wss-goofish.dingtalk.com/ POST JSON</li>
     *   <li>{"lwp":"/r/MessageManager/listUserMessages","headers":{"mid":"..."},
     *       "body":["63620203412@goofish",false,9007199254740991,20,false]}</li>
     * </ul>
     *
     * @param cid    会话 id，形如 "63620203412@goofish"
     * @param size   拉取条数，默认 20
     */
    public JsonNode getMessageHistory(String cid, Integer size) throws Exception {
        ensureAccs();
        Object[] body = new Object[]{
                cid != null ? cid : "",
                false,
                9007199254740991L,
                size != null && size > 0 ? size : 20,
                false
        };
        return accsClient.sendFrame("/r/MessageManager/listUserMessages", body);
    }

    /**
     * 批量拉取所有会话的历史消息。
     * <p>先通过 WSS 获取会话列表，再逐条拉取每会话语义。</p>
     *
     * @param maxCidCount 最多处理多少个会话（避免一次性拉太多）
     * @param msgLimit    每个会话拉取条数
     * @return 返回包含 hasMore 和 userConvs 的 JSON
     */
    public JsonNode pullAllHistory(int maxCidCount, int msgLimit) throws Exception {
        ensureAccs();
        // 1. 获取会话列表
        JsonNode convList = getConversationList(maxCidCount);
        if (convList == null || !convList.has("body")) return convList;

        JsonNode bodyNode = convList.path("body");
        List<String> cids = new ArrayList<>();
        for (JsonNode conv : bodyNode.path("userConvs")) {
            String cid = conv.path("cid").asText();
            if (!cid.isEmpty()) {
                // 确保 cid 带 @goofish 后缀
                if (!cid.contains("@")) {
                    cid += "@goofish";
                }
                cids.add(cid);
            }
        }

        // 2. 对每个会话拉取历史
        JsonNode allHistory = MAPPER.readTree("{}");
        for (String cid : cids) {
            try {
                JsonNode history = getMessageHistory(cid, msgLimit);
                if (history != null && history.has("body")) {
                    allHistory = MAPPER.readTree(
                            allHistory.toString().replace("}",
                                    ",\"histories\":[\"" + history.path("body").path("userMessageModels").toString() + "\"]}"
                            )
                    );
                }
            } catch (Exception e) {
                log.warn("[MESSAGE] failed to pull history for cid={}: {}", cid, e.getMessage());
            }
        }
        return allHistory;
    }

    // ==================== 黑名单 ====================

    /**
     * 查询黑名单列表 — mtop.taobao.idlemessage.pc.blacklist.query v1.0
     */
    public JsonNode queryBlacklist() {
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.blacklist.query", "{}");
    }

    /**
     * 添加黑名单 — mtop.taobao.idlemessage.pc.blacklist.add v2.0
     *
     * @param userId 要拉黑的用户 id
     */
    public JsonNode addBlacklist(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId != null ? userId : "");
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.blacklist.add", "2.0", toJson(data));
    }

    /**
     * 移除黑名单 — mtop.taobao.idlemessage.pc.blacklist.remove v1.0
     *
     * @param userId 要移除的用户 id
     */
    public JsonNode removeBlacklist(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId != null ? userId : "");
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.blacklist.remove", toJson(data));
    }

    // ==================== 红花（点赞/送花） ====================

    /**
     * 送红花 — mtop.taobao.idlemessage.red.flower v1.0
     *
     * @param targetId   目标 id（用户或商品）
     * @param targetType 目标类型（USER 或 ITEM）
     */
    public JsonNode sendRedFlower(String targetId, String targetType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetId", targetId != null ? targetId : "");
        data.put("targetType", targetType != null ? targetType : "USER");
        return apiClient.callMtop("mtop.taobao.idlemessage.red.flower", "1.0", toJson(data));
    }

    // ==================== 通知设置 ====================

    /**
     * 关闭/更新平台通知 — mtop.taobao.idlemessage.pc.profile.notice.update v1.0
     *
     * @param noticeId 通知设置 id
     */
    public JsonNode closeNotice(String noticeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeId", noticeId != null ? noticeId : "");
        data.put("status", "CLOSED");
        return apiClient.callMtop("mtop.taobao.idlemessage.pc.profile.notice.update", toJson(data));
    }

    private void ensureAccs() throws Exception {
        if (accsClient == null) {
            accsClient = new XianyuImAccsClient(apiClient);
        }
        accsClient.connect();
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
