package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼商品发布 API 服务
 * 封装商品创建、草稿、图片上传等 MTOP 接口调用
 */
public class XianyuPublishApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuPublishApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 创建商品 — mtop.taobao.idlehome.item.create
     * 业务参数通过 data JSON 传递，底层自动计算 sign、预热 token。
     */
    public JsonNode createProduct(String title, String price, String description,
                                   String categoryId, String images) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("price", price != null ? price : "");
        data.put("description", description != null ? description : "");
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("images", images != null ? images : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.create", toJson(data));
    }

    /**
     * 创建商品（JSON body 方式）
     */
    public JsonNode createProductWithBody(String title, String price, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("price", price != null ? price : "");
        data.put("description", description != null ? description : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.create", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
