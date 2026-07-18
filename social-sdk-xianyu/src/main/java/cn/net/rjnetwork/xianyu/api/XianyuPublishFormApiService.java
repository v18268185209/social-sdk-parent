package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼商品发布表单 API 服务
 * 封装商品发布时的表单数据准备、草稿保存、智能分类推荐等 MTOP 接口调用
 *
 * <p>替代原有的 CDP DOM 表单填写方式，全部通过 API 完成。</p>
 */
public class XianyuPublishFormApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuPublishFormApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 发布前数据准备 ====================

    /**
     * 获取发布页预加载数据
     * API: mtop.idle.pc.idleitem.preget
     *
     * <p>发布商品前需要调用此接口获取表单初始数据，包括默认分类、发货地、
     * 价格模板等。</p>
     *
     * @return 预加载数据 JSON
     */
    public JsonNode getPublishPreData() {
        String url = new XianyuMtopRequestBuilder("mtop.idle.pc.idleitem.preget")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取默认发货地址
     * API: mtop.taobao.idle.local.poi.get
     *
     * @return 默认地址 JSON
     */
    public JsonNode getDefaultLocation() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idle.local.poi.get")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取价格模板/历史定价
     * API: mtop.taobao.idleprice.template.get
     *
     * @return 价格模板 JSON
     */
    public JsonNode getPriceTemplate() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleprice.template.get")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 草稿管理 ====================

    /**
     * 保存商品草稿
     * API: mtop.taobao.idlehome.item.draft.save
     *
     * @param title 商品标题
     * @param description 商品描述
     * @param price 价格
     * @param categoryId 分类 ID
     * @param images 图片 CDN URL 列表（逗号分隔）
     * @param location 发货地
     * @param shippingType 发货方式（0=包邮 1=买家承担）
     * @return 草稿保存结果 JSON
     */
    public JsonNode saveDraft(String title, String description, String price,
                               String categoryId, String images,
                               String location, String shippingType) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.draft.save")
                .addParam("title", title != null ? title : "")
                .addParam("description", description != null ? description : "")
                .addParam("price", price != null ? price : "")
                .addParam("categoryId", categoryId != null ? categoryId : "")
                .addParam("images", images != null ? images : "")
                .addParam("location", location != null ? location : "")
                .addParam("shippingType", shippingType != null ? shippingType : "0")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取我的草稿列表
     * API: mtop.taobao.idlehome.item.draft.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 草稿列表 JSON
     */
    public JsonNode getDraftList(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.draft.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 删除草稿
     * API: mtop.taobao.idlehome.item.draft.delete
     *
     * @param draftId 草稿 ID
     * @return 删除结果 JSON
     */
    public JsonNode deleteDraft(String draftId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.draft.delete")
                .addParam("draftId", draftId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 加载草稿内容
     * API: mtop.taobao.idlehome.item.draft.get
     *
     * @param draftId 草稿 ID
     * @return 草稿内容 JSON
     */
    public JsonNode loadDraft(String draftId) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlehome.item.draft.get")
                .addParam("draftId", draftId)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 智能分类 ====================

    /**
     * AI 智能识别分类（根据标题和描述）
     * API: mtop.taobao.idlecategory.ai.recommend
     *
     * @param title 商品标题
     * @param description 商品描述
     * @return AI 推荐分类列表 JSON
     */
    public JsonNode aiRecommendCategory(String title, String description) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecategory.ai.recommend")
                .addParam("title", title != null ? title : "")
                .addParam("description", description != null ? description : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取分类树
     * API: mtop.taobao.idlecategory.tree.get
     *
     * @param level 层级（1=一级分类）
     * @return 分类树 JSON
     */
    public JsonNode getCategoryTree(String level) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlecategory.tree.get")
                .addParam("level", level != null ? level : "1")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 价格智能建议 ====================

    /**
     * 获取同类商品价格建议
     * API: mtop.taobao.idleprice.suggest
     *
     * @param categoryId 分类 ID
     * @param keyword 关键词
     * @return 价格建议 JSON（最低价、均价、最高价）
     */
    public JsonNode suggestPrice(String categoryId, String keyword) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleprice.suggest")
                .addParam("categoryId", categoryId != null ? categoryId : "")
                .addParam("keyword", keyword != null ? keyword : "")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    // ==================== 运费模板 ====================

    /**
     * 获取运费模板列表
     * API: mtop.taobao.idleshipping.template.list
     *
     * @return 运费模板列表 JSON
     */
    public JsonNode getShippingTemplates() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleshipping.template.list")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 设置发货方式
     * API: mtop.taobao.idleshipping.method.set
     *
     * @param method 发货方式（0=无需邮寄 1=快递 2=自提）
     * @return 设置结果 JSON
     */
    public JsonNode setShippingMethod(String method) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idleshipping.method.set")
                .addParam("method", method != null ? method : "0")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
