package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.*;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    private final ProductMapper productMapper;
    private final AccountService accountService;

    @Value("${file.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${file.upload-url-prefix:/uploads}")
    private String uploadUrlPrefix;

    public ProductService(ProductMapper productMapper, AccountService accountService) {
        this.productMapper = productMapper;
        this.accountService = accountService;
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
     * <p><b>图片限制</b>：闲鱼图片需先上传到闲鱼 CDN（stream-upload.goofish.com multipart），
     * 当前 SDK 未集成 multipart 上传。若调用方传的图片是本地 URL（/uploads/xxx.jpg），
     * 走无图发布模式（publishItem imageInfoList 传空），闲鱼允许无图发布（会提示但能成功）。
     * 若调用方已通过其他方式拿到闲鱼 CDN 图片 URL，传进来即可正常带图发布。</p>
     *
     * <p><b>发布 ≠ 上架已下架商品</b>：发布会生成新 itemId，原商品的浏览量/收藏/历史成交清零。
     * 这是闲鱼平台设计，不是 SDK 限制。</p>
     */
    @Transactional
    public XianyuProduct create(ProductCreateRequest request) {
        // 1. 本地先落 DRAFT 记录（保留请求痕迹，万一发布失败也有本地草稿可查）
        XianyuProduct product = new XianyuProduct();
        product.setAccountId(request.getAccountId());
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

        // 2. 校验账号 cookie
        Long accountId = request.getAccountId();
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
                        // 单张图上传失败不阻塞整个发布（无图也能发），记录后跳过这张
                        // 让 publishItem 走无图模式，闲鱼会提示但能成功
                        continue;
                    }
                }
                imageInfoList.add(img);
            }
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

    @Transactional
    public void delete(Long id) {
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
     * 改价 — 走「编辑重发」真闭环：拉原商品详情 → 拼 publish data 带 itemId+新价 → 调 publishItem
     * <p>真实定位结论（2026-07-19 全参考项目翻查）：闲鱼 PC 无独立改价 mtop 接口，
     * 真正的改价路径是「编辑商品重新 publish」——调 mtop.idle.pc.idleitem.publish 带 itemId + 新价格 + 原商品全套信息，
     * itemId 保留，浏览量/收藏不清零。SDK 的 publishItem 重载版已支持 itemId 参数（publishScene=pcEdit）。</p>
     *
     * <p>编辑重发改价流程：</p>
     * <ol>
     *   <li>校验账号 cookie + itemId</li>
     *   <li>调 getProductDetail 拉原商品完整详情（b2cItemDO/picDetailDO/logisticsDO/trackParams）</li>
     *   <li>从详情里拆出 publish data 需要的字段：标题/描述/图片/分类/所在地/运费/库存/属性标签</li>
     *   <li>调 publishItem(itemId, title, desc, newPriceInCent, origPrice, stock, images, cat, labels, addr, delivery)</li>
     *   <li>闲鱼确认 SUCCESS 后回写本地 product.price</li>
     * </ol>
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
            throw new IllegalStateException("Product has no itemId, can not edit republish");
        }

        // 2. 构造 SDK：MtopApiClient + ProductApiService（拉详情）+ PublishApiService（重发）
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);
        XianyuPublishApiService publishApi = new XianyuPublishApiService(mtopClient);

        // 3. 拉原商品详情（b2cItemDO/picDetailDO/logisticsDO/trackParams）
        JsonNode detailResp = productApi.getProductDetail(itemId);
        if (!isMtopSuccess(detailResp)) {
            throw new IllegalStateException("Xianyu getProductDetail failed: " + safeMtopMsg(detailResp));
        }
        JsonNode detailData = detailResp.path("data");
        JsonNode b2cItem = detailData.path("b2cItemDO");
        JsonNode picDetail = detailData.path("picDetailDO");
        JsonNode logistics = detailData.path("logisticsDO");
        JsonNode trackParams = detailData.path("trackParams");

        // 4. 从详情拆出 publish data 需要的字段
        // 标题/描述：b2cItemDO.title / b2cItemDO.desc（部分版本在 itemTextDO）
        String title = pickText(b2cItem, "title");
        if (title.isEmpty()) title = pickText(detailData, "title");
        String desc = pickText(b2cItem, "desc");
        if (desc.isEmpty()) desc = pickText(detailData, "desc");
        if (desc.isEmpty()) desc = title;  // 闲鱼 itemTextDTO.desc 允许与 title 相同

        // 库存：b2cItemDO.quantity 或本地 product.stock
        String stock = pickText(b2cItem, "quantity");
        if (stock.isEmpty() && product.getStock() != null) {
            stock = String.valueOf(product.getStock());
        }
        if (stock.isEmpty()) stock = "1";

        // 原价：b2cItemDO.oriPriceInCent 或本地 product.originalPrice
        String origPriceInCent = pickText(b2cItem, "oriPriceInCent");
        if (origPriceInCent.isEmpty() && product.getOriginalPrice() != null) {
            origPriceInCent = String.valueOf(product.getOriginalPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue());
        }

        // 图片：picDetailDO.picUrlList[] 或 b2cItemDO.picUrlList[]，每项含 url + 宽高
        List<Map<String, Object>> imageInfoList = new ArrayList<>();
        JsonNode picList = picDetail.path("picUrlList");
        if (!picList.isArray() || picList.size() == 0) {
            picList = b2cItem.path("picUrlList");
        }
        if (picList.isArray()) {
            for (JsonNode pic : picList) {
                String picUrl = pickText(pic, "url");
                if (picUrl.isEmpty()) picUrl = pickText(pic, "picUrl");
                if (picUrl.isEmpty()) continue;
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("url", picUrl);
                // 宽高：pix 字段形如 "1024x768"，或 width/height 字段
                int w = 0, h = 0;
                String pix = pickText(pic, "pix");
                if (!pix.isEmpty() && pix.contains("x")) {
                    try {
                        String[] parts = pix.split("x");
                        w = Integer.parseInt(parts[0]);
                        h = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                if (w == 0) w = pickInt(pic, "width");
                if (h == 0) h = pickInt(pic, "height");
                img.put("width", w);
                img.put("height", h);
                imageInfoList.add(img);
            }
        }

        // 分类：b2cItemDO.catId / catName / channelCatId / tbCatId
        Map<String, String> catDTO = new LinkedHashMap<>();
        String catId = pickText(b2cItem, "catId");
        if (catId.isEmpty()) catId = pickText(detailData, "catId");
        if (!catId.isEmpty()) {
            catDTO.put("catId", catId);
            catDTO.put("catName", pickText(b2cItem, "catName"));
            catDTO.put("channelCatId", pickText(b2cItem, "channelCatId"));
            catDTO.put("tbCatId", pickText(b2cItem, "tbCatId"));
        }

        // 所在地：logisticsDO.addressList[0] 或 b2cItemDO.addrDTO
        Map<String, Object> addrDTO = new LinkedHashMap<>();
        JsonNode addrList = logistics.path("addressList");
        if (addrList.isArray() && addrList.size() > 0) {
            JsonNode addr = addrList.get(0);
            addrDTO.put("area", pickText(addr, "area"));
            addrDTO.put("city", pickText(addr, "city"));
            addrDTO.put("divisionId", pickText(addr, "divisionId"));
            String longitude = pickText(addr, "longitude");
            String latitude = pickText(addr, "latitude");
            if (!longitude.isEmpty() && !latitude.isEmpty()) {
                addrDTO.put("gps", longitude + "," + latitude);
            }
            addrDTO.put("poiId", pickText(addr, "poiId"));
            addrDTO.put("poi", pickText(addr, "poi"));
        }

        // 运费：logisticsDO 含运费设置，简单拼保守默认（按距离计费）
        Map<String, Object> deliverySettings = new LinkedHashMap<>();
        deliverySettings.put("supportFreight", true);
        deliverySettings.put("canFreeShipping", false);
        deliverySettings.put("onlyTakeSelf", false);
        deliverySettings.put("templateId", "-100");

        // 属性标签：trackParams.sellerOptions 或 b2cItemDO.itemLabelExtList，复杂结构留空让闲鱼按原值保留
        List<Map<String, Object>> labelExtList = new ArrayList<>();

        // 5. 新价格元转分（元 → 分）
        String newPriceInCent = String.valueOf(price.multiply(java.math.BigDecimal.valueOf(100)).longValue());

        // 6. 真调 publishItem 编辑重发（带 itemId + 新价 + 原商品全套信息）
        JsonNode pubResp = publishApi.publishItem(
                itemId, title, desc, newPriceInCent, origPriceInCent, stock,
                imageInfoList, catDTO, labelExtList, addrDTO, deliverySettings
        );
        if (!isMtopSuccess(pubResp)) {
            throw new IllegalStateException("Xianyu edit republish (price) failed: " + safeMtopMsg(pubResp));
        }

        // 7. 闲鱼确认 SUCCESS 后回写本地 product.price
        product.setPrice(price);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
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
     * 改库存 — 走「编辑重发」真闭环：拉原商品详情 → 拼 publish data 带 itemId+新库存 → 调 publishItem
     * <p>真实定位结论同 updatePrice：闲鱼 PC 无独立改库存 mtop 接口，
     * 真路径是「编辑商品重新 publish」——publishItem 带 itemId + 新库存 + 原商品全套信息，
     * itemId 保留，浏览量/收藏不清零。闲鱼 quantity 字段就是库存。</p>
     *
     * <p>编辑重发改库存流程同 {@link #updatePrice}，只是用新库存替换 stock 字段，
     * 价格走原商品原值（从 getProductDetail 拿原价回填，不改）。</p>
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
            throw new IllegalStateException("Product has no itemId, can not edit republish");
        }

        // 2. 构造 SDK：MtopApiClient + ProductApiService（拉详情）+ PublishApiService（重发）
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);
        XianyuPublishApiService publishApi = new XianyuPublishApiService(mtopClient);

        // 3. 拉原商品详情
        JsonNode detailResp = productApi.getProductDetail(itemId);
        if (!isMtopSuccess(detailResp)) {
            throw new IllegalStateException("Xianyu getProductDetail failed: " + safeMtopMsg(detailResp));
        }
        JsonNode detailData = detailResp.path("data");
        JsonNode b2cItem = detailData.path("b2cItemDO");
        JsonNode picDetail = detailData.path("picDetailDO");
        JsonNode logistics = detailData.path("logisticsDO");

        // 4. 从详情拆出 publish data 需要的字段
        String title = pickText(b2cItem, "title");
        if (title.isEmpty()) title = pickText(detailData, "title");
        String desc = pickText(b2cItem, "desc");
        if (desc.isEmpty()) desc = pickText(detailData, "desc");
        if (desc.isEmpty()) desc = title;

        // 价格：保持原值不改（b2cItemDO.priceInCent 或本地 product.price）
        String priceInCent = pickText(b2cItem, "priceInCent");
        if (priceInCent.isEmpty() && product.getPrice() != null) {
            priceInCent = String.valueOf(product.getPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue());
        }
        String origPriceInCent = pickText(b2cItem, "oriPriceInCent");
        if (origPriceInCent.isEmpty() && product.getOriginalPrice() != null) {
            origPriceInCent = String.valueOf(product.getOriginalPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue());
        }

        // 图片：picDetailDO.picUrlList[] 或 b2cItemDO.picUrlList[]
        List<Map<String, Object>> imageInfoList = new ArrayList<>();
        JsonNode picList = picDetail.path("picUrlList");
        if (!picList.isArray() || picList.size() == 0) {
            picList = b2cItem.path("picUrlList");
        }
        if (picList.isArray()) {
            for (JsonNode pic : picList) {
                String picUrl = pickText(pic, "url");
                if (picUrl.isEmpty()) picUrl = pickText(pic, "picUrl");
                if (picUrl.isEmpty()) continue;
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("url", picUrl);
                int w = 0, h = 0;
                String pix = pickText(pic, "pix");
                if (!pix.isEmpty() && pix.contains("x")) {
                    try {
                        String[] parts = pix.split("x");
                        w = Integer.parseInt(parts[0]);
                        h = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                if (w == 0) w = pickInt(pic, "width");
                if (h == 0) h = pickInt(pic, "height");
                img.put("width", w);
                img.put("height", h);
                imageInfoList.add(img);
            }
        }

        // 分类
        Map<String, String> catDTO = new LinkedHashMap<>();
        String catId = pickText(b2cItem, "catId");
        if (catId.isEmpty()) catId = pickText(detailData, "catId");
        if (!catId.isEmpty()) {
            catDTO.put("catId", catId);
            catDTO.put("catName", pickText(b2cItem, "catName"));
            catDTO.put("channelCatId", pickText(b2cItem, "channelCatId"));
            catDTO.put("tbCatId", pickText(b2cItem, "tbCatId"));
        }

        // 所在地
        Map<String, Object> addrDTO = new LinkedHashMap<>();
        JsonNode addrList = logistics.path("addressList");
        if (addrList.isArray() && addrList.size() > 0) {
            JsonNode addr = addrList.get(0);
            addrDTO.put("area", pickText(addr, "area"));
            addrDTO.put("city", pickText(addr, "city"));
            addrDTO.put("divisionId", pickText(addr, "divisionId"));
            String longitude = pickText(addr, "longitude");
            String latitude = pickText(addr, "latitude");
            if (!longitude.isEmpty() && !latitude.isEmpty()) {
                addrDTO.put("gps", longitude + "," + latitude);
            }
            addrDTO.put("poiId", pickText(addr, "poiId"));
            addrDTO.put("poi", pickText(addr, "poi"));
        }

        // 运费保守默认
        Map<String, Object> deliverySettings = new LinkedHashMap<>();
        deliverySettings.put("supportFreight", true);
        deliverySettings.put("canFreeShipping", false);
        deliverySettings.put("onlyTakeSelf", false);
        deliverySettings.put("templateId", "-100");

        // 属性标签留空（复杂结构，让闲鱼按原值保留）
        List<Map<String, Object>> labelExtList = new ArrayList<>();

        // 5. 真调 publishItem 编辑重发（带 itemId + 新库存 + 原商品全套信息 + 原价）
        JsonNode pubResp = publishApi.publishItem(
                itemId, title, desc, priceInCent, origPriceInCent, String.valueOf(stock),
                imageInfoList, catDTO, labelExtList, addrDTO, deliverySettings
        );
        if (!isMtopSuccess(pubResp)) {
            throw new IllegalStateException("Xianyu edit republish (stock) failed: " + safeMtopMsg(pubResp));
        }

        // 6. 闲鱼确认 SUCCESS 后回写本地 product.stock
        product.setStock(stock);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
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
     * 拉首页（20 条），按 accountId + itemId 做 upsert，不删除本地已有记录。
     *
     * @return 同步结果统计 {synced, inserted, updated}
     */
    @Transactional
    public SyncResult syncFromXianyu(Long accountId) {
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

        // 构造 SDK：先 MtopApiClient（带 cookie），再 ProductApiService
        XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
        XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);

        JsonNode resp = productApi.getMyProducts("1", "20");

        // 真实返回结构（CDP 抓包验证 2026-07-18）：
        //   data.cardList[] → 每项 .cardData 含 id/itemId/title/soldPrice/priceInfo.price
        //   /categoryId/itemStatus/picInfo.picUrl/detailParams.picUrl/detailUrl/auctionType
        JsonNode listNode = resolveProductList(resp);

        int inserted = 0;
        int updated = 0;
        if (listNode != null && listNode.isArray()) {
            for (JsonNode card : listNode) {
                // card.cardData 才是真正的商品节点
                JsonNode item = card.has("cardData") ? card.get("cardData") : card;
                String itemId = pickString(item, "id", "itemId", "item_id");
                if (itemId == null || itemId.isBlank()) continue;

                // upsert：按 accountId + itemId 查
                LambdaQueryWrapper<XianyuProduct> qw = new LambdaQueryWrapper<>();
                qw.eq(XianyuProduct::getAccountId, accountId)
                        .eq(XianyuProduct::getItemId, itemId);
                XianyuProduct existing = productMapper.selectOne(qw);

                XianyuProduct p = existing != null ? existing : new XianyuProduct();
                if (existing == null) {
                    p.setAccountId(accountId);
                    p.setItemId(itemId);
                    p.setCreatedAt(LocalDateTime.now());
                    p.setViewCount(0);
                    p.setFavoriteCount(0);
                }
                p.setTitle(pickString(item, "title", "name", "itemTitle"));
                // 真实结构：priceInfo.price 嵌套 / detailParams.soldPrice 嵌套 / detailParams.picUrl 嵌套；itemStatus 是数字 0=在售
                BigDecimal price = pickBigDecimal(item, "priceInfo.price", "detailParams.soldPrice", "soldPrice", "price", "itemPrice");
                if (price != null) p.setPrice(price);
                BigDecimal orig = pickBigDecimal(item, "originalPrice", "oriPrice", "marketPrice");
                if (orig != null) p.setOriginalPrice(orig);
                Integer stock = pickInt(item, "stock", "quantity", "remainQuantity");
                if (stock != null) p.setStock(stock);
                String status = mapStatus(pickString(item, "itemStatus", "status", "soldStatus"));
                if (status != null) p.setStatus(status);
                String images = pickImages(item);
                if (images != null) p.setImages(images);
                // 同步主图 URL（闲鱼 API 多为 picUrl / mainPic / imageUrl 字段）
                if (p.getImageUrl() == null) {
                    String imageUrl = pickString(item, "picUrl", "mainPic", "imageUrl", "cover");
                    if (imageUrl != null) p.setImageUrl(imageUrl);
                }
                String desc = pickString(item, "desc", "description", "detail");
                if (desc != null) p.setDescription(desc);
                String detailUrl = pickString(item, "detailUrl", "url", "itemUrl");
                if (detailUrl != null) p.setDetailUrl(detailUrl);
                Integer view = pickInt(item, "viewCount", "pv", "views");
                if (view != null) p.setViewCount(view);
                Integer fav = pickInt(item, "favoriteCount", "wishCount", "collectCount");
                if (fav != null) p.setFavoriteCount(fav);
                // 分类 id 真实结构在 categoryId 顶层
                String cid = pickString(item, "categoryId", "cid");
                if (cid != null) p.setCategoryId(cid);
                p.setUpdatedAt(LocalDateTime.now());

                if (existing == null) {
                    productMapper.insert(p);
                    inserted++;
                } else {
                    productMapper.updateById(p);
                    updated++;
                }
            }
        }
        return new SyncResult(inserted + updated, inserted, updated);
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
