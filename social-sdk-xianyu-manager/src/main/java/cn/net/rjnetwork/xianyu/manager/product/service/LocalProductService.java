package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.*;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductBatchPublishRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.LocalProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.LocalProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 本地商品服务：自建商品（未上架闲鱼的草稿/待发布池）。
 * 发布成功后按业务要求物理删除本地记录。
 */
@Service
public class LocalProductService {

    private static final Logger log = LoggerFactory.getLogger(LocalProductService.class);

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PUBLISHING = "PUBLISHING";
    private static final String STATUS_FAILED = "FAILED";

    private final LocalProductMapper localProductMapper;
    private final ProductService productService;
    private final AccountService accountService;

    public LocalProductService(LocalProductMapper localProductMapper,
                               ProductService productService,
                               AccountService accountService) {
        this.localProductMapper = localProductMapper;
        this.productService = productService;
        this.accountService = accountService;
    }

    /**
     * 分页查询本地商品（默认按更新时间倒序）。
     */
    public Page<LocalProduct> listPage(int pageNum, int pageSize, Long accountId, String keyword, String status) {
        Page<LocalProduct> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<LocalProduct> qw = new LambdaQueryWrapper<>();
        if (accountId != null) qw.eq(LocalProduct::getAccountId, accountId);
        if (keyword != null && !keyword.isBlank()) qw.like(LocalProduct::getTitle, keyword);
        if (status != null && !status.isBlank()) qw.eq(LocalProduct::getStatus, status);
        qw.orderByDesc(LocalProduct::getUpdatedAt);
        return localProductMapper.selectPage(page, qw);
    }

    public LocalProduct getById(Long id) {
        return localProductMapper.selectById(id);
    }

    /**
     * 保存本地商品草稿（不会真发布到闲鱼）。
     */
    @Transactional
    public LocalProduct saveDraft(LocalProductRequest req) {
        LocalProduct p = new LocalProduct();
        applyRequest(p, req);
        p.setStatus((req.getAction() != null && req.getAction().equalsIgnoreCase("SUBMIT")) ? STATUS_PENDING : STATUS_DRAFT);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        localProductMapper.insert(p);
        return p;
    }

    /**
     * 更新本地商品（仅草稿/失败状态可编辑，发布中不允许改）。
     */
    @Transactional
    public LocalProduct update(LocalProductRequest req) {
        if (req.getId() == null) throw new IllegalArgumentException("更新时 id 不能为空");
        LocalProduct p = localProductMapper.selectById(req.getId());
        if (p == null) throw new IllegalArgumentException("本地商品不存在");
        if (STATUS_PUBLISHING.equals(p.getStatus())) {
            throw new IllegalStateException("商品正在发布中，暂时不能修改");
        }
        applyRequest(p, req);
        p.setUpdatedAt(LocalDateTime.now());
        localProductMapper.updateById(p);
        return p;
    }

    /**
     * 物理删除本地商品。
     */
    public void delete(Long id) {
        localProductMapper.deleteById(id);
    }

    /**
     * 单条发布：立即真调闲鱼 publishItem，成功后删除本地记录。
     */
    @Transactional
    public LocalProduct publishOne(Long id) {
        LocalProduct p = localProductMapper.selectById(id);
        if (p == null) throw new IllegalArgumentException("本地商品不存在");
        return doPublish(p);
    }

