package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 闲鱼商品发布 API 服务（真抓验证版）
 *
 * <p>真实抓包来源：参考项目 /Users/vim/Desktop/codes/github/XianYuApis/goofish_apis.py，
 * 真接口名 + data 结构 + 链调用顺序全来自该项目的 public() 方法实测验证。</p>
 *
 * <p><b>发布一个闲鱼商品需要链上 4 个 mtop 接口</b>，缺一不可：</p>
 * <ol>
 *   <li>{@link #uploadImage} — 图片上传到闲鱼 CDN（stream-upload.goofish.com），
 *       拿到 url + pix（宽高），后续 publish 的 imageInfoDOList 要用</li>
 *   <li>{@link #recommendCategory} — AI 推荐分类/属性（mtop.taobao.idle.kgraph.property.recommend v2.0），
 *       拿到 catId/channelCatId/tbCatId + itemLabelExtList（用户已点击的属性标签）</li>
 *   <li>{@link #getDefaultLocation} — 拿默认所在地（mtop.taobao.idle.local.poi.get v1.0），
 *       返回 area/city/divisionId/gps/poiId/poi，publish 的 itemAddrDTO 要用</li>
 *   <li>{@link #publishItem} — 真正提交发布（mtop.idle.pc.idleitem.publish v1.0），
 *       data 含上述全部信息 + 标题/价格/描述/运费设置</li>
 * </ol>
 *
 * <p>底层 {@link XianyuMtopApiClient} 自动计算 sign、预热 token、设置 Referer/Origin，
 * 无需手动构造 URL 和签名。</p>
 */
public class XianyuPublishApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuPublishApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    // ==================== 1. 图片上传到闲鱼 CDN ====================

    /**
     * 上传图片到闲鱼 CDN — 真实接口 stream-upload.goofish.com/api/upload.api
     * <p>真实抓包验证（XianYuApis upload_media + xianyu-auto-reply image_uploader）：</p>
     * <ul>
     *   <li>POST https://stream-upload.goofish.com/api/upload.api?floderId=0&appkey=xy_chat&_input_charset=utf-8</li>
     *   <li>multipart/form-data，字段名 "file"，含 filename + Content-Type（image/png）</li>
     *   <li>不走 mtop 签名，但需要 cookie 鉴权 + 同 origin/referer 头</li>
     *   <li>真返回结构：{object: {url: "http://img.alicdn.com/...", pix: "WxH"}}</li>
     * </ul>
     *
     * <p>本方法返回 {@link UploadResult} 含 url + width + height，方便后续 publishItem 拼 imageInfoDOList。</p>
     *
     * @param imageBytes 图片二进制内容
     * @param filename   文件名（如 "1.jpg"），闲鱼按文件名后缀识别类型
     * @return 上传结果，含 url + width + height；失败抛 IllegalStateException
     */
    public UploadResult uploadImage(byte[] imageBytes, String filename) {
        String url = "https://stream-upload.goofish.com/api/upload.api?floderId=0&appkey=xy_chat&_input_charset=utf-8";
        JsonNode resp = apiClient.uploadMultipart(url, imageBytes, filename, "image/png");
        if (resp == null) {
            throw new IllegalStateException("闲鱼图片上传失败：无返回");
        }
        // 检查 ret 字段（失败时 ret[0] 含 FAIL_xxx）
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) {
            String r0 = ret.get(0).asText("");
            if (!r0.isEmpty() && !r0.contains("SUCCESS")) {
                throw new IllegalStateException("闲鱼图片上传失败：" + r0);
            }
        }
        // 解析 object.url + object.pix
        JsonNode object = resp.path("object");
        if (object.isMissingNode() || object.isNull()) {
            throw new IllegalStateException("闲鱼图片上传失败：返回缺 object 字段，resp=" + resp.toString().substring(0, 300));
        }
        String imgUrl = object.path("url").asText("");
        if (imgUrl.isEmpty()) {
            throw new IllegalStateException("闲鱼图片上传失败：返回缺 object.url，resp=" + resp.toString().substring(0, 300));
        }
        // pix 形如 "1024x768"，解析出 width/height；无 pix 时传 0，闲鱼会自己拉
        int width = 0, height = 0;
        String pix = object.path("pix").asText("");
        if (!pix.isEmpty() && pix.contains("x")) {
            try {
                String[] parts = pix.split("x");
                width = Integer.parseInt(parts[0]);
                height = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        return new UploadResult(imgUrl, width, height, pix);
    }

    /** 兼容旧签名：本地文件路径版（ProductService.create 走 byte[] 版，不调这个）*/
    public JsonNode uploadImage(Path imagePath) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(imagePath);
            String filename = imagePath.getFileName() != null ? imagePath.getFileName().toString() : "upload.png";
            UploadResult r = uploadImage(bytes, filename);
            // 包成 JsonNode 兼容旧 facade 调用
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            return m.readTree(m.writeValueAsString(r));
        } catch (Exception e) {
            throw new IllegalStateException("闲鱼图片上传失败：" + e.getMessage(), e);
        }
    }

    /** 图片上传结果，含闲鱼 CDN url + 宽高 */
    public static class UploadResult {
        public final String url;
        public final int width;
        public final int height;
        public final String pix;
        public UploadResult(String url, int width, int height, String pix) {
            this.url = url; this.width = width; this.height = height; this.pix = pix;
        }
    }

    // ==================== 1b. 视频上传到闲鱼 CDN ====================

    /**
     * 上传视频到闲鱼 CDN — 走同 stream-upload.goofish.com/api/upload.api，仅 Content-Type 改 video/mp4
     * <p>真实定位结论（2026-07-20 全参考项目翻查）：</p>
     * <ul>
     *   <li>参考项目 XianYuApis / XianyuAutoAgent / xianyu-auto-reply 均无视频上传实现</li>
     *   <li>闲鱼网页版发布页只有图片上传按钮，无视频入口；视频上传只在闲鱼 App</li>
     *   <li>按闲鱼流上传服务命名规律：图片视频走同 host 同 path，仅 multipart 字段的 Content-Type 不同
     *       （图片 image/png，视频 video/mp4）。这是阿里 CDN 流上传服务的统一设计，
     *       同 appkey=xy_chat、同 floderId=0、同 file 字段名</li>
     *   <li>真返回结构：{object: {url: "http://img.alicdn.com/...", pix: "WxH"}}（视频可能无 pix，宽高传 0）</li>
     * </ul>
     *
     * <p><b>风险提示</b>：本接口按命名规律推测，未真抓验证。若闲鱼视频走独立接口（如 stream-video-upload.goofish.com），
     * 调用会返回 FAIL_xxx，届时需手机端抓包定位真接口名。</p>
     *
     * @param videoBytes 视频二进制内容
     * @param filename   文件名（如 "1.mp4"），闲鱼按后缀识别类型
     * @return 上传结果，含 url（宽高传 0，视频无 pix）；失败抛 IllegalStateException
     */
    public UploadResult uploadVideo(byte[] videoBytes, String filename) {
        String url = "https://stream-upload.goofish.com/api/upload.api?floderId=0&appkey=xy_chat&_input_charset=utf-8";
        JsonNode resp = apiClient.uploadMultipart(url, videoBytes, filename, "video/mp4");
        if (resp == null) {
            throw new IllegalStateException("闲鱼视频上传失败：无返回");
        }
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) {
            String r0 = ret.get(0).asText("");
            if (!r0.isEmpty() && !r0.contains("SUCCESS")) {
                throw new IllegalStateException("闲鱼视频上传失败：" + r0);
            }
        }
        JsonNode object = resp.path("object");
        if (object.isMissingNode() || object.isNull()) {
            throw new IllegalStateException("闲鱼视频上传失败：返回缺 object 字段，resp=" + resp.toString().substring(0, 300));
        }
        String videoUrl = object.path("url").asText("");
        if (videoUrl.isEmpty()) {
            throw new IllegalStateException("闲鱼视频上传失败：返回缺 object.url，resp=" + resp.toString().substring(0, 300));
        }
        // 视频无 pix，宽高传 0
        return new UploadResult(videoUrl, 0, 0, "");
    }

    // ==================== 2. AI 分类推荐 ====================

    /**
     * AI 推荐商品分类/属性 — 真实接口 mtop.taobao.idle.kgraph.property.recommend v2.0
     * <p>真实抓包验证（XianYuApis get_public_channel）：发布前闲鱼会按标题+图片自动推荐分类和属性标签，
     * 用户点击的属性会进 itemLabelExtList，最终 publish 的 data.itemCatDTO 和 data.itemLabelExtList 要用。</p>
     *
     * <p>真返回结构（XianYuApis 真抓）：</p>
     * <pre>
     * data.cardList[] → 每项 cardData 含 propertyName/propertyId/valuesList[]
     *   valuesList[] 每项含 catName/channelCatId/tbCatId/isClicked（用户已点击为 true）
     * data.categoryPredictResult → {catId, catName, channelCatId, tbCatId}（AI 主推分类）
     * </pre>
     *
     * @param title       商品标题（AI 按标题推荐分类）
     * @param imagesInfo  图片信息列表（每项含 url/height/width），辅助 AI 识别分类
     * @return 闲鱼返回 JSON，含 cardList[] 和 categoryPredictResult
     */
    public JsonNode recommendCategory(String title, List<Map<String, Object>> imagesInfo) {
        // 真实 data 结构（XianYuApis 真抓）：
        //   title + lockCpv=false + multiSKU=false + publishScene="mainPublish"
        //   + scene="newPublishChoice" + description=title + imageInfos[]（每项含 url/height/width/major=true/type=0/status=done）
        //   + uniqueCode（时间戳，毫秒级，13 位）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", title != null ? title : "");
        data.put("lockCpv", false);
        data.put("multiSKU", false);
        data.put("publishScene", "mainPublish");
        data.put("scene", "newPublishChoice");
        data.put("description", title != null ? title : "");
        // imageInfos：图片信息数组，每项含 url/heightSize/widthSize/major/type/status
        List<Map<String, Object>> imageInfos = new ArrayList<>();
        if (imagesInfo != null) {
            for (Map<String, Object> img : imagesInfo) {
                Map<String, Object> item = new LinkedHashMap<>();
                Map<String, Object> extraInfo = new LinkedHashMap<>();
                extraInfo.put("isH", "false");
                extraInfo.put("isT", "false");
                extraInfo.put("raw", "false");
                item.put("extraInfo", extraInfo);
                item.put("isQrCode", false);
                item.put("url", img.getOrDefault("url", ""));
                item.put("heightSize", img.getOrDefault("height", 0));
                item.put("widthSize", img.getOrDefault("width", 0));
                item.put("major", true);
                item.put("type", 0);
                item.put("status", "done");
                imageInfos.add(item);
            }
        }
        data.put("imageInfos", imageInfos);
        data.put("uniqueCode", String.valueOf(System.currentTimeMillis()));

        return apiClient.callMtop("mtop.taobao.idle.kgraph.property.recommend", "2.0", toJson(data));
    }

    // ==================== 3. 默认所在地 ====================

    /**
     * 获取默认所在地（卖家常用地址）— 真实接口 mtop.taobao.idle.local.poi.get v1.0
     * <p>真实抓包验证（XianYuApis get_default_location）：发布时必须填 itemAddrDTO，
     * 含 area/city/divisionId/gps（经纬度）/poiId/poi（地点名）。
     * 调本接口拿卖家的默认地址填进去。</p>
     *
     * <p>真返回结构：data.commonAddresses[0] 含 area/city/divisionId/longitude/latitude/poiId/poi。</p>
     *
     * @return 闲鱼返回 JSON，含 data.commonAddresses[]
     */
    public JsonNode getDefaultLocation() {
        // 真实 data（XianYuApis 真抓）：固定经纬度（杭州阿里园区），实际闲鱼按 cookie 的卖家默认地址返回
        // 这里传一个空的 data，让闲鱼按 cookie 自己返回卖家默认地址
        return apiClient.callMtop("mtop.taobao.idle.local.poi.get", "{}");
    }

    // ==================== 4. 真正提交发布 ====================

    /**
     * 发布商品到闲鱼 — 真实接口 mtop.idle.pc.idleitem.publish v1.0
     * <p>真实抓包验证（XianYuApis public）：这是发布链的最后一环，提交后闲鱼生成新商品 itemId，
     * 商品进入在售状态。data 结构非常复杂，含标题/描述/价格/图片/分类/所在地/运费/属性标签 等十余个子对象。</p>
     *
     * <p><b>发布成功 = 上架</b>（闲鱼没有独立的"上架已下架商品"接口，
     * 已下架商品要回上架只能重新 publish，会生成新 itemId，原浏览量/收藏清零）。</p>
     *
     * <p>真返回结构：ret[0]=SUCCESS::xxx 表示成功，data 含新商品 itemId（部分版本也可能不返回，
     * 需要随后调 syncFromXianyu 拉新商品列表才能拿到 itemId）。</p>
     *
     * @param title          商品标题
     * @param description    商品描述（闲鱼 itemTextDTO.desc，可与标题相同）
     * @param priceInCent    当前价格（分，如 9900 = ¥99.00）；null 则用 defaultPrice=true（闲鱼自动按分类定价）
     * @param origPriceInCent 原价（分）；null 不设原价
     * @param imageInfoList  图片信息列表，每项含 url/height/width（来自闲鱼 CDN，非本地 url）
     * @param catDTO         分类信息 {catId, catName, channelCatId, tbCatId}（来自 recommendCategory 的 categoryPredictResult）
     * @param labelExtList   属性标签列表（来自 recommendCategory 的 cardList 中 isClicked=true 的项，本方法自动解析）
     * @param addrDTO        所在地 {area, city, divisionId, gps, poiId, poi}（来自 getDefaultLocation）
     * @param deliverySettings 运费设置 {canFreeShipping, supportFreight, onlyTakeSelf, templateId, postPriceInCent}
     * @return 闲鱼返回 JSON，ret[0] 含 SUCCESS/FAIL 标识
     */
    public JsonNode publishItem(String title, String description,
                                String priceInCent, String origPriceInCent,
                                List<Map<String, Object>> imageInfoList,
                                Map<String, String> catDTO,
                                List<Map<String, Object>> labelExtList,
                                Map<String, Object> addrDTO,
                                Map<String, Object> deliverySettings) {
        // 发新商品模式：itemId=null，库存默认 1
        return publishItem(null, title, description, priceInCent, origPriceInCent, "1",
                imageInfoList, catDTO, labelExtList, addrDTO, deliverySettings);
    }

    /**
     * 发布/编辑商品 — 真实接口 mtop.idle.pc.idleitem.publish v1.0（重载版，支持编辑重发）
     * <p>传 itemId 时闲鱼按「编辑已有商品」处理，itemId 保留，浏览量/收藏不清零，
     * 用于改价/改库存/改图等。publishScene 改为 "edit"，sourceId/bizcode 改为 "pcEdit"。</p>
     *
     * @param itemId        已有商品 id（编辑重发模式用，传 null 则发新商品）
     * @param stock          库存（如 "10"；闲鱼 quantity 字段，编辑重发改库存用；发新商品传 null 默认 "1"）
     * @see #publishItem(String, String, String, String, List, Map, List, Map, Map) 发新商品版
     */
    public JsonNode publishItem(String itemId, String title, String description,
                                String priceInCent, String origPriceInCent, String stock,
                                List<Map<String, Object>> imageInfoList,
                                Map<String, String> catDTO,
                                List<Map<String, Object>> labelExtList,
                                Map<String, Object> addrDTO,
                                Map<String, Object> deliverySettings) {
        // 真实 data 结构（XianYuApis public 真抓，逐字段对照）：
        Map<String, Object> data = new LinkedHashMap<>();
        // 编辑重发模式：传 itemId 时闲鱼按「编辑已有商品」处理，itemId 保留
        boolean isEdit = itemId != null && !itemId.isEmpty();
        if (isEdit) {
            data.put("itemId", itemId);
        }
        data.put("freebies", false);
        data.put("itemTypeStr", "b");            // b = 普通商品（非拍卖）
        // quantity：库存，编辑重发改库存用；发新商品默认 1
        data.put("quantity", stock != null && !stock.isEmpty() ? stock : "1");
        data.put("simpleItem", "true");
        // imageInfoDOList：图片信息数组，每项含 extraInfo/isQrCode/url/heightSize/widthSize/major/type/status
        List<Map<String, Object>> imageInfoDOList = new ArrayList<>();
        if (imageInfoList != null) {
            for (Map<String, Object> img : imageInfoList) {
                Map<String, Object> item = new LinkedHashMap<>();
                Map<String, Object> extraInfo = new LinkedHashMap<>();
                extraInfo.put("isH", "false");
                extraInfo.put("isT", "false");
                extraInfo.put("raw", "false");
                item.put("extraInfo", extraInfo);
                item.put("isQrCode", false);
                item.put("url", img.getOrDefault("url", ""));
                item.put("heightSize", img.getOrDefault("height", 0));
                item.put("widthSize", img.getOrDefault("width", 0));
                item.put("major", true);
                item.put("type", 0);
                item.put("status", "done");
                imageInfoDOList.add(item);
            }
        }
        data.put("imageInfoDOList", imageInfoDOList);

        // itemTextDTO：标题 + 描述
        Map<String, Object> itemTextDTO = new LinkedHashMap<>();
        itemTextDTO.put("desc", description != null ? description : "");
        itemTextDTO.put("title", title != null ? title : "");
        itemTextDTO.put("titleDescSeparate", false);
        data.put("itemTextDTO", itemTextDTO);

        // itemLabelExtList：属性标签（AI 推荐后用户点击的属性）
        data.put("itemLabelExtList", labelExtList != null ? labelExtList : new ArrayList<>());

        // itemPriceDTO：价格（单位：分，如 9900 = ¥99.00）
        Map<String, Object> itemPriceDTO = new LinkedHashMap<>();
        if (priceInCent != null && !priceInCent.isEmpty()) {
            itemPriceDTO.put("priceInCent", priceInCent);
        }
        if (origPriceInCent != null && !origPriceInCent.isEmpty()) {
            itemPriceDTO.put("origPriceInCent", origPriceInCent);
        }
        data.put("itemPriceDTO", itemPriceDTO);

        // userRightsProtocols：用户权益协议（默认关闭 SKILL_PLAY_NO_MIND）
        List<Map<String, Object>> userRightsProtocols = new ArrayList<>();
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("enable", false);
        protocol.put("serviceCode", "SKILL_PLAY_NO_MIND");
        userRightsProtocols.add(protocol);
        data.put("userRightsProtocols", userRightsProtocols);

        // itemPostFeeDTO：运费设置
        Map<String, Object> itemPostFeeDTO = new LinkedHashMap<>();
        itemPostFeeDTO.put("canFreeShipping", deliverySettings != null && Boolean.TRUE.equals(deliverySettings.get("canFreeShipping")));
        itemPostFeeDTO.put("supportFreight", deliverySettings != null && Boolean.TRUE.equals(deliverySettings.get("supportFreight")));
        itemPostFeeDTO.put("onlyTakeSelf", deliverySettings != null && Boolean.TRUE.equals(deliverySettings.get("onlyTakeSelf")));
        if (deliverySettings != null && deliverySettings.get("templateId") != null) {
            itemPostFeeDTO.put("templateId", String.valueOf(deliverySettings.get("templateId")));
        }
        if (deliverySettings != null && deliverySettings.get("postPriceInCent") != null) {
            itemPostFeeDTO.put("postPriceInCent", String.valueOf(deliverySettings.get("postPriceInCent")));
        }
        data.put("itemPostFeeDTO", itemPostFeeDTO);

        // itemAddrDTO：所在地（来自 getDefaultLocation）
        data.put("itemAddrDTO", addrDTO != null ? addrDTO : new LinkedHashMap<>());

        // defaultPrice：无价格时用闲鱼默认价
        data.put("defaultPrice", priceInCent == null || priceInCent.isEmpty());

        // itemCatDTO：分类（来自 recommendCategory 的 categoryPredictResult）
        data.put("itemCatDTO", catDTO != null ? catDTO : new LinkedHashMap<>());

        // uniqueCode：唯一码，时间戳毫秒
        data.put("uniqueCode", String.valueOf(System.currentTimeMillis()));

        // sourceId/bizcode/publishScene：发布场景（编辑重发改 pcEdit，发新商品 pcMainPublish）
        String scene = isEdit ? "pcEdit" : "pcMainPublish";
        data.put("sourceId", scene);
        data.put("bizcode", scene);
        data.put("publishScene", scene);

        return apiClient.callMtop("mtop.idle.pc.idleitem.publish", "1.0", toJson(data));
    }

    // ==================== 兼容旧 API（保持编译不破）====================

    /**
     * 创建商品 — 命名规律候选 mtop.idle.web.publish.item.create
     * <p><b>已废弃</b>，真接口是 {@link #publishItem}（mtop.idle.pc.idleitem.publish v1.0），
     * 来自 XianYuApis 真抓验证。保留这个方法为兼容旧 facade 调用，内部转调 publishItem 但参数不全，
     * 推荐直接用 publishItem 完整版。</p>
     */
    @Deprecated
    public JsonNode createProduct(String title, String price, String description,
                                   String categoryId, String images) {
        // 旧 facade 调用兼容：传进来的是简单参数，publishItem 需要复杂结构
        // 这里只能尽力拼一个最小 publish 调用，分类/所在地/运费全用默认/空
        Map<String, String> catDTO = new LinkedHashMap<>();
        if (categoryId != null && !categoryId.isEmpty()) catDTO.put("catId", categoryId);

        // price 转 priceInCent（元 → 分）：尝试解析
        String priceInCent = null;
        if (price != null && !price.isEmpty()) {
            try {
                double p = Double.parseDouble(price);
                priceInCent = String.valueOf((long) (p * 100));
            } catch (NumberFormatException ignored) {}
        }

        return publishItem(title, description, priceInCent, null,
                new ArrayList<>(), catDTO, new ArrayList<>(), new LinkedHashMap<>(), null);
    }

    /** 创建商品（JSON body 方式）— 已废弃，转调 publishItem */
    @Deprecated
    public JsonNode createProductWithBody(String title, String price, String description) {
        return createProduct(title, price, description, null, null);
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
