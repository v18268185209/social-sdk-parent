package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SingleVerify {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);

        // 系统化生成候选路径
        List<String> candidates = new ArrayList<>();
        String[] prefixes = {"mtop.taobao.idle.web", "mtop.taobao.idle", "mtop.idle.web", "mtop.idle", "com.taobao.idle.web", "com.taobao.idle"};
        String[] actions = {"favor.item", "favor", "favorite.item", "favorite", "collect.item", "collect", "favorites.item", "favorites", "follow.user", "follow", "user.follow.user", "unfollow.user"};
        String[] ops = {"add", "delete", "remove", "save", "create", "update"};

        for (String prefix : prefixes) {
            for (String action : actions) {
                // action (读接口 list)
                candidates.add(prefix + "." + action + ".list");
                for (String op : ops) {
                    // action.op (写接口)
                    candidates.add(prefix + "." + action + "." + op);
                }
                // op.action (另一种命名)
                for (String op : ops) {
                    candidates.add(prefix + "." + op + "." + action);
                }
            }
        }
        // 添加特殊已知接口
        candidates.add("com.taobao.idle.unfavor.item");
        candidates.add("mtop.taobao.idle.unfavor.item");
        
        int tested = 0;
        int hits = 0;
        for (String path : candidates) {
            tested++;
            for (String v : new String[]{"1.0", "2.0"}) {
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("itemId", "1040285795675");
                    data.put("action", "add");
                    data.put("type", "DEFAULT");
                    JsonNode r = facade.callMtop(path, v, m.writeValueAsString(data));
                    String ret = retStr(r);
                    if (ret.isEmpty() || ret.contains("API_NOT_FOUNDED")) continue;
                    hits++;
                    System.out.println("HIT: " + path + " v=" + v + " => " + ret);
                } catch (Exception e) {}
            }
        }
        System.out.println("\nTested: " + tested + ", Hits: " + hits);
    }

    private static String retStr(JsonNode r) {
        if (r == null) return "null";
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return "";
        return ret.get(0).asText("");
    }
}
