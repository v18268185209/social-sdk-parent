^package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuProductApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductSyncService {

    private final AccountMapper accountMapper;
    private final ProductMapper productMapper;

    public ProductSyncService(AccountMapper accountMapper, ProductMapper productMapper) {
        this.accountMapper = accountMapper;
        this.productMapper = productMapper;
    }

    public SyncResult sync(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) return SyncResult.error("账号不存在");
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank())
            return SyncResult.error("Cookie 未设置");

        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuProductApiService productApi = new XianyuProductApiService(mtopClient);

            JsonNode data = productApi.getMyProducts(1, 100);
            List<XianyuProduct> products = parseProducts(data, accountId);

            int upserted = 0;
            for (XianyuProduct p : products) {
                XianyuProduct existing = findByAccountIdAndItemId(accountId, p.getItemId());
                if (existing != null) {
                    existing.setTitle(p.getTitle());
                    existing.setPrice(p.getPrice());
                    existing.setImageUrl(p.getImageUrl());
                    existing.setViewCount(p.getViewCount());
                    existing.setFavoriteCount(p.getFavoriteCount());
                    existing.setStatus(p.getStatus());
                    existing.setUpdatedAt(LocalDateTime.now());
                    productMapper.updateById(existing);
                } else {
                    p.setCreatedAt(LocalDateTime.now());
                    p.setUpdatedAt(LocalDateTime.now());
                    productMapper.insert(p);
                }
                upserted++;
            }
            return SyncResult.ok(upserted);
        } catch (Exception e) {
            return SyncResult.error("同步失败: " + e.getMessage());
        }
    }

    private List<XianyuProduct> parseProducts(JsonNode data, Long accountId) {
        List<XianyuProduct> list = new ArrayList<>();
        if (data == null || !data.has("data")) return list;
        JsonNode items = data.path("data").path("items");
        if (!items.isArray()) return list;

        for (JsonNode item : items) {
            XianyuProduct p = new XianyuProduct();
            p.setAccountId(accountId);
            p.setItemId(item.path("itemId").asText());
            p.setTitle(item.path("title").asText());
            p.setStatus("ON_SALE");
            String priceStr = item.path("price").asText();
            if (priceStr != null && !priceStr.isEmpty()) {
                try { p.setPrice(new java.math.BigDecimal(priceStr)); } catch (Exception ignored) {}
            }
            p.setImageUrl(item.path("imageUrl").asText());
            p.setViewCount(item.path("viewCount").asInt(0));
            p.setFavoriteCount(item.path("favoriteCount").asInt(0));
            list.add(p);
        }
        return list;
    }

    private XianyuProduct findByAccountIdAndItemId(Long accountId, String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        return productMapper.selectOne(new LambdaQueryWrapper<XianyuProduct>()
                .eq(XianyuProduct::getAccountId, accountId)
                .eq(XianyuProduct::getItemId, itemId)
                .last("LIMIT 1"));
    }

    public static class SyncResult {
        public boolean success;
        public String message;
        public int count;

        public static SyncResult ok(int count) {
            SyncResult r = new SyncResult();
            r.success = true;
            r.count = count;
            return r;
        }

        public static SyncResult error(String msg) {
            SyncResult r = new SyncResult();
            r.success = false;
            r.message = msg;
            return r;
        }
    }
}
