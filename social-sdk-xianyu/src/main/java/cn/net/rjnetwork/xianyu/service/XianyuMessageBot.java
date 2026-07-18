package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 闲鱼消息能力聚合：接收/回复/关键词回复/自定义词/AI 接管回复。
 *
 * <p>消息接收采用轮询策略（CDP 无法直接拦截闲鱼 WebSocket 推送协议，
 * 改为定时刷新消息列表 + 对比上次会话快照，识别新会话/新消息）。
 * 关键词与自定义词回复基于「会话名/最后一条消息文案」匹配命中规则，
 * AI 接管回复接受外部 {@link Function} 回调（上层注入 LLM 客户端），
 * SDK 不绑定具体 AI 框架。</p>
 *
 * <p>每账号独立实例，由 {@link XianyuSdk.XianyuAccount#message()} 懒加载。</p>
 */
public class XianyuMessageBot {

    private final CdpClient client;
    private final XianyuCdpBot bot;

    public XianyuMessageBot(CdpClient client, XianyuCdpBot bot) {
        this.client = client;
        this.bot = bot;
    }

    // ---------- 接收 ----------

    /** 列出当前消息会话（点击「消息」入口后提取卡片）。 */
    public List<Map<String, Object>> listConversations() {
        return bot.listConversations();
    }

    /**
     * 轮询消息列表，返回 {@code sinceSnapshot} 之后新增的会话。
     * {@code sinceSnapshot} 为 null 表示全量返回（首次拉取）。
     * 上层对比前后快照可识别「新消息」：新增会话或未读数变化。
     */
    public List<Map<String, Object>> receiveNew(java.util.Set<String> sinceSnapshot) {
        List<Map<String, Object>> convs = bot.listConversations();
        if (sinceSnapshot == null) return convs;
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> c : convs) {
            String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
            if (!sinceSnapshot.contains(name)) out.add(c);
        }
        return out;
    }

    /** 拉取当前所有会话名集合，供下次 {@link #receiveNew(java.util.Set)} 做差集。 */
    public java.util.Set<String> snapshot() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (Map<String, Object> c : bot.listConversations()) {
            names.add(String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", ""))));
        }
        return names;
    }

    // ---------- 回复 ----------

    /** 对指定会话发送文本（复用 bot）。 */
    public Map<String, Object> reply(String conversation, String text) {
        return bot.sendMessage(conversation, text);
    }

    /**
     * 关键词回复：遍历当前所有会话，对最后一条消息命中 {@code keyword} 的会话发送 {@code reply}。
     * @param keyword 命中关键字（消息文案包含即命中）
     * @param reply 回复内容
     * @return 命中并回复的会话数
     */
    public int replyByKeyword(String keyword, String reply) {
        if (keyword == null || keyword.isBlank() || reply == null || reply.isBlank()) return 0;
        List<Map<String, Object>> convs = bot.listConversations();
        int sent = 0;
        for (Map<String, Object> c : convs) {
            String last = String.valueOf(c.getOrDefault("detail", c.getOrDefault("title", "")));
            if (last.contains(keyword)) {
                String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
                try {
                    Map<String, Object> r = bot.sendMessage(name, reply);
                    if (Boolean.TRUE.equals(r.get("success"))) sent++;
                    bot.sleep(800);
                } catch (Exception ignore) {}
            }
        }
        return sent;
    }

    /**
     * 自定义词回复：按 {@code rules} 映射表，对当前会话命中任一关键字的发送对应回复。
     * @param rules 关键字→回复内容 映射（按插入顺序匹配，命中即停）
     * @return 命中并回复的会话数
     */
    public int replyByCustomRules(java.util.Map<String, String> rules) {
        if (rules == null || rules.isEmpty()) return 0;
        List<Map<String, Object>> convs = bot.listConversations();
        int sent = 0;
        for (Map<String, Object> c : convs) {
            String last = String.valueOf(c.getOrDefault("detail", c.getOrDefault("title", "")));
            String matchedReply = null;
            for (Map.Entry<String, String> e : rules.entrySet()) {
                if (last.contains(e.getKey())) {
                    matchedReply = e.getValue();
                    break;
                }
            }
            if (matchedReply != null) {
                String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
                try {
                    Map<String, Object> r = bot.sendMessage(name, matchedReply);
                    if (Boolean.TRUE.equals(r.get("success"))) sent++;
                    bot.sleep(800);
                } catch (Exception ignore) {}
            }
        }
        return sent;
    }

    /**
     * AI 接管回复：对当前所有会话，将「会话名+最后消息文案」交给 {@code aiResponder}，
     * 由其返回回复内容（null 表示不回复），SDK 负责发送。
     *
     * <p>SDK 不绑定具体 AI 框架——上层注入 {@code Function<String,String>}，
     * 输入为「会话名 | 最后消息」，输出为回复文本（null/blank 跳过）。
     * 上层可在此回调内对接 ChatGPT/通义/本地模型等。</p>
     *
     * @param aiResponder AI 回复函数（输入上下文，输出回复文本或 null）
     * @return 已回复的会话数
     */
    public int replyByAi(Function<String, String> aiResponder) {
        if (aiResponder == null) return 0;
        List<Map<String, Object>> convs = bot.listConversations();
        int sent = 0;
        for (Map<String, Object> c : convs) {
            String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
            String last = String.valueOf(c.getOrDefault("detail", c.getOrDefault("title", "")));
            try {
                String reply = aiResponder.apply(name + " | " + last);
                if (reply == null || reply.isBlank()) continue;
                Map<String, Object> r = bot.sendMessage(name, reply);
                if (Boolean.TRUE.equals(r.get("success"))) sent++;
                bot.sleep(1000);
            } catch (Exception ignore) {}
        }
        return sent;
    }

    /**
     * 轮询监控新消息并按 {@code handler} 处理（关键词/自定义词/AI 都可由此封装）。
     * @param intervalMs 轮询间隔
     * @param rounds 最大轮数
     * @param handler 新会话处理器（输入会话名，返回回复文本；null 表示不回复）
     * @return 已回复的会话数
     */
    public int watchNewMessages(long intervalMs, int rounds, Function<String, String> handler) {
        java.util.Set<String> snap = snapshot();
        int sent = 0;
        for (int r = 0; r < rounds; r++) {
            bot.sleep(intervalMs);
            List<Map<String, Object>> news = receiveNew(snap);
            for (Map<String, Object> c : news) {
                String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
                snap.add(name);
                try {
                    String reply = handler == null ? null : handler.apply(name);
                    if (reply != null && !reply.isBlank()) {
                        Map<String, Object> rs = bot.sendMessage(name, reply);
                        if (Boolean.TRUE.equals(rs.get("success"))) sent++;
                        bot.sleep(800);
                    }
                } catch (Exception ignore) {}
            }
            if (sent > 0 && news.isEmpty()) return sent;
        }
        return sent;
    }

    /** 批量对当前所有会话发送固定文案（客服群发场景）。 */
    public Map<String, Object> broadcast(String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少群发文案");
            return m;
        }
        List<Map<String, Object>> convs = bot.listConversations();
        int sent = 0;
        for (Map<String, Object> c : convs) {
            String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
            try {
                Map<String, Object> r = bot.sendMessage(name, text);
                if (Boolean.TRUE.equals(r.get("success"))) sent++;
                bot.sleep(800);
            } catch (Exception ignore) {}
        }
        m.put("success", sent > 0);
        m.put("sent", sent);
        m.put("total", convs.size());
        m.put("message", "群发完成 " + sent + "/" + convs.size());
        return m;
    }
}
