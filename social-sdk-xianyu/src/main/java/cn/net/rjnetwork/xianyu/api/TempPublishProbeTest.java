package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：用 CDP cookie 探测闲鱼发布类接口的真实接口名（publish/category 域）。 */
public class TempPublishProbeTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);

        // 命名规律候选：publish/category/preData/suggest 域
        String[][] candidates = {
            // 类目树
            {"mtop.taobao.idle.publish.category.tree", "1.0", "{}"},
            {"mtop.idle.web.publish.category.tree", "1.0", "{}"},
            {"mtop.taobao.idlemanage.category.tree", "1.0", "{}"},
            {"com.taobao.idle.publish.category.tree", "1.0", "{}"},
            {"mtop.taobao.idle.publish.category.list", "1.0", "{\"parentId\":\"0\"}"},
            {"mtop.idle.web.publish.category.list", "1.0", "{\"parentId\":\"0\"}"},
            // 发布预载数据
            {"mtop.taobao.idle.publish.predata", "1.0", "{}"},
            {"mtop.idle.web.publish.predata", "1.0", "{}"},
            {"mtop.taobao.idle.publish.pre.get", "1.0", "{}"},
            {"mtop.idle.web.publish.pre.get", "1.0", "{}"},
            // 创建商品
            {"mtop.taobao.idle.publish.item.create", "1.0", "{\"title\":\"测试\"}"},
            {"mtop.idle.web.publish.item.create", "1.0", "{\"title\":\"测试\"}"},
            {"com.taobao.idle.publish.item.create", "1.0", "{\"title\":\"测试\"}"},
            {"mtop.taobao.idle.publish.create", "1.0", "{\"title\":\"测试\"}"},
            {"mtop.idle.web.publish.create", "1.0", "{\"title\":\"测试\"}"},
            // 存草稿
            {"mtop.taobao.idle.publish.draft.save", "1.0", "{\"title\":\"测试\"}"},
            {"mtop.idle.web.publish.draft.save", "1.0", "{\"title\":\"测试\"}"},
            {"mtop.taobao.idle.publish.draft.list", "1.0", "{}"},
            {"mtop.idle.web.publish.draft.list", "1.0", "{}"},
            // 价格建议
            {"mtop.taobao.idle.publish.price.suggest", "1.0", "{\"title\":\"测试\"}"},
            {"mtop.idle.web.publish.price.suggest", "1.0", "{\"title\":\"测试\"}"},
            // 运费模板
            {"mtop.taobao.idle.publish.freight.template", "1.0", "{}"},
            {"mtop.idle.web.publish.freight.template", "1.0", "{}"},
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
