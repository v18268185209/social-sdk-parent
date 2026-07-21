package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 利用已发现的 com.taobao.idle.unfavor.item 规律，暴力枚举收藏/关注接口
 */
public class FindAddFavor {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("==== 暴力枚举收藏/关注接口 ====");
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);

        // 根据 unfavor.item 推测相关接口
        String[][] candidates = {
            // 添加收藏
            {"com.taobao.idle.favor.item", "1.0"},
            {"com.taobao.idle.collect.item", "1.0"},
            {"com.taobao.idle.addcollect.item", "1.0"},
            {"com.taobao.idle.addfavor.item", "1.0"},
            {"com.taobao.idle.favor.add", "1.0"},
            {"com.taobao.idle.collect.add", "1.0"},
            // 关注用户
            {"com.taobao.idle.follow.user", "1.0"},
            {"com.taobao.idle.follow.add", "1.0"},
            {"com.taobao.idle.user.follow", "1.0"},
            {"com.taobao.idle.unfollow.user", "1.0"},
            {"com.taobao.idle.unfollow.add", "1.0"},
            // 长路径
            {"mtop.taobao.idle.favor.addcollect", "1.0"},
            {"mtop.taobao.idle.favor.addcollect.item", "1.0"},
            {"mtop.taobao.idle.unfavor.uncollect", "1.0"},
            {"mtop.taobao.idle.unfavor.unfollow", "1.0"},
            // 直接拼路径
            {"com.taobao.idle.favor.item.add", "1.0"},
            {"com.taobao.idle.collect.item.add", "1.0"},
            {"com.taobao.idle.user.follow.add", "1.0"},
            {"com.taobao.idle.user.unfollow", "1.0"},
            {"com.taobao.idle.userinfo.updatefavor", "1.0"},
            {"com.taobao.idle.message.favor.add", "1.0"},
        };

        for (String[] c : candidates) {
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("itemId", "1049469752692");
                data.put("targetId", "1049469752692");
                JsonNode r = facade.callMtop(c[0], c[1], m.writeValueAsString(data));
                String ret = retStr(r);
                if (!ret.contains("API_NOT_FOUNDED")) {
                    System.out.println("*** [" + c[0] + " v=" + c[1] + "] => " + ret);
                } else {
                    System.out.println("[    " + c[0] + " v=" + c[1] + "] => API_NOT_FOUNDED");
                }
            } catch (Exception e) {
                System.out.println("[ERR " + c[0] + " v=" + c[1] + "] => " + e.getMessage());
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
