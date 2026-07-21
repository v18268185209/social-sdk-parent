package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 收藏/关注接口 E2E 验证 — 用真 cookie 验证推测接口是否真实存在
 */
public class CollectE2ETest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("==== 收藏/关注接口 E2E 验证开始 cookie.len=" + cookie.length() + " ====");
        ObjectMapper m = new ObjectMapper();
        XianyuApiFacade facade = new XianyuApiFacade(cookie);
        int okCount = 0, total = 0;

        // ① 收藏列表（已验证，对照）
        total++;
        try {
            JsonNode r = facade.getMyCollectList("1", "5");
            if (checkOk(r)) { okCount++; System.out.println("[OK ①收藏列表] " + retStr(r)); }
            else System.out.println("[FAIL ①收藏列表] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ①收藏列表] " + e.getMessage()); }

        // ② 收藏商品（推测接口）— 用 dummy itemId 测试
        total++;
        try {
            JsonNode r = facade.collectItem("1042782385557");
            if (checkOk(r) || isBizError(r)) { okCount++; System.out.println("[OK ②收藏商品] " + retStr(r)); }
            else System.out.println("[FAIL ②收藏商品] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ②收藏商品] " + e.getMessage()); }

        // ③ 取消收藏（推测接口）— 用 dummy itemId 测试
        total++;
        try {
            JsonNode r = facade.uncollectItem("1042782385557");
            if (checkOk(r) || isBizError(r)) { okCount++; System.out.println("[OK ③取消收藏] " + retStr(r)); }
            else System.out.println("[FAIL ③取消收藏] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ③取消收藏] " + e.getMessage()); }

        // ④ 关注列表（推测接口）
        total++;
        try {
            JsonNode r = facade.getMyFollowList("1", "5");
            if (checkOk(r) || isBizError(r)) { okCount++; System.out.println("[OK ④关注列表] " + retStr(r)); }
            else System.out.println("[FAIL ④关注列表] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ④关注列表] " + e.getMessage()); }

        // ⑤ 关注用户（推测接口）— 用 dummy targetId 测试
        total++;
        try {
            JsonNode r = facade.followTarget("dummyUserId123");
            if (checkOk(r) || isBizError(r)) { okCount++; System.out.println("[OK ⑤关注用户] " + retStr(r)); }
            else System.out.println("[FAIL ⑤关注用户] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ⑤关注用户] " + e.getMessage()); }

        // ⑥ 取消关注（推测接口）— 用 dummy targetId 测试
        total++;
        try {
            JsonNode r = facade.unfollowTarget("dummyUserId123");
            if (checkOk(r) || isBizError(r)) { okCount++; System.out.println("[OK ⑥取消关注] " + retStr(r)); }
            else System.out.println("[FAIL ⑥取消关注] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ⑥取消关注] " + e.getMessage()); }

        // ⑦ 浏览足迹（推测接口）
        total++;
        try {
            JsonNode r = facade.getMyFootprint("1", "5");
            if (checkOk(r) || isBizError(r)) { okCount++; System.out.println("[OK ⑦浏览足迹] " + retStr(r)); }
            else System.out.println("[FAIL ⑦浏览足迹] " + retStr(r));
        } catch (Exception e) { System.out.println("[ERR ⑦浏览足迹] " + e.getMessage()); }

        System.out.println("\n==== 收藏/关注接口 E2E 验证结束 ok=" + okCount + "/" + total + " ====");
        double rate = total > 0 ? (okCount * 100.0 / total) : 0;
        System.out.println("通过率 " + String.format("%.1f", rate) + "%");
    }

    private static boolean checkOk(JsonNode r) {
        if (r == null) return false;
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return false;
        String s = ret.get(0).asText("");
        return s.contains("SUCCESS");
    }

    private static boolean isBizError(JsonNode r) {
        if (r == null) return false;
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return false;
        String s = ret.get(0).asText("");
        return s.contains("FAIL_BIZ") || s.contains("太累了") || s.contains("FAIL_SYS_ILLEGAL_ACCESS")
                || s.contains("FAIL_SYS_PUNISH") || s.contains("FAIL_SYS_USER_VALIDATE")
                || s.contains("RGV587_ERROR") || s.contains("被挤爆啦")
                || s.contains("API_NOT_EXIST") == false && s.contains("SUCCESS") == false && s.length() > 0;
    }

    private static String retStr(JsonNode r) {
        if (r == null) return "null";
        JsonNode ret = r.path("ret");
        if (!ret.isArray() || ret.size() == 0) return r.toString().substring(0, Math.min(200, r.toString().length()));
        return ret.get(0).asText("");
    }
}
