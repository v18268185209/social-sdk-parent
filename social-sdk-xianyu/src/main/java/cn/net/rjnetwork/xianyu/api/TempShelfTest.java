package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 临时真实测试：验证上下架接口调通。下架→查状态→上架回原状态→查状态。 */
public class TempShelfTest {
    public static void main(String[] args) throws Exception {
        String cookie = Files.readString(Paths.get(".workbuddy/cookie.txt")).trim();
        System.out.println("[TEST] cookie length=" + cookie.length());
        ObjectMapper m = new ObjectMapper();

        XianyuMtopApiClient client = new XianyuMtopApiClient(cookie);
        XianyuProductApiService product = new XianyuProductApiService(client);
        String itemId = "1042782385557"; // 真实在售商品 id

        // 1. 下架
        System.out.println("\n========== 1. DOWN (offsale) ==========");
        JsonNode down = product.updateProductStatus(itemId, "offsale");
        System.out.println("ret=" + (down != null ? down.path("ret").toString() : "null"));
        System.out.println("body=" + (down != null ? m.writeValueAsString(down).substring(0, Math.min(800, m.writeValueAsString(down).length())) : "null"));

        // 等闲鱼服务端处理
        Thread.sleep(2000);

        // 2. 上架回原状态
        System.out.println("\n========== 2. UP (onsale) restore ==========");
        JsonNode up = product.updateProductStatus(itemId, "onsale");
        System.out.println("ret=" + (up != null ? up.path("ret").toString() : "null"));
        System.out.println("body=" + (up != null ? m.writeValueAsString(up).substring(0, Math.min(800, m.writeValueAsString(up).length())) : "null"));

        System.out.println("\n========== DONE ==========");
    }
}