    /**
     * 批量发布：并发调闲鱼发布。成功的从本地表物理删除；失败的保留并写入 publishError。
     */
    public BatchPublishResult batchPublish(LocalProductBatchPublishRequest req) {
        List<LocalProduct> items;
        if (req.getIds() != null && !req.getIds().isEmpty()) {
            items = localProductMapper.selectBatchIds(req.getIds());
        } else {
            items = localProductMapper.selectList(new LambdaQueryWrapper<LocalProduct>()
                    .eq(LocalProduct::getStatus, STATUS_DRAFT));
        }

        if (items.isEmpty()) {
            return BatchPublishResult.empty();
        }

        int concurrency = Math.max(1, Math.min(req.getMaxConcurrency(), 8));
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        AtomicInteger skip = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        try {
            for (LocalProduct item : items) {
                if (STATUS_PUBLISHING.equals(item.getStatus())) {
                    skip.incrementAndGet();
                    continue;
                }
                // 状态置为发布中（防重入）
                item.setStatus(STATUS_PUBLISHING);
                localProductMapper.updateById(item);

                CompletableFuture<Void> future = CompletableFuture
                        .runAsync(() -> {
                            try {
                                if (req.getDelayMs() > 0) Thread.sleep(req.getDelayMs());
                                doPublish(item);
                                success.incrementAndGet();
                            } catch (Exception e) {
                                fail.incrementAndGet();
                                String err = "商品#" + item.getId() + " 发布失败: " + e.getMessage();
                                log.error("[LOCAL-PUBLISH] {}", err, e);
                                errors.add(err);
                                // 失败保留本地，写错误原因
                                LocalProduct persist = localProductMapper.selectById(item.getId());
                                if (persist != null) {
                                    persist.setStatus(STATUS_FAILED);
                                    persist.setPublishError(e.getMessage());
                                    persist.setUpdatedAt(LocalDateTime.now());
                                    localProductMapper.updateById(persist);
                                }
                            }
                        }, pool);
                if (!req.isPartialSuccess()) {
                    // 有任一失败即整体失败：全部回滚（把发布中的置回草稿）
                    future = future.handle((v, ex) -> {
                        if (ex != null) rollbackPublishing(items);
                        return v;
                    });
                }
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            pool.shutdown();
        }
        return new BatchPublishResult(success.get(), fail.get(), skip.get(), errors);
    }

    private void rollbackPublishing(List<LocalProduct> items) {
        for (LocalProduct item : items) {
            if (STATUS_PUBLISHING.equals(item.getStatus())) {
                item.setStatus(STATUS_DRAFT);
                item.setUpdatedAt(LocalDateTime.now());
                localProductMapper.updateById(item);
            }
        }
    }

    /**
     * 真正发布：走 ProductService.create 的发布链路，成功后删除本地记录。
     */
    private synchronized LocalProduct doPublish(LocalProduct item) {
        if (item.getAccountId() == null) {
            throw new IllegalArgumentException("请选择发布账号");
        }
        XianyuAccount account = accountService.getById(item.getAccountId());
        if (account == null || account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("账号未登录或 Cookie 已过期");
        }

        // 用 ProductService 的发布能力
        cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest createReq =
                new cn.net.rjnetwork.xianyu.manager.product.dto.ProductCreateRequest();
        createReq.setAccountId(item.getAccountId());
        createReq.setTitle(item.getTitle());
        createReq.setPrice(item.getPrice());
        createReq.setOriginalPrice(item.getOriginalPrice());
        createReq.setStock(item.getStock());
        createReq.setCategoryId(item.getCategoryId());
        createReq.setDescription(item.getDescription());
        createReq.setImages(parseJsonArray(item.getImages()));
        createReq.setVideos(parseJsonArray(item.getVideos()));
        productService.create(createReq);

        // 发布成功 → 物理删除本地记录
        localProductMapper.deleteById(item.getId());
        log.info("[LOCAL-PUBLISH] 发布成功并清理本地商品: id={}", item.getId());
        return item;
    }

    private void applyRequest(LocalProduct p, LocalProductRequest r) {
        p.setAccountId(r.getAccountId());
        p.setTitle(r.getTitle());
        p.setPrice(r.getPrice());
        p.setOriginalPrice(r.getOriginalPrice());
        p.setStock(r.getStock());
        p.setCategoryId(r.getCategoryId());
        p.setDescription(r.getDescription());
        if (r.getImages() != null) p.setImages(toJsonArray(r.getImages()));
        if (r.getVideos() != null) p.setVideos(toJsonArray(r.getVideos()));
        p.setGoodsType(r.getGoodsType());
        p.setDeliverType(r.getDeliverType());
        p.setDeliverContentTemplate(r.getDeliverContentTemplate());
        if (r.getImages() != null && !r.getImages().isEmpty()) {
            p.setImageUrl(r.getImages().get(0));
        }
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i).replace("\"", "\\\"");
            sb.append("\"").append(s).append("\"");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        return Arrays.stream(json.replace("[", "").replace("]", "").replace("\"", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 批量发布结果。 */
    public static class BatchPublishResult {
        public final int success;
        public final int fail;
        public final int skip;
        public final List<String> errors;

        public BatchPublishResult(int success, int fail, int skip, List<String> errors) {
            this.success = success;
            this.fail = fail;
            this.skip = skip;
            this.errors = errors;
        }

        static BatchPublishResult empty() {
            return new BatchPublishResult(0, 0, 0, Collections.emptyList());
        }
    }
}
