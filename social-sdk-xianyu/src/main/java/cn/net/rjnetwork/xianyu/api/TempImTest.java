package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：用 CDP cookie 调 accs 长连接拉消息历史 + 发一条测试消息，验证走通。 */
public class TempImTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();

        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);
        XianyuMessageApiService message = new XianyuMessageApiService(client);

        // 1. 拉历史（用真实抓到的 cid 63620203412@goofish，对应"致新科技"会话）
        System.out.println("\n========== 1. getMessageHistory cid=63620203412@goofish ==========");
        try {
            JsonNode r = message.getMessageHistory("63620203412@goofish", 5);
            String body = r != null ? m.writeValueAsString(r) : "null";
            System.out.println("ret=" + (r != null ? r.path("headers").path("mid").asText("") : "null"));
            System.out.println("body=" + (body.length() > 1500 ? body.substring(0, 1500) + "..." : body));
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        // 2. 发一条测试消息（同会话，对方 uid 2215024781926@goofish 是自己）
        System.out.println("\n========== 2. sendMessage cid=63620203412@goofish ==========");
        try {
            JsonNode r = message.sendMessage("63620203412@goofish", "【SDK测试消息请忽略】", "2215024781926@goofish");
            String body = r != null ? m.writeValueAsString(r) : "null";
            System.out.println("ret=" + (r != null ? r.path("headers").path("mid").asText("") : "null"));
            System.out.println("body=" + (body.length() > 1500 ? body.substring(0, 1500) + "..." : body));
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        System.out.println("\n========== DONE ==========");
    }
}
