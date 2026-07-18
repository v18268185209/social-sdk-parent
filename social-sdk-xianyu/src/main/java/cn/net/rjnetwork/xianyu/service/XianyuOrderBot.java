package cn.net.rjnetwork.xianyu.service;

import cn.net.rjnetwork.chrome.cdp.CdpClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 闲鱼订单能力聚合：监控/自动收货/自动发货/批量/详情。
 *
 * <p>底层复用 {@link XianyuCdpBot} 的 {@code listPendingShipments/ship/listPendingReceipts/acceptOrder}，
 * 在其上加「闭环」语义：批量处理、轮询监控、单笔详情提取等。</p>
 *
 * <p>每账号独立实例，由 {@link XianyuSdk.XianyuAccount#order()} 懒加载。</p>
 */
public class XianyuOrderBot {

    private final CdpClient client;
    private final XianyuCdpBot bot;

    public XianyuOrderBot(CdpClient client, XianyuCdpBot bot) {
        this.client = client;
        this.bot = bot;
    }

    // ---------- 监控 ----------

    /** 加载「我卖出的」待发货订单卡片（刷新后提取）。 */
    public List<Map<String, Object>> monitorSold() {
        bot.clickByText("我的", true);
        bot.sleep(600);
        bot.clickByText("我卖出的", true);
        bot.sleep(1800);
        return bot.extractCards();
    }

    /** 加载「我买到的」待收货订单卡片。 */
    public List<Map<String, Object>> monitorBought() {
        bot.clickByText("我的", true);
        bot.sleep(600);
        bot.clickByText("我买到的", true);
        bot.sleep(1800);
        return bot.extractCards();
    }

    // ---------- 自动发货 ----------

    /** 单笔发货（复用 bot）。logisticsNo 为空则走「无需物流」流程。 */
    public Map<String, Object> ship(String keyword, String logisticsNo) {
        return bot.ship(keyword, logisticsNo);
    }

    /**
     * 批量发货：对匹配 {@code keyword} 的所有待发货订单执行发货。
     * 多轮刷新直到无匹配项或达到 5 轮上限。
     */
    public Map<String, Object> batchShip(String keyword, String logisticsNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        int shipped = 0, attempts = 0;
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> orders = monitorSold();
            boolean found = false;
            for (Map<String, Object> order : orders) {
                String title = String.valueOf(order.getOrDefault("title", ""));
                if (!title.contains(keyword)) continue;
                found = true;
                attempts++;
                Map<String, Object> r = bot.ship(title, logisticsNo);
                if (Boolean.TRUE.equals(r.get("success"))) shipped++;
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

    /**
     * 轮询监控待发货订单，对每个匹配 {@code keywordFilter} 的订单执行 {@code handler}。
     * @param keywordFilter 订单标题包含的关键字（null = 全部）
     * @param handler 对每个匹配订单执行的动作（返回是否处理成功）
     * @param intervalMs 轮询间隔
     * @param rounds 最大轮数
     * @return 已处理的订单数
     */
    public int watchPendingShipments(String keywordFilter, Predicate<Map<String, Object>> handler,
                                     long intervalMs, int rounds) {
        int processed = 0;
        for (int r = 0; r < rounds; r++) {
            List<Map<String, Object>> orders = monitorSold();
            for (Map<String, Object> order : orders) {
                String title = String.valueOf(order.getOrDefault("title", ""));
                if (keywordFilter != null && !keywordFilter.isBlank()
                        && !title.contains(keywordFilter)) continue;
                try {
                    if (handler.test(order)) processed++;
                } catch (Exception ignore) {}
            }
            if (processed > 0) return processed;
            bot.sleep(intervalMs);
        }
        return processed;
    }

    // ---------- 自动收货 ----------

    /** 单笔确认收货/验收（复用 bot）。 */
    public Map<String, Object> accept(String keyword) {
        return bot.acceptOrder(keyword);
    }

    /**
     * 批量确认收货：对「我买到的」中匹配 {@code keyword} 的所有订单执行确认收货。
     */
    public Map<String, Object> batchAccept(String keyword) {
        Map<String, Object> result = new LinkedHashMap<>();
        int accepted = 0, attempts = 0;
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> orders = monitorBought();
            boolean found = false;
            for (Map<String, Object> order : orders) {
                String title = String.valueOf(order.getOrDefault("title", ""));
                if (!title.contains(keyword)) continue;
                found = true;
                attempts++;
                Map<String, Object> r = bot.acceptOrder(title);
                if (Boolean.TRUE.equals(r.get("success"))) accepted++;
                bot.sleep(1200);
            }
            if (!found) break;
        }
        result.put("keyword", keyword);
        result.put("attempts", attempts);
        result.put("accepted", accepted);
        result.put("success", accepted > 0);
        result.put("message", "已确认收货 " + accepted + "/" + attempts);
        return result;
    }

    // ---------- 详情 ----------

    /**
     * 查询订单详情：点击匹配 {@code keyword} 的订单卡片进入详情页，
     * 返回详情页可见文本（含物流/买家/金额/状态等）+ 截图。
     */
    public Map<String, Object> detail(String keyword) {
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

    // ---------- 评价 ----------

    /**
     * 对已完成的订单提交评价（复用 {@link XianyuTradeBot#review}）。
     * 放在此处是因为评价是订单完成后的自然延伸能力。
     */
    public Map<String, Object> review(String keyword, String comment, int stars) {
        return new XianyuTradeBot(client, bot).review(keyword, comment, stars);
    }
}
