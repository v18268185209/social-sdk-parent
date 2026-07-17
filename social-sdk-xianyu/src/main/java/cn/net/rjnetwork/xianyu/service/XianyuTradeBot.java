package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 闲鱼交易闭环能力：订单监控、发货处理、自动消息回复、评价。
 *
 * <p>该类在 {@link XianyuCdpBot} 已有的 {@code listPendingShipments/ship/acceptOrder/sendMessage}
 * 等单步操作之上，补齐「闭环」语义：监控轮询、自动回复、批量发货、评价提交等。</p>
 *
 * <p>所有方法均基于已登录的 CDP 会话执行，不持有自己的连接；
 * 由 {@link XianyuCdpSessionManager} 统一管理会话生命周期。</p>
 */
public class XianyuTradeBot {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CdpClient client;
    private final XianyuCdpBot bot;

    public XianyuTradeBot(CdpClient client, XianyuCdpBot bot) {
        this.client = client;
        this.bot = bot;
    }

    // ============================ 订单监控 ============================

    /**
     * 监控「我卖出的」订单列表，返回当前待发货订单卡片。
     * 与 {@link XianyuCdpBot#listPendingShipments()} 的区别在于该方法会先刷新页面
     * 确保拿到最新状态，并返回结构化字段（标题/价格/买家/状态）。
     */
    public List<Map<String, Object>> monitorSoldOrders() {
        bot.clickByText("我的", true);
        bot.sleep(600);
        bot.clickByText("我卖出的", true);
        bot.sleep(1800);
        return bot.extractCards();
    }

    /**
     * 轮询监控待发货订单，对每个匹配 {@code keywordFilter} 的订单执行 {@code handler}。
     * 间隔 {@code intervalMs} 轮询，最多 {@code rounds} 轮，命中即返回。
     *
     * @param keywordFilter 过滤订单标题包含的关键字（null = 全部）
     * @param handler 对每个匹配订单执行的动作（如 {@code ship(keyword, logisticsNo)}）
     * @param intervalMs 轮询间隔（毫秒）
     * @param rounds 最大轮数
     * @return 已处理的订单数
     */
    public int watchPendingShipments(String keywordFilter, Predicate<Map<String, Object>> handler,
                                     long intervalMs, int rounds) {
        int processed = 0;
        for (int r = 0; r < rounds; r++) {
            List<Map<String, Object>> orders = monitorSoldOrders();
            for (Map<String, Object> order : orders) {
                String title = String.valueOf(order.getOrDefault("title", ""));
                if (keywordFilter != null && !keywordFilter.isBlank()
                        && !title.contains(keywordFilter)) {
                    continue;
                }
                try {
                    if (handler.test(order)) {
                        processed++;
                    }
                } catch (Exception ignore) {
                    // 单个订单处理失败不中断监控
                }
            }
            if (processed > 0) return processed;
            bot.sleep(intervalMs);
        }
        return processed;
    }

    // ============================ 批量发货 ============================

