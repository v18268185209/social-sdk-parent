package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼商品管理 API 服务
 * 封装商品列表、详情、搜索、上下架等 MTOP 接口调用
 *
 * <p>所有业务参数通过 data JSON 传递，底层 XianyuMtopApiClient 自动计算 sign、预热 token、
 * 设置 Referer/Origin，无需手动构造 URL 和签名。</p>
 */
public class XianyuProductApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuProductApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 搜索商品 — mtop.taobao.idlemtopsearch.pc.search */
    public JsonNode searchProducts(String keyword, String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword != null ? keyword : "");
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlemtopsearch.pc.search", toJson(data));
    }

    /**
     * 获取我的商品列表 — 真实接口 mtop.idle.web.xyh.item.list
     * <p>真实抓包验证（2026-07-18 CDP）：</p>
     * <ul>
     *   <li>POST https://h5api.m.goofish.com/h5/mtop.idle.web.xyh.item.list/1.0/</li>
     *   <li>data: {"needGroupInfo":true,"pageNumber":1,"userId":"2215024781926","pageSize":20}</li>
     *   <li>userId 从 cookie 的 unb 字段解析，cookie 里形如 `unb=2215024781926`</li>
     *   <li>返回 data.cardList[] → 每项 cardData 含 id/itemId/title/soldPrice/priceInfo.price/categoryId/itemStatus/picInfo.picUrl/detailUrl</li>
     * </ul>
     *
     * @param pageNumber 页码，从 1 开始
     * @param pageSize 每页条数，默认 20
     */
    public JsonNode getMyProducts(String pageNumber, String pageSize) {
        String userId = extractUserIdFromCookie();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("needGroupInfo", true);
        data.put("pageNumber", pageNumber != null ? pageNumber : "1");
        data.put("userId", userId);
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.idle.web.xyh.item.list", toJson(data));
    }

    /**
     * 兼容旧调用：page/pageSize 形式
     */
    public JsonNode getMyProducts(int pageNumber, int pageSize) {
        return getMyProducts(String.valueOf(Math.max(1, pageNumber)), String.valueOf(Math.max(1, pageSize)));
    }

    /**
     * 从 cookie 的 unb 字段解析当前登录用户 id。
     * 闲鱼 cookie 里形如 `unb=2215024781926`，是商品列表接口必需的 userId。
     * 找不到时返回空字符串（接口会按匿名处理）。
     */
    private String extractUserIdFromCookie() {
        String cookie = apiClient.getCookie();
        if (cookie == null || cookie.isEmpty()) return "";
        for (String part : cookie.split(";")) {
            String p = part.trim();
            if (p.startsWith("unb=")) {
                String v = p.substring(4).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return "";
    }

    /**
     * 获取商品详情 — 真实接口 mtop.taobao.idle.pc.detail
     * <p>真实抓包验证（2026-07-18 CDP）：</p>
     * <ul>
     *   <li>POST https://h5api.m.goofish.com/h5/mtop.taobao.idle.pc.detail/1.0/</li>
     *   <li>data: {"itemId":"1042782385557"} （从商品详情页 https://www.goofish.com/item?id={itemId}&categoryId={cid} 触发）</li>
     *   <li>返回 data.b2cItemDO（含 wantBuyCount/browseCnt/templateId 等）/ data.picDetailDO / data.logisticsDO / data.trackParams（含 itemId/sellerOptions/buyerOptions 等）</li>
     * </ul>
     */
    public JsonNode getProductDetail(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.pc.detail", toJson(data));
    }

    /**
     * 商品上架/下架 — 真实接口 mtop.taobao.idle.item.downshelf / upshelf
     * <p>真实抓包验证（2026-07-19 CDP 抓 React onClick handler 源码）：</p>
     * <ul>
     *   <li>闲鱼详情页"下架"按钮的 React onClick handler 源码里直接调 ev.G({api, v, data})</li>
     *   <li>下架：api="mtop.taobao.idle.item.downshelf" v="2.0" data={itemId}</li>
     *   <li>上架：按命名规律姊妹接口为 "mtop.taobao.idle.item.upshelf" v="2.0" data={itemId}</li>
     *   <li>handler 内部含 confirm 弹窗"确定要下架这个宝贝侣？" + 成功后 toast"下架成功"</li>
     * </ul>
     *
     * @param itemId 闲鱼商品 id（如 "1042782385557"）
     * @param action "onsale"=上架 / "offsale"=下架
     */
    public JsonNode updateProductStatus(String itemId, String action) {
        // 真实接口名按 action 分：下架 downshelf / 上架 upshelf
        String api;
        if ("offsale".equalsIgnoreCase(action) || "off".equalsIgnoreCase(action) || "下架".equals(action)) {
            api = "mtop.taobao.idle.item.downshelf";
        } else {
            api = "mtop.taobao.idle.item.upshelf";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop(api, "2.0", toJson(data));
    }

    /** 首页 Feed 流 — mtop.taobao.idlehome.home.webpc.feed */
    public JsonNode getHomeFeed() {
        return apiClient.callMtop("mtop.taobao.idlehome.home.webpc.feed", "{}");
    }

    /** 搜索激活 — mtop.taobao.idlemtopsearch.pc.item.search.activate */
    public JsonNode activateSearch(String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword != null ? keyword : "");
        return apiClient.callMtop("mtop.taobao.idlemtopsearch.pc.item.search.activate", toJson(data));
    }

    /** 搜索遮罩 — mtop.taobao.idlemtopsearch.pc.search.shade */
    public JsonNode getSearchShade() {
        return apiClient.callMtop("mtop.taobao.idlemtopsearch.pc.search.shade", "{}");
    }

    private static String toJson(Map<String, ?> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
