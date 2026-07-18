package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼商品发布 API 服务
 * 封装商品创建、草稿、图片上传等 MTOP 接口调用
 */
public class XianyuPublishApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuPublishApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 创建商品
     * API: mtop.taobao.idlehome.item.create
     */
    public JsonNode createProduct(String title, String price, String description, String categoryId, String images) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.create")
                .addParam("title", title)
                .addParam("price", price)
                .addParam("description", description)
                .addParam("categoryId", categoryId != null ? categoryId : "")
                .addParam("images", images != null ? images : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 创建商品（JSON body 方式）
     */
    public JsonNode createProductWithBody(String title, String price, String description) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.create")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        
        String body = "{\"title\":\"" + title + "\",\"price\":\"" + price + "\",\"description\":\"" + description + "\"}";
        return apiClient.postJson(url, body);
    }
}
