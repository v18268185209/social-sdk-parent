package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class VerifyCollect {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);

        // 从 JS 源码提取的确切路径
        String[][] tests = {
            // 已验证的
            {"mtop.taobao.idle.web.favor.item.list", "1.0", "list"},
            {"com.taobao.idle.unfavor.item", "1.0", "unfavor"},
            // JS 源码显示的添加收藏
            {"mtop.taobao.idle.collect.item", "1.0", "collect_add"},
            {"com.taobao.idle.collect.item", "1.0", "collect_add_com"},
            // 关注系列（基于命名规律推导）
            {"mtop.taobao.idle.user.follow.list", "1.0", "follow_list_mtop"},
            {"mtop.idle.web.user.follow.list", "1.0", "follow_list_web"},
            {"com.taobao.idle.follow.user.list", "1.0", "follow_list_com"},
            {"mtop.taobao.idle.web.user.follow.list", "1.0", "follow_list_web2"},
            {"mtop.taobao.idle.user.follow.add", "1.0", "follow_add_mtop"},
            {"mtop.taobao.idle.web.user.follow.add", "1.0", "follow_add_web"},
            {"com.taobao.idle.follow.user.add", "1.0", "follow_add_com"},
            {"com.taobao.idle.unfollow.user", "1.0", "unfollow_com"},
            {"mtop.taobao.idle.user.unfollow", "1.0", "unfollow_mtop"},
        };

        for (String[] test : tests) {
            String api = test[0], version = test[1], desc = test[2];
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                if (desc.contains("collect") || desc.contains("unfavor") || desc.contains("follow_add") || desc.contains("unfollow")) {
                    data.put("itemId", "1040285795675");
                    data.put("targetId", "1040285795675");
                } else {
                    // list 接口
                    data.put("pageNumber", "1");
                    data.put("pageSize", "5");
                    data.put("type", "DEFAULT");
                }
                JsonNode r = facade.callMtop(api, version, m.writeValueAsString(data));
                String ret = retStr(r);
                String marker = ret.contains("API_NOT_FOUNDED") ? "  " : "★ ";
                System.out.println(marker + desc + " [" + api + "] => " + ret.substring(0, Math.min(80, ret.length())));
            } catch (Exception e) {
                System.out.println("  " + desc + " [" + api + "] => ERR: " + e.getMessage());
            }
        }
    }

    private static String retStr(JsonNode r) {
        if (r == null) return "null";
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return "";
        return ret.get(0).asText("");
    }
}
