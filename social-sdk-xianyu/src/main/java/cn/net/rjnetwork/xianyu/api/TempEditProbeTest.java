package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 临时真实测试：用 CDP cookie 探测闲鱼商品编辑类接口的真实接口名。
 * 按 com.taobao.idle.item.* / mtop.taobao.idle.item.* 命名规律穷举候选，
 * 看哪个返回 SUCCESS（而不是 API_NOT_EXIST）。
 */
public class TempEditProbeTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);
        String itemId = "1042782385557"; // 真实商品 id（已下架态，适合测删除/编辑）

        // 候选接口名 + 候选 data + 候选 version
        // 已真验：downshelf=mtop.taobao.idle.item.downshelf v2.0, delete=com.taobao.idle.item.delete v1.1
        String[][] candidates = {
            // 上架（downshelf 妹妹）
            {"mtop.taobao.idle.item.upshelf", "2.0", "{\"itemId\":\"" + itemId + "\"}"},
            {"com.taobao.idle.item.upshelf", "1.1", "{\"itemId\":\"" + itemId + "\"}"},
            {"mtop.taobao.idle.item.onsale", "2.0", "{\"itemId\":\"" + itemId + "\"}"},
            // 编辑
            {"com.taobao.idle.item.edit", "1.1", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试编辑\"}"},
            {"mtop.taobao.idle.item.edit", "1.0", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试编辑\"}"},
            {"com.taobao.idle.item.update", "1.1", "{\"itemId\":\"" + itemId + "\",\"title\":\"测试编辑\"}"},
            // 改价
            {"com.taobao.idle.item.price.update", "1.1", "{\"itemId\":\"" + itemId + "\",\"price\":\"599\"}"},
            {"com.taobao.idle.item.price", "1.1", "{\"itemId\":\"" + itemId + "\",\"price\":\"599\"}"},
            {"mtop.taobao.idle.item.price.update", "1.0", "{\"itemId\":\"" + itemId + "\",\"price\":\"599\"}"},
            // 改库存
            {"com.taobao.idle.item.stock.update", "1.1", "{\"itemId\":\"" + itemId + "\",\"stock\":\"1\"}"},
            {"com.taobao.idle.item.stock", "1.1", "{\"itemId\":\"" + itemId + "\",\"stock\":\"1\"}"},
            // 复制
            {"com.taobao.idle.item.copy", "1.1", "{\"sourceItemId\":\"" + itemId + "\"}"},
            {"mtop.taobao.idle.item.copy", "1.0", "{\"sourceItemId\":\"" + itemId + "\"}"},
            // 批量上下架
            {"mtop.taobao.idle.item.batch.upshelf", "2.0", "{\"itemIds\":\"" + itemId + "\"}"},
            {"com.taobao.idle.item.batch.upshelf", "1.1", "{\"itemIds\":\"" + itemId + "\"}"},
            {"mtop.taobao.idle.item.batch.downshelf", "2.0", "{\"itemIds\":\"" + itemId + "\"}"},
            // 详情/全信息
            {"com.taobao.idle.item.detail", "1.1", "{\"itemId\":\"" + itemId + "\"}"},
            {"com.taobao.idle.item.fullinfo", "1.1", "{\"itemId\":\"" + itemId + "\"}"},
            // 浏览统计
            {"com.taobao.idle.item.viewstats", "1.1", "{\"itemId\":\"" + itemId + "\"}"},
            // 分类
            {"com.taobao.idle.category.list", "1.1", "{\"parentId\":\"0\"}"},
            {"com.taobao.idle.category.tree", "1.1", "{}"},
            {"com.taobao.idle.category.recommend", "1.1", "{\"title\":\"测试\"}"},
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
