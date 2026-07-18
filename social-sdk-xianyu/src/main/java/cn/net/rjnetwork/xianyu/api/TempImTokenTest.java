package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：单独调 mtop.taobao.idlemessage.pc.login.token 看完整返回结构，定位 accessToken 字段。 */
public class TempImTokenTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();

        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);

        // 1. 直接调 login.token，看完整 body
        System.out.println("\n========== 1. pc.login.token data={} ==========");
        try {
            JsonNode r = client.callMtop("mtop.taobao.idlemessage.pc.login.token", "{}");
            String body = r != null ? m.writeValueAsString(r) : "null";
            System.out.println("ret=" + (r != null ? r.path("ret").toString() : "null"));
            System.out.println("body=" + (body.length() > 2000 ? body.substring(0, 2000) + "..." : body));
            // 关键字段探
            if (r != null) {
                JsonNode data = r.path("data");
                System.out.println("data.accessToken=" + data.path("accessToken").asText(""));
                System.out.println("data.access_token=" + data.path("access_token").asText(""));
                System.out.println("data.token=" + data.path("token").asText(""));
                System.out.println("data.tk=" + data.path("tk").asText(""));
                System.out.println("data.imToken=" + data.path("imToken").asText(""));
                System.out.println("data.im.token=" + data.path("im").path("token").asText(""));
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        System.out.println("\n========== DONE ==========");
    }
}
