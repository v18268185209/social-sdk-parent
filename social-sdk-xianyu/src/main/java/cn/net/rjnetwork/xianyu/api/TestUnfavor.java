package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestUnfavor {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("cookie.len=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);

        // 先验证已发现的 unfavor.item 是否真的能通过 SDK 调用
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", "1049469752692");
        
        String[] paths = {
            "com.taobao.idle.unfavor.item",  // 确认可用
            "com.taobao.idle.favor.item",    // 猜测添加收藏
            "com.taobao.idle.favor.add",     // 另一个猜测
            "com.taobao.idle.collect.item",  // collect 变体
            "com.taobao.idle.addcollect",    // 又一种变体
            "com.taobao.idle.favorite.item", // favorite 变体
            "com.taobao.idle.user.follow.add", // 关注
            "com.taobao.idle.follow.user",   // 另一种关注
        };
        
        for (String path : paths) {
            try {
                JsonNode r = facade.callMtop(path, "1.0", m.writeValueAsString(data));
                String ret = retStr(r);
                System.out.println(path + " => " + ret);
            } catch (Exception e) {
                System.out.println(path + " => ERR: " + e.getMessage());
            }
        }
    }

    private static String retStr(JsonNode r) {
        if (r == null) return "null";
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return r.toString().substring(0, Math.min(100, r.toString().length()));
        return ret.get(0).asText("");
    }
}
