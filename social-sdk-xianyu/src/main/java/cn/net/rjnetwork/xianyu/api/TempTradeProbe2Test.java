package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：用 CDP cookie 探测闲鱼 review/refund/address/offer 类接口的真实接口名（多域穷举）。 */
public class TempTradeProbe2Test {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);

        // 已真验：mtop.idle.web.trade.order.detail（详情，业务错误而非 NOT_EXIST）
        //         mtop.idle.web.trade.sold.list / mtop.idle.web.trade.bought.list（列表）
        // 缺：review/refund/offer/address 真实接口名，多域穷举
        String[][] candidates = {
            // review 域（评价）
            {"mtop.idle.web.review.list", "1.0", "{}"},
            {"mtop.idle.web.review.my", "1.0", "{}"},
            {"mtop.idle.web.review.send", "1.0", "{\"orderId\":\"test\"}"},
            {"mtop.idle.web.auction.review.list", "1.0", "{}"},
            {"mtop.taobao.idle.auction.review.list", "1.0", "{}"},
            {"mtop.taobao.idle.web.review.list", "1.0", "{}"},
            {"mtop.idle.web.xyh.review.list", "1.0", "{}"},
            // refund/aftersale 域（退款/售后）
            {"mtop.idle.web.aftersale.list", "1.0", "{}"},
            {"mtop.idle.web.refund.list", "1.0", "{}"},
            {"mtop.idle.web.aftersale.detail", "1.0", "{\"refundId\":\"test\"}"},
            {"mtop.taobao.idle.aftersale.list", "1.0", "{}"},
            {"mtop.taobao.idle.web.refund.list", "1.0", "{}"},
            {"mtop.idle.web.xyh.aftersale.list", "1.0", "{}"},
            {"mtop.idle.web.trade.aftersale", "1.0", "{}"},
            // offer 域（议价/出价）
            {"mtop.idle.web.offer.list", "1.0", "{}"},
            {"mtop.idle.web.auction.offer.list", "1.0", "{\"itemId\":\"test\"}"},
            {"mtop.taobao.idle.auction.offer.list", "1.0", "{}"},
            {"mtop.idle.web.xyh.offer.list", "1.0", "{}"},
            {"mtop.idle.web.trade.offer", "1.0", "{}"},
            // address 域（地址）
            {"mtop.idle.web.address.list", "1.0", "{}"},
            {"mtop.idle.web.user.address.list", "1.0", "{}"},
            {"mtop.idle.web.shipping.address.list", "1.0", "{}"},
            {"mtop.taobao.idle.address.list", "1.0", "{}"},
            {"mtop.taobao.idle.web.address.list", "1.0", "{}"},
            {"mtop.idle.web.xyh.address.list", "1.0", "{}"},
            // delivery 域（发货，已知 detail 接口在 trade.order.detail，推测 delivery 在同域）
            {"mtop.idle.web.trade.order.delivery", "1.0", "{\"orderId\":\"test\"}"},
            {"mtop.idle.web.trade.order.ship", "1.0", "{\"orderId\":\"test\"}"},
            {"mtop.idle.web.trade.order.send", "1.0", "{\"orderId\":\"test\"}"},
            {"mtop.idle.web.trade.order.logistics", "1.0", "{\"orderId\":\"test\"}"},
            {"mtop.idle.web.trade.order.delivery.send", "1.0", "{\"orderId\":\"test\"}"},
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
