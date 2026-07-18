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
    // 注意：以下发布类接口名均为命名规律候选，闲鱼发布 SPA 走闲鱼 App 内 WebView 域，
    // PC/H5 浏览器都抓不到真实接口名（三轮 SDK 推测共 67 个候选域全部 FAIL_SYS_API_NOT_FOUNDED）。
    // 已真验同域接口参考：商品列表 mtop.idle.web.xyh.item.list、订单 mtop.idle.web.trade.bought.list，
    // 推测发布走 mtop.idle.web.publish.* 域，待后续闲鱼 App WebView 真抓微调。

    /** 获取发布页预加载数据 — 命名规律候选 mtop.idle.web.publish.predata（未真抓） */
    public JsonNode getPublishPreData() {
        return apiClient.callMtop("mtop.idle.web.publish.predata", "{}");
    }

    /** 获取默认发货地址 — 命名规律候选 mtop.idle.web.publish.local.poi.get（未真抓） */
    public JsonNode getDefaultLocation() {
        return apiClient.callMtop("mtop.idle.web.publish.local.poi.get", "{}");
    }

    /** 获取价格模板/历史定价 — 命名规律候选 mtop.idle.web.publish.price.template（未真抓） */
    public JsonNode getPriceTemplate() {
        return apiClient.callMtop("mtop.idle.web.publish.price.template", "{}");
    }

    // ==================== 草稿管理 ====================

    /** 保存商品草稿 — 命名规律候选 mtop.idle.web.publish.draft.save（未真抓） */
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
        return apiClient.callMtop("mtop.idle.web.publish.draft.save", toJson(data));
    }

    /** 获取我的草稿列表 — 命名规律候选 mtop.idle.web.publish.draft.list（未真抓） */
    public JsonNode getDraftList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.idle.web.publish.draft.list", toJson(data));
    }

    /** 删除草稿 — 命名规律候选 mtop.idle.web.publish.draft.delete（未真抓） */
    public JsonNode deleteDraft(String draftId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("draftId", draftId != null ? draftId : "");
        return apiClient.callMtop("mtop.idle.web.publish.draft.delete", toJson(data));
    }

    /** 加载草稿内容 — 命名规律候选 mtop.idle.web.publish.draft.get（未真抓） */
    public JsonNode loadDraft(String draftId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("draftId", draftId != null ? draftId : "");
        return apiClient.callMtop("mtop.idle.web.publish.draft.get", toJson(data));
    }

    // ==================== 智能分类 ====================

    /** AI 智能识别分类 — 命名规律候选 mtop.idle.web.publish.category.ai.recommend（未真抓） */
    public JsonNode aiRecommendCategory(String title, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        return apiClient.callMtop("mtop.idle.web.publish.category.ai.recommend", toJson(data));
    }

    /** 获取分类树 — 命名规律候选 mtop.idle.web.publish.category.tree（未真抓） */
    public JsonNode getCategoryTree(String level) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("level", level != null ? level : "1");
        return apiClient.callMtop("mtop.idle.web.publish.category.tree", toJson(data));
    }

    // ==================== 价格智能建议 ====================

    /** 获取同类商品价格建议 — 命名规律候选 mtop.idle.web.publish.price.suggest（未真抓） */
    public JsonNode suggestPrice(String categoryId, String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("keyword", keyword != null ? keyword : "");
        return apiClient.callMtop("mtop.idle.web.publish.price.suggest", toJson(data));
    }

    // ==================== 运费模板 ====================

    /** 获取运费模板列表 — 命名规律候选 mtop.idle.web.publish.freight.template.list（未真抓） */
    public JsonNode getShippingTemplates() {
        return apiClient.callMtop("mtop.idle.web.publish.freight.template.list", "{}");
    }

    /** 设置发货方式 — 命名规律候选 mtop.idle.web.publish.freight.method.set（未真抓） */
    public JsonNode setShippingMethod(String method) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", method != null ? method : "0");
        return apiClient.callMtop("mtop.idle.web.publish.freight.method.set", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