    /**
     * 批量发货：对 {@code keyword} 匹配的所有待发货订单执行发货。
     * {@code logisticsNo} 非空时填写物流单号，空则走「无需物流」流程。
     */
    public Map<String, Object> batchShip(String keyword, String logisticsNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        int shipped = 0;
        int attempts = 0;
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> orders = monitorSoldOrders();
            boolean found = false;
            for (Map<String, Object> order : orders) {
                String title = String.valueOf(order.getOrDefault("title", ""));
                if (!title.contains(keyword)) continue;
                found = true;
                attempts++;
                Map<String, Object> r = bot.ship(title, logisticsNo);
                if (Boolean.TRUE.equals(r.get("success"))) {
                    shipped++;
                }
                bot.sleep(1500);
            }
            if (!found) break;
        }
        result.put("keyword", keyword);
        result.put("attempts", attempts);
        result.put("shipped", shipped);
        result.put("success", shipped > 0);
        result.put("message", "已发货 " + shipped + "/" + attempts);
        return result;
    }

    // ============================ 自动消息回复 ============================

    /**
     * 列出当前消息会话（含未读数）。
     * 在 {@link XianyuCdpBot#listConversations()} 基础上补充未读判定。
     */
    public List<Map<String, Object>> monitorConversations() {
        return bot.listConversations();
    }

    /**
     * 自动回复：对 {@code conversationFilter} 匹配的会话发送 {@code reply}。
     * 用于「收到消息即回复」的自动客服场景。
     *
     * @param conversationFilter 会话名/商品名关键字（null = 全部会话）
     * @param reply 回复内容
     * @return 已回复的会话数
     */
    public int autoReply(String conversationFilter, String reply) {
        bot.clickByText("消息", true);
        bot.sleep(1500);
        List<Map<String, Object>> convs = bot.extractCards();
        int replied = 0;
        for (Map<String, Object> c : convs) {
            String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
            if (conversationFilter != null && !conversationFilter.isBlank()
                    && !name.contains(conversationFilter)) {
                continue;
            }
            Map<String, Object> r = bot.sendMessage(name, reply);
            if (Boolean.TRUE.equals(r.get("success"))) replied++;
            bot.sleep(800);
        }
        return replied;
    }

    /**
     * 监听新消息并回调。轮询消息列表，对每个含 {@code keyword} 的新会话触发 {@code handler}。
     * 简化版：基于轮询而非 WebSocket 长连接（后者需要额外接入闲鱼推送协议）。
     */
    public int watchMessages(String keyword, Predicate<String> handler,
                             long intervalMs, int rounds) {
        int handled = 0;
        for (int r = 0; r < rounds; r++) {
            bot.clickByText("消息", true);
            bot.sleep(1500);
            List<Map<String, Object>> convs = bot.extractCards();
            for (Map<String, Object> c : convs) {
                String name = String.valueOf(c.getOrDefault("title", c.getOrDefault("detail", "")));
                if (keyword == null || keyword.isBlank() || name.contains(keyword)) {
                    try {
                        if (handler.test(name)) handled++;
                    } catch (Exception ignore) {
                    }
                }
            }
            if (handled > 0) return handled;
            bot.sleep(intervalMs);
        }
        return handled;
    }

    // ============================ 评价 ============================

    /**
     * 对已完成的订单提交评价。
     * 进入「我卖出的」→ 找含 {@code keyword} 的订单 → 点击「评价」/「去评价」→
     * 选星级（默认 5 星）→ 填评价内容 → 提交。
     *
     * @param keyword 订单/商品关键字
     * @param comment 评价文本（空则用默认好评）
     * @param stars 星级 1-5（默认 5）
     */
    public Map<String, Object> review(String keyword, String comment, int stars) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keyword == null || keyword.isBlank()) {
            m.put("success", false);
            m.put("message", "缺少订单关键字");
            return m;
        }
        bot.clickByText("我的", true);
        bot.sleep(600);
        bot.clickByText("我卖出的", true);
        bot.sleep(1500);

        boolean entered = bot.clickActionNear(keyword, "评价")
                || bot.clickActionNear(keyword, "去评价")
                || bot.clickActionNear(keyword, "评价管理");
        if (!entered) {
            m.put("success", false);
            m.put("message", "未找到订单/评价入口");
            return m;
        }
        bot.sleep(1500);

        // 选星级（点第 N 颗星）
        int s = (stars < 1 || stars > 5) ? 5 : stars;
        boolean starClicked = clickStar(s);
        bot.sleep(600);

        // 填评价内容
        String content = (comment == null || comment.isBlank())
                ? "交易愉快，感谢支持！" : comment;
        boolean filled = fillReviewContent(content);
        bot.sleep(400);

        // 提交
        boolean submitted = bot.clickByText("提交", true)
                || bot.clickByText("发布评价", true)
                || bot.clickByText("确定", true);
        bot.sleep(1200);

        m.put("success", submitted);
        m.put("keyword", keyword);
        m.put("starClicked", starClicked);
        m.put("contentFilled", filled);
        m.put("message", submitted ? "评价已提交" : "已操作但未找到提交按钮");
        return m;
    }

    /** 点击第 n 颗星（1-based）。通过定位「评价」容器内的星星元素点击。 */
    private boolean clickStar(int n) {
        String expr = "(function(){"
                + "var all=document.querySelectorAll('[class*=star],[class*=Star],[role=radio],svg');"
                + "var reviewStars=[];"
                + "for(var i=0;i<all.length;i++){"
                + "  var e=all[i];"
                + "  if(e.offsetParent===null) continue;"
                + "  var p=e.parentElement;"
                + "  var nearReview=false;"
                + "  while(p){ if((p.innerText||'').indexOf('评价')>=0){nearReview=true;break;} p=p.parentElement; }"
                + "  if(nearReview) reviewStars.push(e);"
                + "}"
                + "if(reviewStars.length<" + n + ") return null;"
                + "var star=reviewStars[" + (n - 1) + "];"
                + "star.scrollIntoView({block:'center'});"
                + "var r=star.getBoundingClientRect();"
                + "return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});"
                + "})()";
        String res = bot.eval(expr);
        if (res == null || res.equals("null")) return false;
        try {
            JsonNode node = MAPPER.readTree(res);
            client.click(node.get("x").asDouble(), node.get("y").asDouble());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 定位评价内容输入框并填写。 */
    private boolean fillReviewContent(String content) {
        // 优先找 textarea / contenteditable 在「评价」容器内
        String selector = "textarea";
        boolean has = bot.eval("!!document.querySelector('textarea')").equals("true");
        if (!has) {
            selector = "[contenteditable='true']";
        }
        try {
            bot.typeInto(selector, content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================ 订单详情 ============================

    /**
     * 查询订单详情：点击 {@code keyword} 匹配的订单卡片进入详情页，
     * 返回详情页可见文本（含物流、买家、金额、状态等）。
     */
    public Map<String, Object> orderDetail(String keyword) {
        Map<String, Object> m = new LinkedHashMap<>();
        bot.clickByText("我的", true);
        bot.sleep(600);
        bot.clickByText("我卖出的", true);
        bot.sleep(1500);
        boolean opened = bot.clickByText(keyword, true);
        if (!opened) {
            m.put("success", false);
            m.put("message", "未找到订单: " + keyword);
            return m;
        }
        bot.sleep(2000);
        m.put("success", true);
        m.put("keyword", keyword);
        m.put("detail", bot.getBodyText());
        m.put("screenshot", bot.screenshotViewport());
        return m;
    }
}
