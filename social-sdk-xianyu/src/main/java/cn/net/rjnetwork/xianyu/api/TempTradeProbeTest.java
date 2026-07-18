package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：用 CDP cookie 探测闲鱼订单扩展类接口的真实接口名（trade.detail/delivery/review/refund 域）。 */
public class TempTradeProbeTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);

        // 已真验同域：mtop.idle.web.trade.sold.list / mtop.idle.web.trade.bought.list
        // 推测同域：detail/delivery/review/refund/offer
        // 先拉一个真实 orderId 做候选 body（用 sold.list 拿）
        String orderId = "";
        try {
            JsonNode sold = client.callMtop("mtop.idle.web.trade.sold.list", "1.0", "{}");
            if (sold != null) {
                JsonNode items = sold.path("data").path("items");
                if (items.isArray() && items.size() > 0) {
                    orderId = items.get(0).path("commonData").path("orderId").asText("");
                    System.out.println("[REAL orderId] " + orderId);
                }
            }
        } catch (Exception e) { System.out.println("[sold list err] " + e.getMessage()); }

        String oid = orderId.isEmpty() ? "dummyOrderId" : orderId;
        String[][] candidates = {
            // 详情（同 sold/bought 域）
            {"mtop.idle.web.trade.order.detail", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.idle.web.trade.detail", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.idle.web.trade.sold.detail", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.idle.web.trade.order.get", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            // 发货
            {"mtop.idle.web.trade.delivery.send", "1.0", "{\"orderId\":\"" + oid + "\",\"trackingNo\":\"test\"}"},
            {"mtop.idle.web.trade.delivery", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.idle.web.trade.ship", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.idle.web.trade.send", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            // 评价
            {"mtop.idle.web.trade.review.list", "1.0", "{\"page\":\"1\",\"pageSize\":\"20\"}"},
            {"mtop.idle.web.trade.review.my", "1.0", "{}"},
            {"mtop.idle.web.trade.review.send", "1.0", "{\"orderId\":\"" + oid + "\",\"rating\":\"5\"}"},
            {"mtop.idle.web.trade.review.add", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            // 退款
            {"mtop.idle.web.trade.refund.list", "1.0", "{\"page\":\"1\",\"pageSize\":\"20\"}"},
            {"mtop.idle.web.trade.refund.apply", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.idle.web.trade.refund.detail", "1.0", "{\"refundId\":\"test\"}"},
            {"mtop.idle.web.trade.aftersale.list", "1.0", "{}"},
            // 议价
            {"mtop.idle.web.trade.offer.list", "1.0", "{}"},
            {"mtop.idle.web.trade.offer.send", "1.0", "{\"itemId\":\"test\",\"price\":\"1\"}"},
            // 地址
            {"mtop.idle.web.trade.address.list", "1.0", "{}"},
            {"mtop.idle.web.user.address.list", "1.0", "{}"},
            // 旧 trade 域（对照）
            {"mtop.taobao.idletrade.order.review", "1.0", "{\"orderId\":\"" + oid + "\"}"},
            {"mtop.taobao.idletrade.review.list", "1.0", "{}"},
            {"mtop.taobao.idletrade.refund.list", "1.0", "{}"},
        };

        for (int i = 0; i < candidates.length; i++) {
            String api = candidates[i][0];
            String version = candidates[i][1];
            String data = candidates[i][2];
            try {
                JsonNode r = client.callMtop(api, version, data);
                String ret = r != null ? r.path("ret").toString() : "null";
                String body = m.writeValueAsString(r);
                String preview = body.length() > 400 ? body.substring(0, 400) + "..." : body;
                System.out.println("\n[" + i + "] api=" + api + " v=" + version);
                System.out.println("    ret=" + ret);
                System.out.println("    body=" + preview);
            } catch (Exception e) {
                System.out.println("\n[" + i + "] api=" + api + " ERROR: " + e.getMessage());
            }
        }
        System.out.println("\n========== DONE ==========");
    }
}
