package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.*;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.config.AsyncConfig;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.product.service.SyncProgressService;
import cn.net.rjnetwork.xianyu.manager.product.service.SyncProgressService.Progress;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProductMapper productMapper;
    private final AccountService accountService;
    private final SyncProgressService syncProgressService;

    @Value("${file.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${file.upload-url-prefix:/uploads}")
    private String uploadUrlPrefix;

    public ProductService(ProductMapper productMapper, AccountService accountService,
                          SyncProgressService syncProgressService) {
        this.productMapper = productMapper;
        this.accountService = accountService;
        this.syncProgressService = syncProgressService;
    }

    /**
     * 落盘上传文件并返回可访问 URL。
     * 真实场景可替换为 OSS / 网盘直传，当前实现为本地落盘 + 静态资源映射。
     */
    public String storeFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();
        List<String> imgExts = List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
        List<String> videoExts = List.of(".mp4", ".mov", ".avi", ".mkv", ".webm");
        if (!imgExts.contains(ext) && !videoExts.contains(ext)) {
            throw new IllegalArgumentException("unsupported file type: " + ext);
        }

        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        File target = new File(dir, storedName);
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(file.getBytes());
        }
        return uploadUrlPrefix + "/" + storedName;
    }

    public Page<XianyuProduct> listPage(int pageNum, int pageSize, Long accountId, String keyword, String status) {
        Page<XianyuProduct> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuProduct> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) {
            wrapper.eq(XianyuProduct::getAccountId, accountId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(XianyuProduct::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(XianyuProduct::getStatus, status);
        }
        wrapper.orderByDesc(XianyuProduct::getUpdatedAt);
        productMapper.selectPage(page, wrapper);
        return page;
    }

    public XianyuProduct getById(Long id) {
        return productMapper.selectById(id);
    }

    /**
     * 创建商品并真发布到闲鱼 — 完整闭环
     * <p>真实抓包来源：参考项目 /Users/vim/Desktop/codes/github/XianYuApis/goofish_apis.py 的 public() 方法，
     * 链上 4 个 mtop 接口：recommendCategory → getDefaultLocation → publishItem（图片上传走外部，此处跳过）。</p>
     *
     * <p><b>发布闭环</b>（参考 XianYuApis 真抓）：本地落 DRAFT → 调闲鱼发布链 → 拿闲鱼返回 →
     * 回写本地 status=ON_SALE + itemId → 调 syncFromXianyu 同步完整信息（浏览量/分类/图片等）。</p>
     *
     * <p><b>图片限制</b>：闲鱼平台硬性要求商品必须有图片，无图发布会被拒
     * （FAIL_BIZ_ITEM_NO_PICS）。本地 URL（/uploads/xxx.jpg）会先上传到闲鱼 CDN
     * （stream-upload.goofish.com）拿到 alicdn URL 再传给 publishItem；
     * 若调用方已通过其他方式拿到闲鱼 CDN 图片 URL，直接传进来即可。
     * 若最终 imageInfoList 为空（未传图或全部上传失败），create 提前抛
     * IllegalArgumentException 友好提示，不会调 publishItem。</p>
     *
     * <p><b>发布 ≠ 上架已下架商品</b>：发布会生成新 itemId，原商品的浏览量/收藏/历史成交清零。
     * 这是闲鱼平台设计，不是 SDK 限制。</p>
     */
    @Transactional
    public XianyuProduct create(ProductCreateRequest request) {
        // 1. 先校验账号（accountId 非空 + 存在 + cookie 有效），再落库，
        //    避免 null accountId 触发 NOT NULL 约束异常冒泡成 500。
        Long accountId = request.getAccountId();
        if (accountId == null) {
            throw new IllegalArgumentException("请选择闲鱼账号");
        }
        XianyuAccount account = accountService.getById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在: " + accountId);
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("账号未登录或 Cookie 已过期，请重新扫码登录: " + accountId);
        }

        // 2. 本地先落 DRAFT 记录（保留请求痕迹，万一发布失败也有本地草稿可查）
        XianyuProduct product = new XianyuProduct();
        product.setAccountId(accountId);
        product.setTitle(request.getTitle());
        product.setPrice(request.getPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setStock(request.getStock());
        product.setCategoryId(request.getCategoryId());
        product.setDescription(request.getDescription());
        product.setImages(toJsonArray(request.getImages()));
        product.setVideos(toJsonArray(request.getVideos()));
        product.setStatus("DRAFT");
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);

        // 3. 构造 SDK：MtopApiClient + PublishApiService
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuPublishApiService publishApi = new XianyuPublishApiService(mtopClient);

        // 4. 步骤 A：AI 推荐分类（title + 图片信息；图片信息从本地 images URL 拼不出闲鱼 CDN 的 url/height/width，传空让 AI 只按标题推荐）
        JsonNode catResp = publishApi.recommendCategory(
                request.getTitle() != null ? request.getTitle() : "",
                new ArrayList<>()

        );
        // 解析 categoryPredictResult → catDTO
        Map<String, String> catDTO = new LinkedHashMap<>();
        JsonNode catPredict = catResp != null ? catResp.path("data").path("categoryPredictResult") : null;
        if (catPredict != null && !catPredict.isMissingNode()) {
            catDTO.put("catId", pickText(catPredict, "catId"));
            catDTO.put("catName", pickText(catPredict, "catName"));
            catDTO.put("channelCatId", pickText(catPredict, "channelCatId"));
            catDTO.put("tbCatId", pickText(catPredict, "tbCatId"));
        }
        // 若用户在前端表单里显式传了 categoryId，覆盖 AI 推荐结果（用户指定优先）
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
            catDTO.put("catId", request.getCategoryId());
        }

        // 5. 步骤 B：拿默认所在地（itemAddrDTO 必填）
        JsonNode locResp = publishApi.getDefaultLocation();
        Map<String, Object> addrDTO = new LinkedHashMap<>();
        JsonNode addrNode = locResp != null ? locResp.path("data").path("commonAddresses") : null;
        if (addrNode != null && addrNode.isArray() && addrNode.size() > 0) {
            JsonNode first = addrNode.get(0);
            addrDTO.put("area", pickText(first, "area"));
            addrDTO.put("city", pickText(first, "city"));
            addrDTO.put("divisionId", pickText(first, "divisionId"));
            // gps = "经度,纬度"（逗号拼接）
            String longitude = pickText(first, "longitude");
            String latitude = pickText(first, "latitude");
            if (!longitude.isEmpty() && !latitude.isEmpty()) {
                addrDTO.put("gps", longitude + "," + latitude);
            }
            addrDTO.put("poiId", pickText(first, "poiId"));
            addrDTO.put("poi", pickText(first, "poi"));
        }

        // 6. 步骤 C：图片真上传闲鱼 CDN（stream-upload.goofish.com），拿 url+pix → 拼 imageInfoList
        // 前端传的 images 是本地上传后的 URL（/uploads/xxx.jpg），闲鱼 publish 不认本地 URL，
        // 必须先把二进制传到闲鱼 CDN（alicdn.com），拿到的 URL 才能传给 publishItem。
        // 兼容：若前端直接传了闲鱼 CDN URL（alicdn.com），跳过本地上传直接用。
        List<Map<String, Object>> imageInfoList = new ArrayList<>();
        if (request.getImages() != null) {
            for (String imgUrl : request.getImages()) {
                if (imgUrl == null || imgUrl.isBlank()) continue;
                Map<String, Object> img = new LinkedHashMap<>();
                if (imgUrl.contains("alicdn.com")) {
                    // 已经是闲鱼 CDN URL，直接用（宽高未知传 0）
                    img.put("url", imgUrl);
                    img.put("width", 0);
                    img.put("height", 0);
                } else {
                    // 本地 URL（/uploads/xxx.jpg）→ 读文件二进制 → 调 uploadImage 真上传闲鱼 CDN
                    try {
                        XianyuPublishApiService.UploadResult ur = uploadLocalFileToXianyu(publishApi, imgUrl, true);
                        img.put("url", ur.url);
                        img.put("width", ur.width);
                        img.put("height", ur.height);
                    } catch (Exception uploadErr) {
                        // 单张图上传失败不阻塞其余图片，跳过这张继续；
                        // 若全部失败导致 imageInfoList 为空，下方校验会抛友好错误
                        continue;
                    }
                }
                imageInfoList.add(img);
            }
        }

        // 闲鱼平台硬性要求商品必须有图片（无图会返回 FAIL_BIZ_ITEM_NO_PICS）。
        // 若调用方未传图片、或所传图片全部上传闲鱼 CDN 失败，提前给出友好业务错误，
        // 不再往下走视频上传 / publishItem（否则闲鱼抛 NO_PICS 堆栈到前端，体验差）。
        if (imageInfoList.isEmpty()) {
            throw new IllegalArgumentException("闲鱼发布必须有图片，请至少上传一张图片");
        }

        // 视频同理：本地 URL → 调 uploadVideo 真上传闲鱼 CDN → 拿 url
        // 注意：publishItem 的 imageInfoDOList 闲鱼只接图片，视频走单独字段
        // 但 publishItem 当前 data 结构里没有视频字段（XianYuApis public 真抓里也没有），
        // 视频在闲鱼网页版发布页根本无入口，只在闲鱼 App。所以视频先上传到 CDN 拿 url 落本地，
        // 等 publishItem 后续补视频字段（或闲鱼 App 抓包定位真接口）才能传给闲鱼。
        List<String> xianyuVideoUrls = new ArrayList<>();
        if (request.getVideos() != null) {
            for (String videoUrl : request.getVideos()) {
                if (videoUrl == null || videoUrl.isBlank()) continue;
                if (videoUrl.contains("alicdn.com")) {
                    xianyuVideoUrls.add(videoUrl);
                } else {
                    try {
                        XianyuPublishApiService.UploadResult ur = uploadLocalFileToXianyu(publishApi, videoUrl, false);
                        xianyuVideoUrls.add(ur.url);
                    } catch (Exception uploadErr) {
                        // 视频上传失败不阻塞发布，跳过
                        continue;
                    }
                }
            }
        }
        // 把闲鱼 CDN 视频 URL 回写本地 product.videos，方便后续 syncFromXianyu 不丢
        if (!xianyuVideoUrls.isEmpty()) {
            product.setVideos(toJsonArray(xianyuVideoUrls));
        }

        // 7. 步骤 D：运费设置（默认按距离计费，templateId=-100；前端暂时未提供运费选项，用保守默认）
        Map<String, Object> deliverySettings = new LinkedHashMap<>();
        deliverySettings.put("supportFreight", true);
        deliverySettings.put("canFreeShipping", false);
        deliverySettings.put("onlyTakeSelf", false);
        deliverySettings.put("templateId", "-100");  // 按距离计费

        // 8. 步骤 E：价格转分（元 → 分）
        String priceInCent = null;
        if (request.getPrice() != null) {
            priceInCent = String.valueOf(request.getPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue());
        }
        String origPriceInCent = null;
        if (request.getOriginalPrice() != null) {
            origPriceInCent = String.valueOf(request.getOriginalPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue());
        }

        // 9. 步骤 F：真调 publishItem 提交发布
        JsonNode pubResp = publishApi.publishItem(
                request.getTitle() != null ? request.getTitle() : "",
                request.getDescription() != null ? request.getDescription() : "",
                priceInCent, origPriceInCent,
                imageInfoList, catDTO, new ArrayList<>(), addrDTO, deliverySettings
        );

        // 10. 检查发布结果：ret[0] 应含 SUCCESS
        if (!isMtopSuccess(pubResp)) {
            throw new IllegalStateException("Xianyu publish failed: " + safeMtopMsg(pubResp));
        }

        // 11. 回写本地：status=ON_SALE，尝试解析 itemId（部分版本返回，部分不返回需 syncFromXianyu 拉回）
        product.setStatus("ON_SALE");
        String publishedItemId = parseItemIdFromPublishResp(pubResp);
        if (publishedItemId != null && !publishedItemId.isEmpty()) {
            product.setItemId(publishedItemId);
        }
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);

        // 12. 调 syncFromXianyu 同步完整信息（浏览量/收藏/真实 itemId/图片 URL/分类等）
        // 发布后闲鱼商品列表会立即出现新商品，syncFromXianyu 按 accountId + itemId upsert
        try {
            syncFromXianyu(accountId);
        } catch (Exception syncErr) {
            // sync 失败不回滚发布（发布已经成功了），只记录日志
            // 同步可后续手动触发
        }

        return product;
    }

    /**
     * 同步单个商品到本地（通过 itemId 拉详情 → upsert）。
     * 用于改价/改库存后，将新发布的商品信息同步回本地 DB。
     *
     * @param accountId 账号 id
     * @param itemId    闲鱼商品 id
     */
    private void syncSingleItem(Long accountId, String itemId) {
        if (accountId == null || itemId == null || itemId.isBlank()) return;
        XianyuAccount account = accountService.getById(accountId);
        if (account == null || account.getCookieHeader() == null || account.getCookieHeader().isBlank()) return;

        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);

        try {
            JsonNode detailResp = productApi.getProductDetail(itemId);
            if (!isMtopSuccess(detailResp)) return;

            JsonNode detailData = detailResp.path("data");
            JsonNode b2cItem = detailData.path("b2cItemDO");
            JsonNode picDetail = detailData.path("picDetailDO");
            JsonNode itemDO = detailData.path("itemDO");

            String title = pickText(b2cItem, "title");
            if (title.isEmpty()) title = pickText(detailData, "title");
            BigDecimal price = pickBigDecimal(b2cItem, "priceInCent", "soldPrice");
            if (price == null) price = pickBigDecimal(detailData, "priceInfo.price", "soldPrice", "price");
            BigDecimal orig = pickBigDecimal(b2cItem, "oriPriceInCent", "originalPrice");
            Integer stock = pickInt(itemDO, "quantity");
            if (stock == null) stock = pickInt(b2cItem, "quantity");
            String description = pickText(b2cItem, "desc");
            if (description.isEmpty()) description = pickText(detailData, "desc");
            String images = extractImagesJson(picDetail.path("picUrlList"));
            if (images == null) images = extractImagesJson(b2cItem.path("picUrlList"));
            String mainImageUrl = firstPicUrl(picDetail.path("picUrlList"));
            if (mainImageUrl == null) mainImageUrl = firstPicUrl(b2cItem.path("picUrlList"));
            String rawStatus = pickString(itemDO, "itemStatus");
            if (rawStatus == null || rawStatus.isEmpty()) rawStatus = pickString(detailData, "itemStatus");
            String status = mapStatus(rawStatus);

            XianyuProduct p = new XianyuProduct();
            p.setAccountId(accountId);
            p.setItemId(itemId);
            p.setTitle(title);
            if (price != null) p.setPrice(price);
            if (orig != null) p.setOriginalPrice(orig);
            if (stock != null) p.setStock(stock);
            p.setImageUrl(mainImageUrl);
            p.setImages(images);
            if (description != null) p.setDescription(description);
            if (status != null) p.setStatus(status);
            p.setUpdatedAt(LocalDateTime.now());

            // upsert by accountId + itemId
            LambdaQueryWrapper<XianyuProduct> qw = new LambdaQueryWrapper<>();
            qw.eq(XianyuProduct::getAccountId, accountId).eq(XianyuProduct::getItemId, itemId);
            XianyuProduct existing = productMapper.selectOne(qw);
            if (existing != null) {
                p.setId(existing.getId());
                p.setCreatedAt(existing.getCreatedAt());
                productMapper.updateById(p);
            } else {
                p.setCreatedAt(LocalDateTime.now());
                productMapper.insert(p);
            }
        } catch (Exception e) {
            logger.warn("syncSingleItem: failed for account={} item={}: {}", accountId, itemId, e.getMessage());
        }
    }

    /** 从 publish 接口返回里解析 itemId（部分版本返回 data.itemId，部分不返回需 syncFromXianyu 拉） */
    private String parseItemIdFromPublishResp(JsonNode resp) {
        if (resp == null) return null;
        JsonNode data = resp.path("data");
        if (data.isMissingNode() || data.isNull()) return null;
        // 候选字段名：itemId / id / itemDO.itemId
        String[] candidates = {"itemId", "id", "itemDO.itemId"};
        for (String key : candidates) {
            if (key.contains(".")) {
                // 嵌套：itemDO.itemId
                String[] parts = key.split("\\.");
                JsonNode node = data;
                for (String p : parts) {
                    node = node.path(p);
                    if (node.isMissingNode() || node.isNull()) break;
                }
                if (!node.isMissingNode() && !node.isNull()) {
                    String v = node.asText("");
                    if (!v.isEmpty()) return v;
                }
            } else {
                JsonNode node = data.get(key);
                if (node != null && !node.isNull()) {
                    String v = node.asText("");
                    if (!v.isEmpty()) return v;
                }
            }
        }
        return null;
    }

    /** 从 JsonNode 拿指定字段文本值，缺失返空串 */
    private String pickText(JsonNode node, String fieldName) {
        if (node == null) return "";
        JsonNode v = node.path(fieldName);
        if (v.isMissingNode() || v.isNull()) return "";
        return v.asText("");
    }

    /** 多字段名兜底取文本，命中任一即返，全缺失返空串 */
    private String pickText(JsonNode node, String... keys) {
        if (node == null) return "";
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.isNull()) return v.asText("");
        }
        return "";
    }

    /**
     * 读本地文件二进制 → 调闲鱼 CDN 上传 → 拿 url+pix。
     * @param publishApi 闲鱼发布服务（含 uploadImage/uploadVideo）
     * @param localUrl   本地 URL（如 /uploads/xxx.jpg 或 http://localhost:8080/uploads/xxx.jpg）
     * @param isImage    true=图片走 uploadImage，false=视频走 uploadVideo
     * @return 闲鱼 CDN 上传结果（url + width + height）
     */
    private XianyuPublishApiService.UploadResult uploadLocalFileToXianyu(
            XianyuPublishApiService publishApi, String localUrl, boolean isImage) throws Exception {
        // 把本地 URL 解析成本地文件路径：
        //   /uploads/xxx.jpg → uploadDir/xxx.jpg
        //   http://localhost:8080/uploads/xxx.jpg → uploadDir/xxx.jpg
        String filePath = localUrl;
        // 剥 host 部分（若前端传了完整 URL）
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            int idx = filePath.indexOf("/uploads/");
            if (idx >= 0) filePath = filePath.substring(idx + "/uploads/".length() - 1); // 保留开头的 /
        }
        // 去 uploadUrlPrefix 前缀（默认 /uploads）
        String prefix = uploadUrlPrefix != null ? uploadUrlPrefix : "/uploads";
        if (filePath.startsWith(prefix)) {
            filePath = filePath.substring(prefix.length());
        }
        // 拼 uploadDir + filePath → 真实本地文件
        String fullLocalPath = (uploadDir != null ? uploadDir : "./data/uploads") + filePath;
        java.io.File localFile = new java.io.File(fullLocalPath);
        if (!localFile.exists() || !localFile.isFile()) {
            throw new IllegalStateException("本地文件不存在：" + fullLocalPath);
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(localFile.toPath());
        String filename = localFile.getName();
        if (isImage) {
            return publishApi.uploadImage(bytes, filename);
        } else {
            return publishApi.uploadVideo(bytes, filename);
        }
    }

    @Transactional
    public XianyuProduct update(ProductUpdateRequest request) {
        XianyuProduct product = productMapper.selectById(request.getId());
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + request.getId());
        }
        if (request.getTitle() != null) product.setTitle(request.getTitle());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getOriginalPrice() != null) product.setOriginalPrice(request.getOriginalPrice());
        if (request.getStock() != 0) product.setStock(request.getStock());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getImages() != null) product.setImages(toJsonArray(request.getImages()));
        if (request.getVideos() != null) product.setVideos(toJsonArray(request.getVideos()));
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    /**
     * 保存商品级虚拟发货配置（deliver_type + deliver_content_template + goods_type）。
     * <p>供"虚拟发货页 → 商品列表 → 配置弹窗"调用，把商品串进虚拟发货链路。</p>
     */
    @Transactional
    public XianyuProduct saveVirtualShipConfig(Long productId,
                                                String goodsType,
                                                String deliverType,
                                                String deliverContentTemplate) {
        XianyuProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        if (goodsType != null) product.setGoodsType(goodsType);
        if (deliverType != null) product.setDeliverType(deliverType);
        // 模板允许清空（传 null 不动，传空串清空）
        if (deliverContentTemplate != null) product.setDeliverContentTemplate(deliverContentTemplate);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    /**
     * 查询所有虚拟商品（goods_type=VIRTUAL），供虚拟发货页商品列表展示。
     */
    public List<XianyuProduct> listVirtualProducts(Long accountId) {
        LambdaQueryWrapper<XianyuProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuProduct::getGoodsType, "VIRTUAL");
        if (accountId != null) {
            wrapper.eq(XianyuProduct::getAccountId, accountId);
        }
        wrapper.orderByDesc(XianyuProduct::getUpdatedAt);
        return productMapper.selectList(wrapper);
    }

    @Transactional
    public void delete(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) return;
        // 先调闲鱼商品删除接口，闲鱼侧真正下架并标记为"已删除"；成功后再删本地记录
        try {
            XianyuAccount account = accountService.getById(product.getAccountId());
            if (account != null && account.getCookieHeader() != null && !account.getCookieHeader().isBlank()) {
                String itemId = product.getItemId();
                if (itemId != null && !itemId.isBlank()) {
                    XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
                    XianyuProductEditApiService editApi = new XianyuProductEditApiService(mtopClient);
                    JsonNode resp = editApi.deleteProduct(itemId);
                    // 闲鱼删除失败不阻塞本地删除（只 warn），否则用户无法清理本地脏数据
                    if (!isMtopSuccess(resp)) {
                        logger.warn("Xianyu item delete failed, locally deleted anyway: {} — {}", product.getId(), safeMtopMsg(resp));
                    }
                } else {
                    logger.warn("No itemId for local product {}, skip xianyu delete", product.getId());
                }
            } else {
                logger.warn("No cookie for account {}, skip xianyu delete", product.getAccountId());
            }
        } catch (Exception delErr) {
            logger.warn("Xianyu item delete threw, locally deleted anyway: {}", delErr.getMessage());
        }
        productMapper.deleteById(id);
    }

    /**
     * 拉闲鱼商品分类树 — 真调 SDK XianyuPublishFormApiService.getCategoryTree
     * <p>前端创建商品表单的分类下拉选择用这个，不让用户手输 catId。
     * 闲鱼分类树接口：mtop.idle.web.publish.category.tree，返回分类树节点列表，
     * 每节点含 catId/catName/channelCatId/tbCatId/children[]（嵌套子分类）。</p>
     *
     * @param accountId 账号 id（按账号 cookie 调闲鱼接口）
     * @return 闲鱼返回的 JSON 分类树，前端按 children[] 嵌套渲染下拉
     */
    public JsonNode getCategoryTree(Long accountId) {
        XianyuAccount account = accountService.getById(accountId);
        if (account == null) throw new IllegalArgumentException("Account not found: " + accountId);
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + accountId);
        }
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuPublishFormApiService formApi = new XianyuPublishFormApiService(mtopClient);
        return formApi.getCategoryTree("1");
    }

    /**
     * 商品上架 — 真实调用闲鱼 mtop.taobao.idle.item.upshelf v2.0
     * <p>商品是从闲鱼同步来的（持有 itemId），所以上下架必须真打闲鱼接口，
     * 不能只改本地 DB 状态。流程：
     * <ol>
     *   <li>按 productId 取本地商品 → 拿到 itemId 和 accountId</li>
     *   <li>按 accountId 取账号 cookie，校验非空</li>
     *   <li>构造 SDK：XianyuMtopApiClient + XianyuProductEditApiService</li>
     *   <li>调 shelfOn(itemId) 真打闲鱼上架接口</li>
     *   <li>闲鱼返回成功后再把本地 status 改为 ON_SALE</li>
     * </ol>
     * 若闲鱼接口失败，抛 IllegalStateException 带上闲鱼返回的 msg，本地状态不动。</p>
     */
    @Transactional
    public XianyuProduct shelfOn(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");

        XianyuAccount account = accountService.getById(product.getAccountId());
        if (account == null) throw new IllegalStateException("Account not found: " + product.getAccountId());
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + product.getAccountId());
        }
        String itemId = product.getItemId();
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalStateException("Product has no itemId, can not call Xianyu shelf-on API");
        }

        // 真打闲鱼上架接口
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductEditApiService editApi = new XianyuProductEditApiService(mtopClient);
        JsonNode resp = editApi.shelfOn(itemId);
        if (!isMtopSuccess(resp)) {
            throw new IllegalStateException("Xianyu shelf-on failed: " + safeMtopMsg(resp));
        }

        // 闲鱼确认成功后再改本地状态
        product.setStatus("ON_SALE");
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    /**
     * 商品下架 — 真实调用闲鱼 mtop.taobao.idle.item.downshelf v2.0
     * <p>下架接口已 CDP 真抓验证（2026-07-19 详情页下架按钮 onClick）。</p>
     */
    @Transactional
    public XianyuProduct shelfOff(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");

        XianyuAccount account = accountService.getById(product.getAccountId());
        if (account == null) throw new IllegalStateException("Account not found: " + product.getAccountId());
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + product.getAccountId());
        }
        String itemId = product.getItemId();
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalStateException("Product has no itemId, can not call Xianyu shelf-off API");
        }

        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductEditApiService editApi = new XianyuProductEditApiService(mtopClient);
        JsonNode resp = editApi.shelfOff(itemId);
        if (!isMtopSuccess(resp)) {
            throw new IllegalStateException("Xianyu shelf-off failed: " + safeMtopMsg(resp));
        }

        product.setStatus("OFF_SALE");
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    /**
     * 判断 mtop 返回是否成功。
     * <p>闲鱼 mtop 返回结构：ret[0] 形如 "FAIL_SYS_API_NOT_FOUNDED::api not found" 或
     * "SUCCESS::xxx"。SUCCESS 或 ret 为空都算成功（部分接口无 ret 字段但 data 非空也算成功）。</p>
     */
    private boolean isMtopSuccess(JsonNode resp) {
        if (resp == null) return false;
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) {
            String first = ret.get(0).asText("");
            // SUCCESS::xxx / FAIL_SYS_XXX::xxx → 看 :: 前段
            String code = first.contains("::") ? first.substring(0, first.indexOf("::")) : first;
            return "SUCCESS".equalsIgnoreCase(code);
        }
        // 没有 ret 字段，看 data 是否非空
        JsonNode data = resp.path("data");
        return data != null && !data.isMissingNode() && !data.isNull();
    }

    private String safeMtopMsg(JsonNode resp) {
        if (resp == null) return "no response";
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) {
            return ret.get(0).asText("unknown");
        }
        return resp.toString().length() > 300 ? resp.toString().substring(0, 300) : resp.toString();
    }

    /**
     * 改价 — 走「新建替代」完整流程：获取原商品信息 → 改价格 → 发布新商品 → 下架原商品
     * <p>闲鱼 PC 无独立改价接口，SDK 的 XianyuProductEditApiService.updatePrice 已封装完整逻辑：
     * getProductDetail(itemId) → 提取原商品字段 → 改价格 → publishItem(null, ...) 发布新商品 → shelfOff(itemId) 下架原商品。</p>
     *
     * <p>本方法负责：校验 → 调 SDK → 解析新 itemId → 更新本地 DB（原商品标记下架，新商品 upsert）。</p>
     */
    @Transactional
    public XianyuProduct updatePrice(Long id, java.math.BigDecimal price) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        if (price == null) throw new IllegalArgumentException("price is required");

        // 1. 校验账号 cookie + itemId
        XianyuAccount account = accountService.getById(product.getAccountId());
        if (account == null) throw new IllegalStateException("Account not found: " + product.getAccountId());
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + product.getAccountId());
        }
        String itemId = product.getItemId();
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalStateException("Product has no itemId, can not update price");
        }

        // 2. 构造 SDK（三参数构造函数，支持改价改库存）
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);
        XianyuPublishApiService publishApi = new XianyuPublishApiService(mtopClient);
        XianyuProductEditApiService editApi = new XianyuProductEditApiService(mtopClient, productApi, publishApi);

        // 3. 调 SDK updatePrice：获取详情 → 改价 → 发布新商品 → 下架原商品
        JsonNode pubResp = editApi.updatePrice(itemId, price.toPlainString());
        if (!isMtopSuccess(pubResp)) {
            throw new IllegalStateException("Xianyu updatePrice (republish new + shelf off old) failed: " + safeMtopMsg(pubResp));
        }

        // 4. 解析新商品 itemId
        String newItemId = parseItemIdFromPublishResp(pubResp);

        // 5. 更新本地 DB：原商品标记下架（闲鱼已自动下架，本地同步状态）
        product.setStatus("OFF_SALE");
        product.setPrice(price);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);


        // 6. 同步新商品信息到本地（如果有新 itemId 则 upsert 新记录，否则通过 syncFromXianyu 拉回）
        if (newItemId != null && !newItemId.isEmpty()) {
            syncSingleItem(account.getId(), newItemId);
        } else {
            // 发布接口未返回新 itemId，通过列表同步拉回
            try { syncFromXianyu(account.getId()); } catch (Exception e) { /* 不影响主流程 */ }
        }

        return product;
    }

    /** 从 JsonNode 拿指定字段 int 值，缺失返 0 */
    private int pickInt(JsonNode node, String fieldName) {
        if (node == null) return 0;
        JsonNode v = node.path(fieldName);
        if (v.isMissingNode() || v.isNull()) return 0;
        try { return v.asInt(0); } catch (Exception e) { return 0; }
    }

    /**
     * 改库存 — 走「新建替代」完整流程：获取原商品信息 → 改库存 → 发布新商品 → 下架原商品
     * <p>闲鱼 PC 无独立改库存接口，SDK 的 XianyuProductEditApiService.updateStock 已封装完整逻辑：
     * getProductDetail(itemId) → 提取原商品字段 → 改库存 → publishItem(null, ...) 发布新商品 → shelfOff(itemId) 下架原商品。</p>
     *
     * <p>本方法负责：校验 → 调 SDK → 解析新 itemId → 更新本地 DB（原商品标记下架，新商品 upsert）。</p>
     */
    @Transactional
    public XianyuProduct updateStock(Long id, Integer stock) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        if (stock == null) throw new IllegalArgumentException("stock is required");

        // 1. 校验账号 cookie + itemId
        XianyuAccount account = accountService.getById(product.getAccountId());
        if (account == null) throw new IllegalStateException("Account not found: " + product.getAccountId());
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + product.getAccountId());
        }
        String itemId = product.getItemId();
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalStateException("Product has no itemId, can not update stock");
        }

        // 2. 构造 SDK（三参数构造函数，支持改价改库存）
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);
        XianyuPublishApiService publishApi = new XianyuPublishApiService(mtopClient);
        XianyuProductEditApiService editApi = new XianyuProductEditApiService(mtopClient, productApi, publishApi);

        // 3. 调 SDK updateStock：获取详情 → 改库存 → 发布新商品 → 下架原商品
        JsonNode pubResp = editApi.updateStock(itemId, String.valueOf(stock));
        if (!isMtopSuccess(pubResp)) {
            throw new IllegalStateException("Xianyu updateStock (republish new + shelf off old) failed: " + safeMtopMsg(pubResp));
        }

        // 4. 解析新商品 itemId
        String newItemId = parseItemIdFromPublishResp(pubResp);

        // 5. 更新本地 DB：原商品标记下架（闲鱼已自动下架，本地同步状态）
        product.setStatus("OFF_SALE");
        product.setStock(stock);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);

        // 6. 同步新商品信息到本地（如果有新 itemId 则 upsert 新记录，否则通过 syncFromXianyu 拉回）
        if (newItemId != null && !newItemId.isEmpty()) {
            syncSingleItem(account.getId(), newItemId);
        } else {
            // 发布接口未返回新 itemId，通过列表同步拉回
            try { syncFromXianyu(account.getId()); } catch (Exception e) { /* 不影响主流程 */ }
        }

        return product;
    }

    public List<XianyuProduct> listByAccountId(Long accountId) {
        LambdaQueryWrapper<XianyuProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuProduct::getAccountId, accountId);
        wrapper.orderByDesc(XianyuProduct::getUpdatedAt);
        return productMapper.selectList(wrapper);
    }

    /**
     * 从闲鱼同步指定账号的商品到本地。
     * 拉在线商品列表（默认 100 条），每条约一次详情接口补齐 description / 库存 / 多图 / 真实状态，
     * 按 accountId + itemId 做 upsert，不删除本地已有记录。
     *
     * <p>注意：本方法每条商品会多调一次详情接口（HTTP），同步 N 件商品 ≈ N+1 次闲鱼请求，
     * 建议在异步任务里调用（前端进度条 / 定时任务），不要阻塞请求线程。</p>
     *
     * @param accountId 账号 id
     * @return 同步结果统计 {synced, inserted, updated}
     */
    public SyncResult syncFromXianyu(Long accountId) {
        return syncFromXianyu(accountId, 100);
    }

    /**
     * 从闲鱼同步指定账号的商品到本地（可指定每页条数）。
     *
     * @param accountId 账号 id
     * @param pageSize  列表接口每页条数（上限 100）
     * @return 同步结果统计 {synced, inserted, updated}
     */
    public SyncResult syncFromXianyu(Long accountId, int pageSize) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        XianyuAccount account = accountService.getById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("Account has no cookie, please re-login: " + accountId);
        }

        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);
        List<JsonNode> cards = fetchAllProductCards(productApi, pageSize, null);
        List<XianyuProduct> toUpsert = collectFromDetail(accountId, productApi, cards, null, cards.size());

        int inserted = 0;
        int updated = 0;
        int failed = 0;
        for (XianyuProduct p : toUpsert) {
            try {
                LambdaQueryWrapper<XianyuProduct> qw = new LambdaQueryWrapper<>();
                qw.eq(XianyuProduct::getAccountId, accountId)
                        .eq(XianyuProduct::getItemId, p.getItemId());
                XianyuProduct existing = productMapper.selectOne(qw);
                if (existing != null) {
                    p.setId(existing.getId());
                    p.setCreatedAt(existing.getCreatedAt());
                    productMapper.updateById(p);
                    updated++;
                } else {
                    p.setCreatedAt(LocalDateTime.now());
                    productMapper.insert(p);
                    inserted++;
                }
            } catch (Exception e) {
                logger.error("syncFromXianyu: upsert failed for item {}: {}", p.getItemId(), e.getMessage());
                failed++;
            }
        }
        logger.info("syncFromXianyu: account {} done. fetched={} synced={} inserted={} updated={} failed={}",
                accountId, cards.size(), inserted + updated, inserted, updated, failed);
        return new SyncResult(inserted + updated, inserted, updated);
    }

    /**
     * 执行商品同步（供 controller 手动提交到线程池调用，本方法不再加 @Async 注解，
     * 避免与手动线程池提交混在一起导致静默失败）。
     */
    public void syncFromXianyuAsync(Long accountId, String syncId) {
        syncProgressService.update(Progress.listing(syncId));
        try {
            XianyuAccount account = accountService.getById(accountId);
            if (account == null) {
                syncProgressService.update(Progress.failed(syncId, "账号不存在: " + accountId));
                syncProgressService.finish(accountId, syncId);
                return;
            }
            if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
                syncProgressService.update(Progress.failed(syncId, "账号 Cookie 过期或未设置，请重新扫码登录"));
                syncProgressService.finish(accountId, syncId);
                return;
            }

            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);
            List<JsonNode> cards = fetchAllProductCards(productApi, 20, syncId);
            if (cards.isEmpty()) {
                syncProgressService.update(Progress.completed(syncId, 0, 0, 0));
                syncProgressService.finish(accountId, syncId);
                return;
            }
            int total = cards.size();
            syncProgressService.update(Progress.detailing(syncId, 0, total));

            List<XianyuProduct> toUpsert = collectFromDetail(accountId, productApi, cards, syncId, total);

            int inserted = 0, updated = 0, failed = 0;
            for (XianyuProduct p : toUpsert) {
                try {
                    LambdaQueryWrapper<XianyuProduct> qw = new LambdaQueryWrapper<>();
                    qw.eq(XianyuProduct::getAccountId, accountId)
                            .eq(XianyuProduct::getItemId, p.getItemId());
                    XianyuProduct existing = productMapper.selectOne(qw);
                    if (existing != null) {
                        p.setId(existing.getId());
                        p.setCreatedAt(existing.getCreatedAt());
                        productMapper.updateById(p);
                        updated++;
                    } else {
                        p.setCreatedAt(LocalDateTime.now());
                        productMapper.insert(p);
                        inserted++;
                    }
                } catch (Exception e) {
                    logger.error("sync: upsert failed item {}", p.getItemId(), e);
                    failed++;
                }
            }
            syncProgressService.update(Progress.completed(syncId, inserted, updated, failed));
            syncProgressService.finish(accountId, syncId);
        } catch (Exception e) {
            logger.error("sync: fatal error account {}", accountId, e);
            syncProgressService.update(Progress.failed(syncId, e.getMessage()));
            syncProgressService.finish(accountId, syncId);
        }
    }

    /**
     * 拉详情、列出待 upsert 的 XianyuProduct 列表（在独立事务外调，避免长事务）。
     * 每处理一条就更新进度。
     */
    private List<XianyuProduct> collectFromDetail(Long accountId, XianyuProductApiService productApi,
                                                    List<JsonNode> cards, String syncId, int total) {
        List<XianyuProduct> list = new ArrayList<>();
        int current = 0;
        for (JsonNode card : cards) {
            current++;
            JsonNode item = card.has("cardData") ? card.get("cardData") : card;
            String itemId = pickString(item, "id", "itemId", "item_id");
            if (itemId == null || itemId.isBlank()) continue;

            String title = pickString(item, "title", "name", "itemTitle");
            BigDecimal price = pickBigDecimal(item, "priceInfo.price", "detailParams.soldPrice",
                    "soldPrice", "price", "itemPrice");
            BigDecimal orig = pickBigDecimal(item, "originalPrice", "oriPrice", "marketPrice");
            String rawStatus = pickString(item, "itemStatus", "status");
            String mainImageUrl = pickString(item, "picInfo.picUrl", "detailParams.picUrl", "picUrl");

            // 详情接口补齐
            String description = null;
            Integer stock = null;
            String images = null;
            String detailStatus = null;
            try {
                JsonNode detailResp = productApi.getProductDetail(itemId);
                if (isMtopSuccess(detailResp)) {
                    JsonNode detailData = detailResp.path("data");
                    JsonNode b2cItem = detailData.path("b2cItemDO");
                    JsonNode picDetail = detailData.path("picDetailDO");
                    JsonNode itemDO = detailData.path("itemDO");
                    description = pickText(b2cItem, "desc");
                    if (description.isEmpty()) description = pickText(detailData, "desc");
                    Integer q = pickInt(itemDO, "quantity");
                    if (q == null) q = pickInt(b2cItem, "quantity");
                    if (q != null) stock = q;
                    images = extractImagesJson(picDetail.path("picUrlList"));
                    if (images == null) images = extractImagesJson(b2cItem.path("picUrlList"));
                    if (images == null) images = extractImagesJson(detailData.path("picUrlList"));
                    detailStatus = pickString(itemDO, "itemStatus");
                    if (detailStatus == null || detailStatus.isEmpty()) detailStatus = pickString(detailData, "itemStatus");
                    // 主图兜底：取详情第一张
                    String detailMain = firstPicUrl(picDetail.path("picUrlList"));
                    if (detailMain == null) detailMain = firstPicUrl(b2cItem.path("picUrlList"));
                    if (detailMain != null) mainImageUrl = detailMain;
                }
            } catch (Exception e) {
                logger.warn("sync: detail failed item {}: {}", itemId, e.getMessage());
            }

            if (rawStatus == null || rawStatus.isEmpty()) rawStatus = detailStatus;
            String status = mapStatus(rawStatus);
            if (images == null) images = extractImagesFromImageInfos(pickString(item, "detailParams.imageInfos"));
            if (mainImageUrl == null || mainImageUrl.isBlank()) mainImageUrl = firstImageFromJsonArray(images);
            if (orig == null) orig = extractOriginalPriceFromLabels(item);

            XianyuProduct p = new XianyuProduct();
            p.setAccountId(accountId);
            p.setItemId(itemId);
            p.setTitle(title);
            if (price != null) p.setPrice(price);
            if (orig != null) p.setOriginalPrice(orig);
            if (stock != null) p.setStock(stock);
            p.setImageUrl(mainImageUrl);
            p.setImages(images);
            if (description != null) p.setDescription(description);
            if (status != null) p.setStatus(status);
            applyListFields(p, item, card, rawStatus);
            p.setUpdatedAt(LocalDateTime.now());
            list.add(p);

            // 诊断：每 10 条记录一次字段写入情况，定位同步是否拿到数据
            if (syncId != null && (current % 10 == 0 || current == total)) {
                logger.info("sync: detail item {} descLen={} imgLen={} imgUrl={}",
                        itemId,
                        description == null ? "null" : String.valueOf(description.length()),
                        images == null ? "null" : String.valueOf(images.length()),
                        mainImageUrl == null ? "null" : mainImageUrl.substring(0, Math.min(60, mainImageUrl.length())));
                syncProgressService.update(Progress.detailing(syncId, current, total));
            }
        }
        return list;
    }

    /**
     * 把图片节点（数组）序列化为 JSON 数组字符串（["url1","url2",...]）。
     * 每项支持：纯字符串 / 对象取 url 或 picUrl。空则返回 null。
     */
    private String extractImagesJson(JsonNode picList) {
        if (picList == null || !picList.isArray() || picList.size() == 0) return null;
        List<String> urls = new ArrayList<>();
        for (JsonNode pic : picList) {
            if (pic.isTextual()) {
                String s = pic.asText();
                if (s != null && !s.isBlank()) urls.add(s);
            } else {
                String u = pickText(pic, "url", "picUrl", "cdnUrl", "imageUrl");
                if (!u.isEmpty()) urls.add(u);
            }
        }
        if (urls.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(urls.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    /**
     * 取图片节点（数组）里第一个 URL，无则 null。
     */
    private String firstPicUrl(JsonNode picList) {
        if (picList == null || !picList.isArray() || picList.size() == 0) return null;
        for (JsonNode pic : picList) {
            if (pic.isTextual()) {
                String s = pic.asText();
                if (s != null && !s.isBlank()) return s;
            } else {
                String u = pickText(pic, "url", "picUrl", "cdnUrl", "imageUrl");
                if (!u.isEmpty()) return u;
            }
        }
        return null;
    }

    /**
     * 从商品详情接口取真库存。真验结论（2026-07-21 dump）：
     * 真库存字段是 data.itemDO.quantity；商品列表接口不返回库存，故必须拉详情。
     * 拉失败返回 null（调用方回退到列表字段）。
     */
    private Integer resolveStockFromDetail(XianyuProductApiService productApi, String itemId) {
        try {
            JsonNode resp = productApi.getProductDetail(itemId);
            if (resp == null) return null;
            JsonNode itemDO = resp.path("data").path("itemDO").path("quantity");
            if (itemDO.isMissingNode() || itemDO.isNull()) return null;
            return itemDO.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    private List<JsonNode> fetchAllProductCards(XianyuProductApiService productApi, int pageSize, String syncId) {
        int size = Math.min(100, Math.max(1, pageSize));
        List<JsonNode> cards = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
            JsonNode resp = productApi.getMyProducts(String.valueOf(page), String.valueOf(size));
            JsonNode listNode = resolveProductList(resp);
            int count = 0;
            if (listNode != null && listNode.isArray()) {
                for (JsonNode card : listNode) {
                    cards.add(card);
                    count++;
                }
            }
            boolean nextPage = hasNextProductPage(resp);
            logger.info("sync products page {} size={} count={} nextPage={} totalFetched={}", page, size, count, nextPage, cards.size());
            if (syncId != null) {
                syncProgressService.update(Progress.listing(syncId));
            }
            if (!nextPage || count == 0) break;
        }
        return cards;
    }

    private boolean hasNextProductPage(JsonNode resp) {
        JsonNode data = resp != null && resp.has("data") ? resp.path("data") : resp;
        return data != null && data.path("nextPage").asBoolean(false);
    }

    private void applyListFields(XianyuProduct p, JsonNode item, JsonNode card, String rawStatus) {
        p.setCategoryId(pickString(item, "categoryId", "detailParams.categoryId"));
        p.setDetailUrl(pickString(item, "detailUrl"));
        p.setAuctionType(pickString(item, "auctionType"));
        p.setItemStatusRaw(rawStatus);
        p.setPostInfo(pickString(item, "detailParams.postInfo", "postInfo"));
        p.setImageInfos(pickString(item, "detailParams.imageInfos"));
        p.setPicWidth(pickInt(item, "picInfo.width", "detailParams.picWidth"));
        p.setPicHeight(pickInt(item, "picInfo.height", "detailParams.picHeight"));
        JsonNode hasVideo = navigate(item, "picInfo.hasVideo");
        if (hasVideo != null && !hasVideo.isMissingNode() && !hasVideo.isNull()) {
            p.setHasVideo(hasVideo.asBoolean(false));
        }
        p.setRawData(serializeJson(item != null && !item.isMissingNode() ? item : card));
    }

    private String serializeJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private String extractImagesFromImageInfos(String imageInfos) {
        if (imageInfos == null || imageInfos.isBlank()) return null;
        try {
            JsonNode arr = MAPPER.readTree(imageInfos);
            return extractImagesJson(arr);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstImageFromJsonArray(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) return null;
        try {
            JsonNode arr = MAPPER.readTree(imagesJson);
            if (arr.isArray() && arr.size() > 0) return arr.get(0).asText(null);
        } catch (Exception ignored) {}
        return null;
    }

    private BigDecimal extractOriginalPriceFromLabels(JsonNode item) {
        JsonNode tagList = item.path("itemLabelDataVO").path("labelData").path("r3").path("tagList");
        if (!tagList.isArray()) return null;
        for (JsonNode tag : tagList) {
            String content = pickString(tag, "data.content");
            if (content == null || !content.startsWith("¥")) continue;
            try {
                return new BigDecimal(content.substring(1).trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** 闲鱼返回结构多变，按优先级探若干路径名。真实结构为 data.cardList[] */
    private JsonNode resolveProductList(JsonNode resp) {
        if (resp == null) return null;
        // resp 本身可能就是 data 节点，也可能要进 data
        JsonNode data = resp.has("data") ? resp.get("data") : resp;
        String[] candidates = {"cardList", "mygoodsList", "list", "items", "itemList", "result"};
        for (String key : candidates) {
            JsonNode n = data != null ? data.get(key) : null;
            if (n != null && n.isArray()) return n;
        }
        // 顶层也试一次
        for (String key : candidates) {
            JsonNode n = resp.get(key);
            if (n != null && n.isArray()) return n;
        }
        return null;
    }

    private String pickString(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            JsonNode n = navigate(node, k);
            if (n != null && !n.isNull() && !n.asText("").isBlank()) return n.asText();
        }
        return null;
    }

    /** 按点分路径（如 priceInfo.price）向下导航到目标节点 */
    private JsonNode navigate(JsonNode root, String path) {
        if (root == null || path == null) return null;
        JsonNode cur = root;
        for (String seg : path.split("\\.")) {
            if (cur == null || !cur.isObject()) return null;
            cur = cur.get(seg);
        }
        return cur;
    }

    private BigDecimal pickBigDecimal(JsonNode node, String... keys) {
        String s = pickString(node, keys);
        if (s == null) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private Integer pickInt(JsonNode node, String... keys) {
        String s = pickString(node, keys);
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    /** 闲鱼图片字段可能是单 url、数组、或对象内 url，统一规整为 JSON 数组字符串 */
    private String pickImages(JsonNode item) {
        String[] keys = {"picUrl", "image", "imageUrl", "mainPic", "pictures"};
        for (String k : keys) {
            JsonNode n = item.get(k);
            if (n == null || n.isNull()) continue;
            if (n.isTextual()) return "[\"" + n.asText().replace("\"", "\\\"") + "\"]";
            if (n.isArray() && n.size() > 0) {
                List<String> urls = new ArrayList<>();
                for (JsonNode img : n) {
                    if (img.isTextual()) urls.add(img.asText());
                    else if (img.has("url")) urls.add(img.get("url").asText());
                }
                if (!urls.isEmpty()) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < urls.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append("\"").append(urls.get(i).replace("\"", "\\\"")).append("\"");
                    }
                    return sb.append("]").toString();
                }
            }
        }
        return null;
    }

    /** 闲鱼商品状态映射到本地枚举。真实 itemStatus 是数字：0=在售，1=下架，2=草稿 */
    private String mapStatus(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // 数字形式
        if ("0".equals(s)) return "ON_SALE";
        if ("1".equals(s)) return "OFF_SALE";
        if ("2".equals(s)) return "DRAFT";
        // 字符串形式兜底
        String sl = s.toLowerCase();
        if (sl.contains("onsale") || sl.contains("on_sale") || sl.contains("selling")) return "ON_SALE";
        if (sl.contains("offsale") || sl.contains("off_sale") || sl.contains("soldout") || sl.contains("off")) return "OFF_SALE";
        if (sl.contains("draft") || sl.contains("wait")) return "DRAFT";
        return "ON_SALE"; // 默认按在售处理
    }

    /** 同步结果统计 */
    public static class SyncResult {
        public final int synced;
        public final int inserted;
        public final int updated;
        public SyncResult(int synced, int inserted, int updated) {
            this.synced = synced;
            this.inserted = inserted;
            this.updated = updated;
        }
    }



    /** 将 URL 列表序列化为 JSON 数组字符串，null/空列表返回 null */
    private String toJsonArray(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(urls.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }
}
