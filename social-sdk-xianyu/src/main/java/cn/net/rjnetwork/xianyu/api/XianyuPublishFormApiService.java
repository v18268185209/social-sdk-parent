package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼商品发布表单 API 服务
 * 封装商品发布时的表单数据准备、草稿保存、智能分类推荐等 MTOP 接口调用
 *
 * <p>所有业务参数通过 data JSON 传递，底层 XianyuMtopApiClient 自动计算 sign、预热 token、
 * 设置 Referer/Origin，无需手动构造 URL 和签名。</p>
 */
public class XianyuPublishFormApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuPublishFormApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 发布前数据准备 ====================

    /** 获取发布页预加载数据 — mtop.idle.pc.idleitem.preget */
    public JsonNode getPublishPreData() {
        return apiClient.callMtop("mtop.idle.pc.idleitem.preget", "{}");
    }

    /** 获取默认发货地址 — mtop.taobao.idle.local.poi.get */
    public JsonNode getDefaultLocation() {
        return apiClient.callMtop("mtop.taobao.idle.local.poi.get", "{}");
    }

    /** 获取价格模板/历史定价 — mtop.taobao.idleprice.template.get */
    public JsonNode getPriceTemplate() {
        return apiClient.callMtop("mtop.taobao.idleprice.template.get", "{}");
    }

    // ==================== 草稿管理 ====================

    /** 保存商品草稿 — mtop.taobao.idlehome.item.draft.save */
    public JsonNode saveDraft(String title, String description, String price,
                               String categoryId, String images,
                               String location, String shippingType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        data.put("price", price != null ? price : "");
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("images", images != null ? images : "");
        data.put("location", location != null ? location : "");
        data.put("shippingType", shippingType != null ? shippingType : "0");
        return apiClient.callMtop("mtop.taobao.idlehome.item.draft.save", toJson(data));
    }

    /** 获取我的草稿列表 — mtop.taobao.idlehome.item.draft.list */
    public JsonNode getDraftList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlehome.item.draft.list", toJson(data));
    }

    /** 删除草稿 — mtop.taobao.idlehome.item.draft.delete */
    public JsonNode deleteDraft(String draftId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("draftId", draftId != null ? draftId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.draft.delete", toJson(data));
    }

    /** 加载草稿内容 — mtop.taobao.idlehome.item.draft.get */
    public JsonNode loadDraft(String draftId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("draftId", draftId != null ? draftId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.draft.get", toJson(data));
    }

    // ==================== 智能分类 ====================

    /** AI 智能识别分类 — mtop.taobao.idlecategory.ai.recommend */
    public JsonNode aiRecommendCategory(String title, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        return apiClient.callMtop("mtop.taobao.idlecategory.ai.recommend", toJson(data));
    }

    /** 获取分类树 — mtop.taobao.idlecategory.tree.get */
    public JsonNode getCategoryTree(String level) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", level != null ? level : "1");
        return apiClient.callMtop("mtop.taobao.idlecategory.tree.get", toJson(data));
    }

    // ==================== 价格智能建议 ====================

    /** 获取同类商品价格建议 — mtop.taobao.idleprice.suggest */
    public JsonNode suggestPrice(String categoryId, String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("keyword", keyword != null ? keyword : "");
        return apiClient.callMtop("mtop.taobao.idleprice.suggest", toJson(data));
    }

    // ==================== 运费模板 ====================

    /** 获取运费模板列表 — mtop.taobao.idleshipping.template.list */
    public JsonNode getShippingTemplates() {
        return apiClient.callMtop("mtop.taobao.idleshipping.template.list", "{}");
    }

    /** 设置发货方式 — mtop.taobao.idleshipping.method.set */
    public JsonNode setShippingMethod(String method) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", method != null ? method : "0");
        return apiClient.callMtop("mtop.taobao.idleshipping.method.set", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
