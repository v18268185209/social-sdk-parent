package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 临时真实测试：用 CDP cookie 探测闲鱼商品编辑类接口的真实接口名（mtop.taobao.idlemanage.* / mtop.idle.web.manage.* 域）。
 */
public class TempEditProbe2Test {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);
        String itemId = "1042782385557";

        // 命名规律候选：idlemanage.* / idle.web.manage.* / idle.pc.* 域
        String[][] candidates = {
            // 上架
            {"mtop.taobao.idlemanage.item.upshelf", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            {"mtop.idle.web.manage.item.upshelf", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            {"mtop.idle.web.xyh.item.upshelf", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            {"mtop.taobao.idle.pc.item.upshelf", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            // 编辑
            {"mtop.taobao.idlemanage.item.edit", "1.0", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试\"}"},
            {"mtop.idle.web.manage.item.edit", "1.0", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试\"}"},
            {"mtop.idle.web.xyh.item.edit", "1.0", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试\"}"},
            {"mtop.taobao.idle.pc.item.edit", "1.0", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试\"}"},
            // 改价
            {"mtop.taobao.idlemanage.item.price.update", "1.0", "{\"itemId\":\"" + itemId + "\",\"price\":\"599\"}"},
            {"mtop.idle.web.manage.item.price", "1.0", "{\"itemId\":\"" + itemId + "\",\"price\":\"599\"}"},
            {"mtop.idle.web.xyh.item.price", "1.0", "{\"itemId\":\"" + itemId + "\",\"price\":\"599\"}"},
            // 改库存
            {"mtop.taobao.idlemanage.item.stock.update", "1.0", "{\"itemId\":\"" + itemId + "\",\"stock\":\"1\"}"},
            {"mtop.idle.web.manage.item.stock", "1.0", "{\"itemId\":\"" + itemId + "\",\"stock\":\"1\"}"},
            // 复制
            {"mtop.taobao.idlemanage.item.copy", "1.0", "{\"sourceItemId\":\"" + itemId + "\"}"},
            {"mtop.idle.web.manage.item.copy", "1.0", "{\"sourceItemId\":\"" + itemId + "\"}"},
            // 批量上下架
            {"mtop.taobao.idlemanage.item.batch.upshelf", "1.0", "{\"itemIds\":\"" + itemId + "\"}"},
            {"mtop.idle.web.manage.item.batch.upshelf", "1.0", "{\"itemIds\":\"" + itemId + "\"}"},
            {"mtop.taobao.idlemanage.item.batch.downshelf", "1.0", "{\"itemIds\":\"" + itemId + "\"}"},
            // 分类
            {"mtop.taobao.idlemanage.category.list", "1.0", "{\"parentId\":\"0\"}"},
            {"mtop.idle.web.manage.category.tree", "1.0", "{}"},
            // 详情/全信息
            {"mtop.taobao.idlemanage.item.fullinfo", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            {"mtop.idle.web.manage.item.fullinfo", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            // 浏览统计
            {"mtop.taobao.idlemanage.item.viewstats", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
            {"mtop.idle.web.manage.item.viewstats", "1.0", "{\"itemId\":\"" + itemId + "\"}"},
        };

        for (int i = 0; i < candidates.length; i++) {
            String api = candidates[i][0];
            String version = candidates[i][1];
            String data = candidates[i][2];
            try {
                JsonNode r = client.callMtop(api, version, data);
                String ret = r != null ? r.path("ret").toString() : "null";
                String body = m.writeValueAsString(r);
                String preview = body.length() > 300 ? body.substring(0, 300) + "..." : body;
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
