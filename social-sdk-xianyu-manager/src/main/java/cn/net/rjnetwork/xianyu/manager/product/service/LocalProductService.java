package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.*;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.account.service.AccountService;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductBatchPublishRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportPreview;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportRequest;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductImportResult;
import cn.net.rjnetwork.xianyu.manager.product.dto.LocalProductRequest;
import cn.net.rjnetwork.xianyu.manager.product.mapper.LocalProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.LocalProduct;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.virtual.service.VirtualShipService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
    private final VirtualShipService virtualShipService;

    public LocalProductService(LocalProductMapper localProductMapper,
                               ProductService productService,
                               AccountService accountService,
                               VirtualShipService virtualShipService) {
        this.localProductMapper = localProductMapper;
        this.productService = productService;
        this.accountService = accountService;
        this.virtualShipService = virtualShipService;
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
        AtomicInteger retried = new AtomicInteger(0);
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
                                // 带重试的发布
                                publishWithRetry(item, req, retried);
                                success.incrementAndGet();
                            } catch (Exception e) {
                                fail.incrementAndGet();
                                String errMsg = "商品#" + item.getId() + " 发布失败: " + e.getMessage();
                                log.error("[LOCAL-PUBLISH] {} (已重试 {} 次)", errMsg, req.getRetryTimes(), e);
                                errors.add(errMsg);
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
        return new BatchPublishResult(success.get(), fail.get(), skip.get(), retried.get(), errors);
    }

    /**
     * 发布（带重试）
     * 每次重试走指数退避：第 n 次重试等待 retryBackoffBaseMs * 2^(n-1)
     */
    private void publishWithRetry(LocalProduct item, LocalProductBatchPublishRequest req,
                                  AtomicInteger retriedCounter) {
        int maxAttempts = 1 + Math.max(0, Math.min(req.getRetryTimes(), 3));
        Exception lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                doPublish(item);
                if (attempt > 1) {
                    log.info("[LOCAL-PUBLISH] 商品#{} 第 {} 次重试后发布成功", item.getId(), attempt - 1);
                    retriedCounter.incrementAndGet();
                }
                return;
            } catch (Exception e) {
                lastEx = e;
                log.warn("[LOCAL-PUBLISH] 商品#{} 第 {}/{} 次发布失败: {}", item.getId(), attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    long backoff = req.getRetryBackoffBaseMs() * (1L << (attempt - 1));
                    log.info("[LOCAL-PUBLISH] 商品#{} 等待 {}ms 后重试", item.getId(), backoff);
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw lastEx != null ? new RuntimeException(lastEx) : new RuntimeException("未知发布错误");
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
     * 虚拟商品发布成功后自动建卡密池/账号池（库存联动）。
     */
    private synchronized LocalProduct doPublish(LocalProduct item) {
        if (item.getAccountId() == null) {
            throw new IllegalArgumentException("请选择发布账号");
        }
        XianyuAccount account = accountService.getById(item.getAccountId());
        if (account == null || account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            throw new IllegalStateException("账号未登录或 Cookie 已过期");
        }

        // 虚拟商品：发货前校验库存充足（CARD / ACCOUNT 统一校验）
        if ("VIRTUAL".equals(item.getGoodsType())
                && ("CARD".equals(item.getDeliverType()) || "ACCOUNT".equals(item.getDeliverType()))) {
            List<String> items = parseJsonArray(item.getDeliverContentTemplate());
            if (items.size() < item.getStock()) {
                throw new IllegalStateException(
                    String.format("发货内容数量不足：库存 %d，实际 %d", item.getStock(), items.size()));
            }
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
        XianyuProduct published = productService.create(createReq);

        // 虚拟商品：发布成功后自动建卡密池/账号池（库存联动）
        if ("VIRTUAL".equals(item.getGoodsType())
                && ("CARD".equals(item.getDeliverType()) || "ACCOUNT".equals(item.getDeliverType()))) {
            try {
                List<String> rawItems = parseJsonArray(item.getDeliverContentTemplate());
                int imported = virtualShipService.importCards(published.getId(), rawItems);
                log.info("[LOCAL-PUBLISH] 虚拟商品建池: productId={}, type={}, count={}",
                        published.getId(), item.getDeliverType(), imported);
            } catch (Exception e) {
                log.warn("[LOCAL-PUBLISH] 建池失败（商品已发布）: {}", e.getMessage());
            }
        }

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

    // ==================== 批量导入 ====================

    /**
     * CSV 列常量
     */
    private static final String COL_ACCOUNT_NAME = "account_name";
    private static final String COL_TITLE = "title";
    private static final String COL_PRICE = "price";
    private static final String COL_STOCK = "stock";
    private static final String COL_IMAGES = "images";
    private static final String COL_GOODS_TYPE = "goods_type";
    private static final String COL_DELIVER_TYPE = "deliver_type";
    private static final String COL_DELIVER_CONTENT = "deliver_content_template";
    private static final String COL_DESCRIPTION = "description";

    /**
     * 预览导入（解析 CSV，返回前 10 条预览 + 错误明细，不写入 DB）
     */
    public LocalProductImportPreview previewImport(LocalProductImportRequest request) throws IOException {
        List<String[]> rows = parseCsv(request.getFile());
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV 文件为空");

        String[] header = rows.get(0);
        Map<String, Integer> colIdx = mapColumns(header);

        List<Map<String, Object>> previewItems = new ArrayList<>();
        List<LocalProductImportPreview.ImportError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();

        int validRows = 0;
        int duplicateCount = 0;

        for (int i = 1; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            int rowNum = i + 1;
            if (isEmptyRow(cols)) continue;

            Map<String, Object> row = new HashMap<>();
            List<LocalProductImportPreview.ImportError> rowErrors = new ArrayList<>();

            // 必填校验
            String title = getCol(cols, colIdx, COL_TITLE);
            if (title.isEmpty()) rowErrors.add(err(rowNum, COL_TITLE, "标题必填"));
            String priceStr = getCol(cols, colIdx, COL_PRICE);
            if (priceStr.isEmpty()) rowErrors.add(err(rowNum, COL_PRICE, "价格必填"));
            else try { Double.parseDouble(priceStr); }
            catch (NumberFormatException e) { rowErrors.add(err(rowNum, COL_PRICE, "价格格式无效")); }
            String accountName = getCol(cols, colIdx, COL_ACCOUNT_NAME);
            if (accountName.isEmpty()) rowErrors.add(err(rowNum, COL_ACCOUNT_NAME, "发布账号必填"));
            else {
                XianyuAccount acc = accountService.findByName(accountName).orElse(null);
                if (acc == null) rowErrors.add(err(rowNum, COL_ACCOUNT_NAME, "账号不存在: " + accountName));
                else row.put("accountId", acc.getId());
            }

            // 可选字段
            String stockStr = getCol(cols, colIdx, COL_STOCK);
            row.put("stock", stockStr.isEmpty() ? 1 : parseIntOr(stockStr, 1, rowErrors, rowNum, COL_STOCK));
            row.put("title", title);
            row.put("price", priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr));
            row.put("originalPrice", 0.0);
            row.put("description", getCol(cols, colIdx, COL_DESCRIPTION));
            row.put("images", parseImagesFromRaw(getCol(cols, colIdx, COL_IMAGES)));
            row.put("goodsType", getCol(cols, colIdx, COL_GOODS_TYPE).isEmpty()
                    ? request.getDefaultGoodsType() : getCol(cols, colIdx, COL_GOODS_TYPE));
            row.put("deliverType", getCol(cols, colIdx, COL_DELIVER_TYPE));
            row.put("deliverContentTemplate", getCol(cols, colIdx, COL_DELIVER_CONTENT));

            // 实物/虚拟字段校验
            String goodsType = (String) row.get("goodsType");
            if ("VIRTUAL".equals(goodsType) && row.get("deliverType").toString().isEmpty()) {
                rowErrors.add(err(rowNum, COL_DELIVER_TYPE, "虚拟商品必须指定发货类型"));
            }
            if ("VIRTUAL".equals(goodsType) && row.get("deliverContentTemplate").toString().isEmpty()) {
                rowErrors.add(err(rowNum, COL_DELIVER_CONTENT, "虚拟商品必须指定发货内容"));
            }

            // 图片 Base64 校验 + 预存
            List<String> images = (List<String>) row.get("images");
            List<String> convertedImages = new ArrayList<>();
            for (String img : images) {
                if (img.startsWith("data:image")) {
                    try {
                        String ext = img.substring(img.indexOf("/") + 1, img.indexOf(";"));
                        String base64 = img.substring(img.indexOf(",") + 1);
                        byte[] data = Base64.getDecoder().decode(base64);
                        Path dir = Paths.get(request.getImageStoragePath());
                        Files.createDirectories(dir);
                        String fname = "imp_" + System.nanoTime() + "." + ext;
                        Files.write(dir.resolve(fname), data);
                        convertedImages.add("/uploads/local-products/" + fname);
                    } catch (Exception e) {
                        rowErrors.add(err(rowNum, COL_IMAGES, "图片 Base64 处理失败: " + e.getMessage()));
                    }
                } else {
                    convertedImages.add(img);
                }
            }
            row.put("images", convertedImages);

            // 虚拟发货内容：分隔符解析
            if ("VIRTUAL".equals(goodsType)) {
                String rawContent = row.get("deliverContentTemplate").toString();
                if (!rawContent.isEmpty()) {
                    List<String> cards = Arrays.stream(rawContent.split(request.getDeliverContentSeparator()))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    row.put("deliverContentTemplate", toJsonArray(cards));
                }
            }

            if (rowErrors.isEmpty()) {
                // 去重
                if (request.isDeduplicate() && !seenTitles.add(title)) {
                    duplicateCount++;
                    if (!request.isOverwriteDuplicate()) continue;
                }
                validRows++;
                if (previewItems.size() < 10) {
                    row.put("rowNum", rowNum);
                    previewItems.add(row);
                }
            } else {
                errors.addAll(rowErrors);
            }
        }

        if (validRows == 0 && errors.isEmpty()) {
            warnings.add("CSV 仅含标题行，无数据行");
        }

        LocalProductImportPreview preview = new LocalProductImportPreview();
        preview.setItems(previewItems);
        preview.setTotalRows(rows.size() - 1);
        preview.setValidRows(validRows);
        preview.setErrors(errors);
        preview.setDuplicateCount(duplicateCount);
        preview.setWarnings(warnings);
        return preview;
    }

    /**
     * 执行导入（写入 local_product 表，返回统计）
     */
    public LocalProductImportResult confirmImport(LocalProductImportRequest request) throws IOException {
        LocalProductImportPreview preview = previewImport(request);
        if (preview.getValidRows() == 0) {
            return new LocalProductImportResult(preview.getTotalRows(), 0, 0, preview.getTotalRows(), List.of("无有效行可导入"));
        }

        // 重新解析完整 CSV 并写入
        List<String[]> rows = parseCsv(request.getFile());
        String[] header = rows.get(0);
        Map<String, Integer> colIdx = mapColumns(header);

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            if (isEmptyRow(cols)) continue;
            try {
                Map<String, Object> item = parseSingleRow(cols, colIdx, request);
                if (item == null) { skipped++; continue; }
                LocalProduct p = new LocalProduct();
                applyRow(p, item);
                p.setStatus(STATUS_DRAFT);
                p.setCreatedAt(LocalDateTime.now());
                p.setUpdatedAt(LocalDateTime.now());
                localProductMapper.insert(p);
                imported++;
            } catch (Exception e) {
                failed++;
                errors.add("第 " + (i + 1) + " 行: " + e.getMessage());
            }
        }
        return new LocalProductImportResult(rows.size() - 1, imported, skipped, failed, errors);
    }

    // ==================== 导入工具方法 ====================

    private List<String[]> parseCsv(org.springframework.web.multipart.MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) rows.add(line.split(",", -1));
            }
        }
        return rows;
    }

    private Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) map.put(header[i].trim().toLowerCase(), i);
        return map;
    }

    private String getCol(String[] cols, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name.toLowerCase());
        return (i != null && i < cols.length) ? cols[i].trim() : "";
    }

    private LocalProductImportPreview.ImportError err(int row, String field, String msg) {
        LocalProductImportPreview.ImportError e = new LocalProductImportPreview.ImportError();
        e.setRow(row); e.setField(field); e.setMessage(msg);
        return e;
    }

    private boolean isEmptyRow(String[] cols) {
        for (String c : cols) if (!c.trim().isEmpty()) return false;
        return true;
    }

    private int parseIntOr(String s, int defaultVal,
                           List<LocalProductImportPreview.ImportError> errors, int row, String field) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) {
            errors.add(err(row, field, "整数格式无效: " + s));
            return defaultVal;
        }
    }

    private List<String> parseImagesFromRaw(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private Map<String, Object> parseSingleRow(String[] cols, Map<String, Integer> colIdx,
                                                LocalProductImportRequest request) {
        Map<String, Object> row = new HashMap<>();
        String title = getCol(cols, colIdx, COL_TITLE);
        if (title.isEmpty()) return null;
        String accountName = getCol(cols, colIdx, COL_ACCOUNT_NAME);
        XianyuAccount acc = accountService.findByName(accountName).orElse(null);
        if (acc == null) return null;

        row.put("title", title);
        row.put("accountId", acc.getId());
        row.put("price", parseDoubleOrDefault(getCol(cols, colIdx, COL_PRICE), 0));
        row.put("stock", (int) parseDoubleOrDefault(getCol(cols, colIdx, COL_STOCK), 1));
        row.put("description", getCol(cols, colIdx, COL_DESCRIPTION));
        row.put("goodsType", getCol(cols, colIdx, COL_GOODS_TYPE).isEmpty()
                ? request.getDefaultGoodsType() : getCol(cols, colIdx, COL_GOODS_TYPE));
        row.put("deliverType", getCol(cols, colIdx, COL_DELIVER_TYPE));

        // 图片处理
        List<String> images = parseImagesFromRaw(getCol(cols, colIdx, COL_IMAGES));
        List<String> converted = new ArrayList<>();
        for (String img : images) {
            if (img.startsWith("data:image")) {
                try {
                    String ext = img.substring(img.indexOf("/") + 1, img.indexOf(";"));
                    String base64 = img.substring(img.indexOf(",") + 1);
                    byte[] data = Base64.getDecoder().decode(base64);
                    Path dir = Paths.get(request.getImageStoragePath());
                    Files.createDirectories(dir);
                    String fname = "imp_" + System.nanoTime() + "." + ext;
                    Files.write(dir.resolve(fname), data);
                    converted.add("/uploads/local-products/" + fname);
                } catch (Exception e) {
                    log.warn("[IMPORT] Base64 处理失败: {}", e.getMessage());
                }
            } else {
                converted.add(img);
            }
        }
        row.put("images", converted);

        // 虚拟发货内容
        String goodsType = (String) row.get("goodsType");
        if ("VIRTUAL".equals(goodsType)) {
            String rawContent = getCol(cols, colIdx, COL_DELIVER_CONTENT);
            List<String> cards = Arrays.stream(rawContent.split(request.getDeliverContentSeparator()))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            row.put("deliverContentTemplate", toJsonArray(cards));
        } else {
            row.put("deliverContentTemplate", "[]");
        }
        return row;
    }

    private double parseDoubleOrDefault(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    private void applyRow(LocalProduct p, Map<String, Object> row) {
        p.setAccountId((Long) row.get("accountId"));
        p.setTitle((String) row.get("title"));
        p.setPrice(java.math.BigDecimal.valueOf((Double) row.get("price")));
        p.setStock((Integer) row.get("stock"));
        p.setDescription((String) row.get("description"));
        p.setGoodsType((String) row.get("goodsType"));
        p.setDeliverType((String) row.get("deliverType"));
        p.setDeliverContentTemplate((String) row.get("deliverContentTemplate"));
        List<String> images = (List<String>) row.get("images");
        p.setImages(toJsonArray(images));
        if (!images.isEmpty()) p.setImageUrl(images.get(0));
    }

    /** 批量发布结果。 */
    public static class BatchPublishResult {
        public final int success;
        public final int fail;
        public final int skip;
        public final int retried;
        public final List<String> errors;

        public BatchPublishResult(int success, int fail, int skip, int retried, List<String> errors) {
            this.success = success;
            this.fail = fail;
            this.skip = skip;
            this.retried = retried;
            this.errors = errors;
        }

        static BatchPublishResult empty() {
            return new BatchPublishResult(0, 0, 0, 0, Collections.emptyList());
        }
    }
}
