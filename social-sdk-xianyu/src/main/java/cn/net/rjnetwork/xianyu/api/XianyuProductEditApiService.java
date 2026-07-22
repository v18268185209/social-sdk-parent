package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼商品编辑与完整上下架 API 服务
 * 封装商品编辑、完整上下架、批量操作、价格调整等 MTOP 接口调用
 *
 * <p>所有业务参数通过 data JSON 传递，底层 XianyuMtopApiClient 自动计算 sign、预热 token、
 * 设置 Referer/Origin，无需手动构造 URL 和签名。</p>
 *
 * <p>改价/改库存采用「获取原商品信息 → 改字段 → 发布新商品 → 下架原商品」完整流程，
 * 因为闲鱼 PC 无独立改价/改库存接口。</p>
 */
public class XianyuProductEditApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;
    private final XianyuProductApiService productApiService;
    private final XianyuPublishApiService publishApiService;

    public XianyuProductEditApiService(XianyuMtopApiClient apiClient,
                                       XianyuProductApiService productApiService,
                                       XianyuPublishApiService publishApiService) {
        this.apiClient = apiClient;
        this.productApiService = productApiService;
        this.publishApiService = publishApiService;
    }

    /**
     * 兼容旧构造函数（不依赖 publish/product 服务，但 updatePrice/updateStock 会抛异常）
     * @deprecated 推荐用三参数构造函数
     */
    @Deprecated
    public XianyuProductEditApiService(XianyuMtopApiClient apiClient) {
        this(apiClient, null, null);
    }

    // ==================== 商品编辑 ====================

    /** 编辑商品基本信息 — mtop.taobao.idlehome.item.edit */
    public JsonNode editProduct(String itemId, String title, String description,
                                String price, String originalPrice,
                                String categoryId, String location) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        data.put("price", price != null ? price : "");
        data.put("originalPrice", originalPrice != null ? originalPrice : "");
        data.put("categoryId", categoryId != null ? categoryId : "");
        data.put("location", location != null ? location : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.edit", toJson(data));
    }

    /**
     * 编辑商品详情图 — 命名规律候选 mtop.taobao.idlemanage.item.detail.edit
     * <p>未真抓验证（闲鱼 PC/H5 详情页未暴露编辑按钮入口，走内部 SPA 域）。
     * 已真验同域接口：com.taobao.idle.item.delete v1.1（删除），
     * 推测编辑类走 mtop.taobao.idlemanage.* 域，待后续真抓微调。</p>
     */
    public JsonNode editProductDetails(String itemId, String images) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        data.put("images", images != null ? images : "");
        return apiClient.callMtop("mtop.taobao.idlemanage.item.detail.edit", toJson(data));
    }

    // ==================== 完整上下架 ====================

    /**
     * 商品上架 — 命名规律候选 mtop.taobao.idlemanage.item.upshelf
     * <p>未真抓验证。已真验下架走 mtop.taobao.idle.item.downshelf v2.0（不是 idlemanage 域），
     * 上架是下架的姊妹接口，命名规律候选 upshelf，待后续真抓微调。</p>
     */
    public JsonNode shelfOn(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        // 上架是下架的姊妹接口，按真实下架接口同域命名：mtop.taobao.idle.item.upshelf v2.0
        return apiClient.callMtop("mtop.taobao.idle.item.upshelf", "2.0", toJson(data));
    }

    /**
     * 商品下架 — 真实接口 mtop.taobao.idle.item.downshelf v2.0
     * <p>真实抓包验证（2026-07-19 CDP 抓详情页「下架」按钮 React onClick handler 源代码）。
     * 与 XianyuProductApiService.updateProductStatus(offsale) 同接口，保留这个方法为兼容旧 facade 调用。</p>
     */
    public JsonNode shelfOff(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.item.downshelf", "2.0", toJson(data));
    }

    /** 批量上架商品 — 命名规律候选 mtop.taobao.idle.item.batch.upshelf v2.0（未真抓） */
    public JsonNode batchShelfOn(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idle.item.batch.upshelf", "2.0", toJson(data));
    }

    /** 批量下架商品 — 命名规律候选 mtop.taobao.idle.item.batch.downshelf v2.0（未真抓） */
    public JsonNode batchShelfOff(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idle.item.batch.downshelf", "2.0", toJson(data));
    }

    // ==================== 价格调整 ====================

    /**
     * 调整商品价格 — 完整流程：获取原商品信息 → 改价格 → 发布新商品 → 下架原商品
     * <p>闲鱼 PC 无独立改价接口，走「新建替代」路径：不传 itemId 给 publishItem（pcMainPublish），
     * 生成新商品后，将原来的商品下架，实现改价效果。</p>
     *
     * @param itemId    要改价的原商品 id
     * @param price     新价格（元，如 "99.00"），内部转分后传给发布接口
     * @return 新商品的发布结果，data 含新 itemId
     */
    public JsonNode updatePrice(String itemId, String price) {
        if (productApiService == null || publishApiService == null) {
            throw new IllegalStateException(
                "updatePrice 需要 XianyuProductApiService 和 XianyuPublishApiService，请使用三参数构造函数"
            );
        }
        // 1. 获取原商品详情（完整字段）
        JsonNode detail = productApiService.getProductDetail(itemId);
        JsonNode itemDO = detail.path("data").path("itemDO");
        if (itemDO.isMissingNode()) {
            throw new IllegalStateException("商品详情获取失败，无 itemDO: " + detail);
        }

        // 2. 提取原商品字段，构造发布参数
        String title = extractTitle(itemDO);
        String description = extractDescription(itemDO);
        String origPriceCent = extractOriginalPrice(itemDO);   // 原价保留
        String stock = extractStock(itemDO);                    // 库存不变
        List<Map<String, Object>> images = extractImages(itemDO);
        Map<String, String> catDTO = extractCatDTO(itemDO, detail);
        List<Map<String, Object>> labelExtList = extractLabelExtList(itemDO);
        Map<String, Object> addrDTO = extractAddrDTO(itemDO);
        Map<String, Object> deliverySettings = extractDeliverySettings(itemDO);

        // 3. 价格：元 → 分
        String newPriceCent = priceToCent(price);

        // 4. 发布新商品（itemId=null → pcMainPublish 场景，生成新商品）
        JsonNode publishResult = publishApiService.publishItem(
            null, title, description, newPriceCent, origPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        // 5. 下架原商品
        shelfOff(itemId);

        return publishResult;
    }

    /**
     * 调整商品库存 — 完整流程：获取原商品信息 → 改库存 → 发布新商品 → 下架原商品
     * <p>闲鱼 PC 无独立改库存接口，走「新建替代」路径：不传 itemId 给 publishItem（pcMainPublish），
     * 生成新商品后，将原来的商品下架，实现改库存效果。</p>
     *
     * @param itemId  要改库存的原商品 id
     * @param stock   新库存数量（如 "50"），直接传给发布接口的 quantity
     * @return 新商品的发布结果，data 含新 itemId
     */
    public JsonNode updateStock(String itemId, String stock) {
        if (productApiService == null || publishApiService == null) {
            throw new IllegalStateException(
                "updateStock 需要 XianyuProductApiService 和 XianyuPublishApiService，请使用三参数构造函数"
            );
        }
        // 1. 获取原商品详情（完整字段）
        JsonNode detail = productApiService.getProductDetail(itemId);
        JsonNode itemDO = detail.path("data").path("itemDO");
        if (itemDO.isMissingNode()) {
            throw new IllegalStateException("商品详情获取失败，无 itemDO: " + detail);
        }

        // 2. 提取原商品字段，构造发布参数
        String title = extractTitle(itemDO);
        String description = extractDescription(itemDO);
        String priceCent = extractPrice(itemDO);                // 价格不变
        String origPriceCent = extractOriginalPrice(itemDO);   // 原价保留
        List<Map<String, Object>> images = extractImages(itemDO);
        Map<String, String> catDTO = extractCatDTO(itemDO, detail);
        List<Map<String, Object>> labelExtList = extractLabelExtList(itemDO);
        Map<String, Object> addrDTO = extractAddrDTO(itemDO);
        Map<String, Object> deliverySettings = extractDeliverySettings(itemDO);

        // 3. 发布新商品（不传 itemId → pcMainPublish 场景，生成新商品）
        JsonNode publishResult = publishApiService.publishItem(
            title, description, priceCent, origPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        // 4. 下架原商品
        shelfOff(itemId);

        return publishResult;
    }

    /** 调整商品原价 — 同 updatePrice 路径，一并修改 */
    public JsonNode updateOriginalPrice(String itemId, String originalPrice) {
        if (productApiService == null || publishApiService == null) {
            throw new IllegalStateException(
                "updateOriginalPrice 需要 XianyuProductApiService 和 XianyuPublishApiService，请使用三参数构造函数"
            );
        }
        JsonNode detail = productApiService.getProductDetail(itemId);
        JsonNode itemDO = detail.path("data").path("itemDO");
        if (itemDO.isMissingNode()) {
            throw new IllegalStateException("商品详情获取失败，无 itemDO: " + detail);
        }

        String title = extractTitle(itemDO);
        String description = extractDescription(itemDO);
        String priceCent = extractPrice(itemDO);                // 售价不变
        String stock = extractStock(itemDO);                    // 库存不变
        List<Map<String, Object>> images = extractImages(itemDO);
        Map<String, String> catDTO = extractCatDTO(itemDO, detail);
        List<Map<String, Object>> labelExtList = extractLabelExtList(itemDO);
        Map<String, Object> addrDTO = extractAddrDTO(itemDO);
        Map<String, Object> deliverySettings = extractDeliverySettings(itemDO);

        String newOrigPriceCent = priceToCent(originalPrice);

        JsonNode publishResult = publishApiService.publishItem(
            title, description, priceCent, newOrigPriceCent, stock,
            images, catDTO, labelExtList, addrDTO, deliverySettings
        );

        shelfOff(itemId);
        return publishResult;
    }

    // ==================== 字段提取方法 ====================

    /** 提取标题 */
    private String extractTitle(JsonNode itemDO) {
        return itemDO.path("title").asText("");
    }

    /** 提取描述 */
    private String extractDescription(JsonNode itemDO) {
        return itemDO.path("desc").asText(itemDO.path("description").asText(""));
    }

    /** 提取售价（分），从 priceInfo.price 或 soldPrice 拿 */
    private String extractPrice(JsonNode itemDO) {
        // getProductDetail 返回的 price 已经在 priceInfo.price（分）
        JsonNode priceNode = itemDO.path("priceInfo").path("price");
        if (!priceNode.isMissingNode() && priceNode.asLong() > 0) {
            return String.valueOf(priceNode.asLong());
        }
        // 兜底：soldPrice 字段（分）
        JsonNode soldPrice = itemDO.path("soldPrice");
        if (!soldPrice.isMissingNode() && soldPrice.asLong() > 0) {
            return String.valueOf(soldPrice.asLong());
        }
        // 再兜底：price 字段（可能带小数点，是元）
        JsonNode price = itemDO.path("price");
        if (!price.isMissingNode() && price.asDouble() > 0) {
            return priceToCent(price.asText());
        }
        return "0";
    }

    /** 提取原价（分） */
    private String extractOriginalPrice(JsonNode itemDO) {
        JsonNode origPrice = itemDO.path("originalPrice");
        if (!origPrice.isMissingNode() && origPrice.asLong() > 0) {
            return String.valueOf(origPrice.asLong());
        }
        // 无原价时用售价兜底
        return extractPrice(itemDO);
    }

    /** 提取库存 */
    private String extractStock(JsonNode itemDO) {
        JsonNode quantity = itemDO.path("quantity");
        if (!quantity.isMissingNode() && quantity.asInt() > 0) {
            return String.valueOf(quantity.asInt());
        }
        return "1"; // 默认 1
    }

    /** 提取图片列表 → publishItem 需要的 imageInfoList（含 url/height/width） */
    private List<Map<String, Object>> extractImages(JsonNode itemDO) {
        List<Map<String, Object>> images = new ArrayList<>();
        // 主图：picInfo / picPath
        JsonNode picInfo = itemDO.path("picInfo");
        if (picInfo.isArray() && !picInfo.isEmpty()) {
            for (JsonNode pic : picInfo) {
                addImageFromPicNode(images, pic);
            }
        } else if (itemDO.path("picPath").isValueNode()) {
            // 单图形式
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("url", itemDO.path("picPath").asText(""));
            img.put("height", itemDO.path("picHeight").asInt(800));
            img.put("width", itemDO.path("picWidth").asInt(800));
            images.add(img);
        }
        // 详情图：picDetailDO / imageList
        JsonNode picDetailDO = itemDO.path("picDetailDO");
        if (picDetailDO.isArray() && !picDetailDO.isEmpty()) {
            for (JsonNode pic : picDetailDO) {
                addImageFromPicNode(images, pic);
            }
        }
        JsonNode imageList = itemDO.path("imageList");
        if (imageList.isArray() && !imageList.isEmpty()) {
            for (JsonNode img : imageList) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("url", img.path("url").asText(img.path("path").asText("")));
                item.put("height", img.path("height").asInt(800));
                item.put("width", img.path("width").asInt(800));
                if (!item.get("url").toString().isEmpty()) {
                    images.add(item);
                }
            }
        }
        return images;
    }

    private void addImageFromPicNode(List<Map<String, Object>> images, JsonNode pic) {
        String url = pic.path("url").asText(pic.path("path").asText(""));
        if (url.isEmpty()) return;
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("url", url);
        img.put("height", pic.path("height").asInt(pic.path("heightSize").asInt(800)));
        img.put("width", pic.path("width").asInt(pic.path("widthSize").asInt(800)));
        images.add(img);
    }

    /** 提取分类信息 → catDTO {catId, catName, channelCatId, tbCatId} */
    private Map<String, String> extractCatDTO(JsonNode itemDO, JsonNode detail) {
        Map<String, String> catDTO = new LinkedHashMap<>();
        // 优先从 itemDO 提取
        String catId = itemDO.path("categoryId").asText("");
        if (!catId.isEmpty()) catDTO.put("catId", catId);
        String channelCatId = itemDO.path("channelCatId").asText("");
        if (!channelCatId.isEmpty()) catDTO.put("channelCatId", channelCatId);
        String catName = itemDO.path("categoryName").asText("");
        if (!catName.isEmpty()) catDTO.put("catName", catName);
        // 兜底从 detail.data.trackParams 拿
        if (catDTO.isEmpty()) {
            JsonNode trackParams = detail.path("data").path("trackParams");
            if (trackParams.path("categoryId").isValueNode()) {
                catDTO.put("catId", trackParams.path("categoryId").asText(""));
            }
        }
        return catDTO;
    }

    /** 提取属性标签列表 */
    private List<Map<String, Object>> extractLabelExtList(JsonNode itemDO) {
        List<Map<String, Object>> labels = new ArrayList<>();
        JsonNode labelList = itemDO.path("itemLabelExtList");
        if (labelList.isArray()) {
            for (JsonNode label : labelList) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("labelId", label.path("labelId").asText(""));
                item.put("labelName", label.path("labelName").asText(""));
                item.put("labelValue", label.path("labelValue").asText(""));
                labels.add(item);
            }
        }
        return labels;
    }

    /** 提取所在地 → addrDTO {area, city, divisionId, gps, poiId, poi} */
    private Map<String, Object> extractAddrDTO(JsonNode itemDO) {
        Map<String, Object> addr = new LinkedHashMap<>();
        JsonNode location = itemDO.path("location");
        if (!location.isMissingNode()) {
            addr.put("area", location.path("area").asText(""));
            addr.put("city", location.path("city").asText(""));
            addr.put("divisionId", location.path("divisionId").asText(""));
            addr.put("gps", location.path("gps").asText(""));
            addr.put("poiId", location.path("poiId").asText(""));
            addr.put("poi", location.path("poi").asText(""));
        } else {
            // 兜底：从 itemDO 直接拿
            addr.put("area", itemDO.path("area").asText(""));
            addr.put("city", itemDO.path("city").asText(""));
            addr.put("divisionId", itemDO.path("divisionId").asText(""));
        }
        return addr;
    }

    /** 提取运费设置 → deliverySettings {canFreeShipping, supportFreight, onlyTakeSelf, templateId, postPriceInCent} */
    private Map<String, Object> extractDeliverySettings(JsonNode itemDO) {
        Map<String, Object> delivery = new LinkedHashMap<>();
        JsonNode postFee = itemDO.path("postFeeDTO");
        if (postFee.isMissingNode()) {
            postFee = itemDO.path("postFee");
        }
        if (!postFee.isMissingNode()) {
            delivery.put("canFreeShipping", postFee.path("canFreeShipping").asBoolean(false));
            delivery.put("supportFreight", postFee.path("supportFreight").asBoolean(false));
            delivery.put("onlyTakeSelf", postFee.path("onlyTakeSelf").asBoolean(false));
            if (postFee.path("templateId").isValueNode()) {
                delivery.put("templateId", postFee.path("templateId").asText(""));
            }
            if (postFee.path("postPriceInCent").isValueNode()) {
                delivery.put("postPriceInCent", postFee.path("postPriceInCent").asText(""));
            }
        } else {
            // 默认包邮
            delivery.put("canFreeShipping", true);
            delivery.put("supportFreight", false);
            delivery.put("onlyTakeSelf", false);
        }
        return delivery;
    }

    /** 价格：元 → 分（如 "99.00" → "9900"） */
    private String priceToCent(String priceYuan) {
        if (priceYuan == null || priceYuan.isEmpty()) return "0";
        try {
            double p = Double.parseDouble(priceYuan);
            return String.valueOf(Math.round(p * 100));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    // ==================== 商品分类 ====================

    /** 获取可用分类列表 — mtop.taobao.idlecategory.list */
    public JsonNode getCategoryList(String parentId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parentId", parentId != null ? parentId : "0");
        return apiClient.callMtop("mtop.taobao.idlecategory.list", toJson(data));
    }

    /** AI 智能推荐分类 — mtop.taobao.idlecategory.recommend */
    public JsonNode recommendCategory(String title, String description) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("description", description != null ? description : "");
        return apiClient.callMtop("mtop.taobao.idlecategory.recommend", toJson(data));
    }

    // ==================== 商品删除 ====================

    /**
     * 删除商品 — 真实接口 mtop.alibaba.idle.seller.pc.item.delete v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply 已真验通）：
     * 闲鱼 PC 卖家中心删除商品走 mtop.alibaba.idle.seller.pc.item.delete 域，
     * 之前抓到的 com.taobao.idle.item.delete 是详情页按钮 onClick 姿妹接口（也存在但走 App WebView），
     * 这里用参考项目真验通的 PC 域接口名。</p>
     */
    public JsonNode deleteProduct(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.alibaba.idle.seller.pc.item.delete", "1.0", toJson(data));
    }

    /**
     * 商品擦亮（提升曝光排名）— 真实接口 mtop.taobao.idle.item.polish v1.0
     * <p>真实抓包验证（参考项目 xianyu-auto-reply scheduler.polish_task 已真验通）：
     * 闲鱼定时擦亮任务走 mtop.taobao.idle.item.polish，data={itemId}，
     * spm_cnt=a21ybx.item.0.0 / spm_pre=a21ybx.personal.feeds.1.42f86ac21eZ9zd</p>
     */
    public JsonNode polishItem(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idle.item.polish", "1.0", toJson(data));
    }

    /** 批量删除商品 — mtop.taobao.idlehome.item.batch.delete */
    public JsonNode batchDeleteProducts(String itemIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemIds", itemIds != null ? itemIds : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.batch.delete", toJson(data));
    }

    // ==================== 商品复制 ====================

    /** 复制商品（一键转卖）— mtop.taobao.idlehome.item.copy */
    public JsonNode copyProduct(String sourceItemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceItemId", sourceItemId != null ? sourceItemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.copy", toJson(data));
    }

    // ==================== 商品状态查询 ====================

    /** 获取商品完整状态信息 — mtop.taobao.idlehome.item.fullinfo.get */
    public JsonNode getProductFullInfo(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.fullinfo.get", toJson(data));
    }

    /** 获取商品浏览量统计 — mtop.taobao.idlehome.item.viewstats.get */
    public JsonNode getViewStats(String itemId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId != null ? itemId : "");
        return apiClient.callMtop("mtop.taobao.idlehome.item.viewstats.get", toJson(data));
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
