package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class CollectRealCapture {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("==== 真抓收藏接口 v2 ====");
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);

        // 暴力枚举所有可能路径
        String[][] paths = {
            // 标准推测 + v2
            {"mtop.taobao.idle.web.favor.item.add", "1.0"},
            {"mtop.taobao.idle.web.favor.item.add", "2.0"},
            {"mtop.taobao.idle.web.favor.add", "1.0"},
            {"mtop.taobao.idle.web.favor.add", "2.0"},
            {"mtop.taobao.idle.favor.item.add", "1.0"},
            {"mtop.taobao.idle.favor.item.add", "2.0"},
            {"mtop.taobao.idle.favor.add", "1.0"},
            {"mtop.taobao.idle.favor.add", "2.0"},
            // 不同版本
            {"mtop.taobao.idle.web.favor.item.update", "1.0"},
            {"mtop.taobao.idle.web.favor.item.save", "1.0"},
            {"mtop.taobao.idle.web.favor.item.create", "1.0"},
            {"mtop.taobao.idle.web.favor.item.collect", "1.0"},
            // 不同前缀
            {"mtop.taobao.idle.web.favorites.add", "1.0"},
            {"mtop.taobao.idle.web.favorites.item.add", "1.0"},
            {"mtop.taobao.idle.web.follow.item.add", "1.0"},
            {"mtop.taobao.idle.web.follow.add", "1.0"},
            {"mtop.taobao.idle.web.like.item.add", "1.0"},
            {"mtop.taobao.idle.web.like.add", "1.0"},
            // 参考 item 系列模式
            {"mtop.taobao.idle.web.item.favor.add", "1.0"},
            {"mtop.taobao.idle.web.item.favorite.add", "1.0"},
            {"mtop.taobao.idle.web.item.collect", "1.0"},
            {"mtop.taobao.idle.web.item.like", "1.0"},
            // 带 user 前缀
            {"mtop.taobao.idle.web.user.favor.item.add", "1.0"},
            {"mtop.taobao.idle.web.user.favorites.add", "1.0"},
            {"mtop.taobao.idle.web.user.collect.item.add", "1.0"},
        };

        for (String[] p : paths) {
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("itemId", "1042782385557");
                data.put("item_id", "1042782385557");
                data.put("id", "1042782385557");
                data.put("type", "DEFAULT");
                JsonNode r = facade.callMtop(p[0], p[1], m.writeValueAsString(data));
                String ret = retStr(r);
                // 过滤掉纯 API_NOT_FOUNDED
                if (!ret.contains("API_NOT_FOUNDED") && !ret.isEmpty()) {
                    System.out.println("*** [" + p[0] + " v=" + p[1] + "] => " + ret);
                } else {
                    System.out.println("[    " + p[0] + " v=" + p[1] + "] => " + ret.substring(0, Math.min(60, ret.length())));
                }
            } catch (Exception e) {
                System.out.println("[ERR " + p[0] + " v=" + p[1] + "] => " + e.getMessage());
            }
        }

        System.out.println("\n==== 完成 ====");
    }

    private static String retStr(JsonNode r) {
        if (r == null) return "null";
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return r.toString().substring(0, Math.min(200, r.toString().length()));
        return ret.get(0).asText("");
    }
}
