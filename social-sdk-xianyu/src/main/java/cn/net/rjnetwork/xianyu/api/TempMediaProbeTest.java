package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：用 CDP cookie 探测闲鱼媒体上传类接口的真实接口名（media/upload 域）。 */
public class TempMediaProbeTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();
        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);

        // 命名规律候选：media/upload 域（用极小 dummy body 避免截断问题影响接口名探测）
        String dummyBody = "{\"file\":{\"name\":\"test.jpg\",\"type\":\"image/jpeg\",\"uuid\":\"abcdef0123456789\"}}";
        String[][] candidates = {
            {"mtop.idle.pc.idleitem.media.upload", "1.0", dummyBody},
            {"mtop.idle.media.upload", "1.0", dummyBody},
            {"mtop.idle.web.media.upload", "1.0", dummyBody},
            {"mtop.idle.web.xyh.media.upload", "1.0", dummyBody},
            {"mtop.taobao.idle.media.upload", "1.0", dummyBody},
            {"mtop.taobao.idle.pc.media.upload", "1.0", dummyBody},
            {"mtop.taobao.idlemanage.media.upload", "1.0", dummyBody},
            {"mtop.idle.web.publish.media.upload", "1.0", dummyBody},
            {"mtop.taobao.idle.publish.media.upload", "1.0", dummyBody},
            {"com.taobao.idle.media.upload", "1.0", dummyBody},
            {"com.taobao.idle.publish.media.upload", "1.0", dummyBody},
            // oss 上传签名授权类（拿到 oss 上传 token +回调）
            {"mtop.idle.web.media.upload.token", "1.0", "{}"},
            {"mtop.idle.web.media.upload.auth", "1.0", "{}"},
            {"mtop.idle.web.publish.media.auth", "1.0", "{}"},
            {"mtop.taobao.idle.media.upload.auth", "1.0", "{}"},
            {"mtop.idle.web.media.upload.pre", "1.0", "{}"},
            {"mtop.idle.web.media.upload.commit", "1.0", "{\"fileId\":\"test\"}"},
            {"mtop.idle.web.media.upload.finish", "1.0", "{\"fileId\":\"test\"}"},
        };

        for (int i = 0; i < candidates.length; i++) {
            String api = candidates[i][0];
            String version = candidates[i][1];
            String data = candidates[i][2];
            try {
                JsonNode r = client.callMtop(api, version, data);
                String ret = r != null ? r.path("ret").toString() : "null";
                String body = m.writeValueAsString(r);
                String preview = body.length() > 400 ? body.substring(0, 400) + "..." : body;
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
