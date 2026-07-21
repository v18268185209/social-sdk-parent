package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeepBruteForce {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("==== 深度暴力枚举 ====");
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);

        String[][] paths = {
            // 基于 unfavor.item 反推 - 可能是同一命名体系
            {"mtop.taobao.idle.unfavor.item", "1.0"}, // 确认取消收藏
            {"mtop.taobao.idle.unfavor", "1.0"},     // 取消收藏的短路径
            {"com.taobao.idle.unfavor", "1.0"},      // 取消收藏的短路径
            {"com.taobao.idle.unfavor.item.add", "1.0"}, // add = 关注添加
            {"mtop.taobao.idle.unfollow.user", "1.0"}, // 取消关注
            {"mtop.taobao.idle.unfollow", "1.0"},     // 取消关注短路径
            
            // 添加收藏可能是同一个 mtop 路径但不同参数（action 参数）
            {"mtop.taobao.idle.favor.item.collect", "1.0"},
            {"mtop.taobao.idle.favor.item.uncollect", "1.0"},
            {"mtop.taobao.idle.favor.item.add", "1.0"},
            {"mtop.taobao.idle.favor.addcollect", "1.0"},
            {"mtop.taobao.idle.favor.addcollect.item", "1.0"},
            {"mtop.taobao.idle.favor.item.favorite", "1.0"},
            {"mtop.taobao.idle.favorite.item", "1.0"},
            
            // 不同版本
            {"mtop.taobao.idle.unfavor.item", "2.0"},
            
            // 关注相关
            {"mtop.taobao.idle.follow.add", "1.0"},
            {"mtop.taobao.idle.follow.item.add", "1.0"},
            {"mtop.taobao.idle.unfollow.add", "1.0"},
            {"mtop.taobao.idle.unfollow.remove", "1.0"},
            {"mtop.taobao.idle.user.follow.add", "1.0"},
            
            // 可能完全不同的路径
            {"mtop.idle.web.collect.item.add", "1.0"},
            {"mtop.idle.web.favor.item.add", "1.0"},
            {"mtop.idle.web.collect.add", "1.0"},
            {"mtop.idle.web.favor.add", "1.0"},
            {"mtop.idle.web.user.follow.add", "1.0"},
            {"mtop.idle.web.user.collect", "1.0"},
            {"mtop.idle.web.collect.item.update", "1.0"},
            
            // 用 add 替代 delete
            {"mtop.taobao.idle.collect.item.add", "1.0"},
            {"mtop.taobao.idle.favorite.item.add", "1.0"},
            {"mtop.taobao.idle.favorites.add", "1.0"},
            {"com.taobao.idle.collect.item.add", "1.0"},
            {"com.taobao.idle.favorite.item.add", "1.0"},
            {"com.taobao.idle.favorites.add", "1.0"},
        };

        for (String[] p : paths) {
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("itemId", "1049469752692");
                data.put("action", "add");  // 可能区分 add/remove
                data.put("op", "add");
                JsonNode r = facade.callMtop(p[0], p[1], m.writeValueAsString(data));
                String ret = retStr(r);
                if (!ret.contains("API_NOT_FOUNDED")) {
                    System.out.println("*** [" + p[0] + " v=" + p[1] + "] => " + ret);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        
        System.out.println("\n==== 完成 ====");
    }

    private static String retStr(JsonNode r) {
        if (r == null) return "null";
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return r.toString().substring(0, Math.min(100, r.toString().length()));
        return ret.get(0).asText("");
    }
}
