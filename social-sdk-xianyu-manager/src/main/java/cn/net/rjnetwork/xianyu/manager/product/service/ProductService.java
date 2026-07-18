package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProductApiService;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.ProductUpdateRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final AccountService accountService;

    public ProductService(ProductMapper productMapper, AccountService accountService) {
        this.productMapper = productMapper;
        this.accountService = accountService;
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

    @Transactional
    public XianyuProduct create(ProductCreateRequest request) {
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
        return product;
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

    @Transactional
    public XianyuProduct shelfOn(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setStatus("ON_SALE");
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public XianyuProduct shelfOff(Long id) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setStatus("OFF_SALE");
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public XianyuProduct updatePrice(Long id, java.math.BigDecimal price) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
        product.setPrice(price);
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    @Transactional
    public XianyuProduct updateStock(Long id, Integer stock) {
        XianyuProduct product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("Product not found");
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
