package cn.net.rjnetwork.xianyu.manager.virtual.service;

import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageAccount;
import cn.net.rjnetwork.xianyu.manager.clouddisk.model.CloudStorageFile;
import cn.net.rjnetwork.xianyu.manager.clouddisk.service.CloudStorageService;
import cn.net.rjnetwork.xianyu.manager.order.mapper.OrderMapper;
import cn.net.rjnetwork.xianyu.manager.order.model.XianyuOrder;
import cn.net.rjnetwork.xianyu.manager.product.mapper.ProductMapper;
import cn.net.rjnetwork.xianyu.manager.product.model.XianyuProduct;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualCardPoolMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipConfigMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.mapper.VirtualShipTaskMapper;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualCardPool;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipConfig;
import cn.net.rjnetwork.xianyu.manager.virtual.model.VirtualShipTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动发货引擎（虚拟商品）
 * 核心职责：
 * 1. 订单支付后创建虚拟发货任务
 * 2. 定时扫描并执行发货（发卡密/发内容到闲鱼聊天）
 * 3. 定时扫描并执行自动确认收货
 * 4. 卡密池扣减
 */
@Service
public class VirtualShipService {

    private static final Pattern CARD_PATTERN = Pattern.compile("^(.+?)(?:\\|(.+))?$");

    private final VirtualShipTaskMapper shipTaskMapper;
    private final VirtualCardPoolMapper cardPoolMapper;
    private final VirtualShipConfigMapper shipConfigMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    /** 消息发送器（注入自定义消息发送器；真实场景对接 xianyu-sdk） */
    private final VirtualMessageSender messageSender;
    /** 网盘存储服务（FILE 类型发货） */
    private final CloudStorageService cloudStorageService;

    public VirtualShipService(VirtualShipTaskMapper shipTaskMapper,
                              VirtualCardPoolMapper cardPoolMapper,
                              VirtualShipConfigMapper shipConfigMapper,
                              ProductMapper productMapper,
                              OrderMapper orderMapper,
                              VirtualMessageSender messageSender,
                              CloudStorageService cloudStorageService) {
        this.shipTaskMapper = shipTaskMapper;
        this.cardPoolMapper = cardPoolMapper;
        this.shipConfigMapper = shipConfigMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.messageSender = messageSender;
        this.cloudStorageService = cloudStorageService;
    }

    // ======================================================================
    // 对外接口：订单支付后调用
    // ======================================================================

    /**
     * 支付成功后调用：如果需要虚拟发货，则创建发货任务
     */
    @Transactional
    public VirtualShipTask createShipTaskIfVirtual(Long orderId) {
        XianyuOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("Order not found: " + orderId);
        if (!Boolean.TRUE.equals(order.getRequireVirtualShip())) return null;

        XianyuProduct product = productMapper.selectById(order.getItemTitle() != null ? findProductIdByOrder(order) : null);
        if (product == null || !"VIRTUAL".equals(product.getGoodsType())) return null;

        // 幂等：已存在任务则不重复创建
        VirtualShipTask existing = shipTaskMapper.selectOne(
                new LambdaQueryWrapper<VirtualShipTask>().eq(VirtualShipTask::getOrderId, orderId));
        if (existing != null) return existing;

        VirtualShipConfig config = getConfig(order.getAccountId());
        int delaySeconds = config != null && config.getDelaySeconds() != null ? config.getDelaySeconds() : 0;

        VirtualShipTask task = new VirtualShipTask();
        task.setOrderId(orderId);
        task.setProductId(product.getId());
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());
        shipTaskMapper.insert(task);

        // 更新订单虚拟发货相关字段
        order.setGoodsType("VIRTUAL");
        order.setRequireVirtualShip(true);
        if (config != null && config.getAutoConfirmDays() != null) {
            order.setAutoReceiptAt(LocalDateTime.now().plusDays(config.getAutoConfirmDays()));
        }
        orderMapper.updateById(order);

        return task;
    }

    /**
     * 根据订单反查 productId（通过 order.item_title 关联）
     * 预留：建议后续给 XianyuOrder 加 productId 字段
     */
    private Long findProductIdByOrder(XianyuOrder order) {
        // 当前 schema 没有给 order 加 productId，通过 title 模糊匹配（注意：可能不准，建议加字段）
        XianyuProduct product = productMapper.selectOne(
                new LambdaQueryWrapper<XianyuProduct>()
                        .eq(XianyuProduct::getAccountId, order.getAccountId())
                        .eq(XianyuProduct::getTitle, order.getItemTitle())
                        .last("LIMIT 1"));
        return product != null ? product.getId() : null;
    }

    // ======================================================================
    // 定时任务：扫描并执行发货
    // ======================================================================

    /**
     * 每 30 秒扫描一次待发货任务
     */
    @Scheduled(fixedDelay = 30_000)
    public void scanAndShip() {
        List<VirtualShipTask> pending = shipTaskMapper.selectList(
                new LambdaQueryWrapper<VirtualShipTask>()
                        .eq(VirtualShipTask::getStatus, "PENDING")
                        .orderByAsc(VirtualShipTask::getCreatedAt)
                        .last("LIMIT 20"));
        for (VirtualShipTask task : pending) {
            processShipTask(task);
        }
    }

    /**
     * 每 5 分钟扫描失败任务重试（最多重试 3 次）
     */
    @Scheduled(fixedDelay = 300_000)
    public void retryFailedShipTasks() {
        List<VirtualShipTask> failed = shipTaskMapper.selectList(
                new LambdaQueryWrapper<VirtualShipTask>()
                        .eq(VirtualShipTask::getStatus, "FAILED")
                        .lt(VirtualShipTask::getRetryCount, 3)
                        .last("LIMIT 10"));
        for (VirtualShipTask task : failed) {
            processShipTask(task);
        }
    }

    @Transactional
    public void processShipTask(VirtualShipTask task) {
        task.setStatus("PROCESSING");
        shipTaskMapper.updateById(task);

        XianyuOrder order = orderMapper.selectById(task.getOrderId());
        XianyuProduct product = productMapper.selectById(task.getProductId());
        if (order == null || product == null) {
            failTask(task, "Order or product not found");
            return;
        }

        try {
            String deliverContent = acquireDeliverContent(product);
            if (deliverContent == null || deliverContent.isBlank()) {
                failTask(task, "No available card/content in pool");
                return;
            }

            // 发送到闲鱼聊天（真实场景通过 sdk 发消息）
            messageSender.sendToBuyer(order, deliverContent);

            // 更新订单发货状态
            order.setVirtualShippedAt(LocalDateTime.now());
            order.setDeliverContent(deliverContent);
            order.setStatus("SHIPPED");
            orderMapper.updateById(order);

            // 更新任务
            task.setStatus("SHIPPED");
            task.setProcessedAt(LocalDateTime.now());
            shipTaskMapper.updateById(task);

        } catch (Exception e) {
            failTask(task, e.getMessage());
        }
    }

    /**
     * 从卡密池取一条，并标记为已使用
     */
    @Transactional
    protected String acquireDeliverContent(XianyuProduct product) {
        String type = product.getDeliverType();
        if ("CARD".equals(type) || "ACCOUNT".equals(type)) {
            // 从池子取一条
            VirtualCardPool card = cardPoolMapper.selectOne(
                    new LambdaQueryWrapper<VirtualCardPool>()
                            .eq(VirtualCardPool::getProductId, product.getId())
                            .eq(VirtualCardPool::getStatus, "AVAILABLE")
                            .orderByAsc(VirtualCardPool::getId)
                            .last("LIMIT 1"));
            if (card == null) return null;

            card.setStatus("USED");
            card.setUsedAt(LocalDateTime.now());
            cardPoolMapper.updateById(card);

            // 构造发卡内容
            if (card.getCardPassword() != null && !card.getCardPassword().isBlank()) {
                return String.format("卡号：%s\n密码：%s", card.getCardCode(), card.getCardPassword());
            }
            return card.getCardCode();
        }

        if ("LINK".equals(type)) {
            return product.getDeliverContentTemplate();
        }

        if ("FILE".equals(type)) {
            // 真实场景：文件路径在 deliverContentTemplate，调 CloudStorageService 上传 + 分享
            String filePath = product.getDeliverContentTemplate();
            if (filePath == null || filePath.isBlank()) {
                return null;
            }
            try {
                // 1. 查找该商品的网盘账号（取第一个可用的）
                List<CloudStorageAccount> accounts = cloudStorageService.listAccounts(product.getAccountId());
                if (accounts.isEmpty()) {
                    return "【系统忙碌】网盘账号未配置，请稍后重试";
                }
                CloudStorageAccount account = accounts.get(0);

                // 2. 上传文件到网盘
                java.io.File file = new java.io.File(filePath);
                if (!file.exists()) {
                    return "【系统错误】商品文件不存在: " + filePath;
                }
                cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest uploadReq =
                        new cn.net.rjnetwork.xianyu.manager.clouddisk.dto.FileUploadRequest();
                uploadReq.setFileName(file.getName());
                uploadReq.setFileSize(file.length());
                uploadReq.setMimeType(java.nio.file.Files.probeContentType(file.toPath()));
                uploadReq.setTargetPath("/xianyu-virtual-ship/" + product.getId());
                uploadReq.setExpireDays(30);
                try (FileInputStream fis = new FileInputStream(file)) {
                    uploadReq.setContent(fis);
                    CloudStorageFile uploaded = cloudStorageService.uploadFile(account.getId(), uploadReq);
                    if (uploaded != null && "COMPLETED".equals(uploaded.getUploadStatus())) {
                        String link = cloudStorageService.shareFile(uploaded.getId());
                        return String.format("下载链接：%s\n提取码：%s\n有效期：7天",
                                link, uploaded.getExtractCode());
                    }
                }
                return "【系统错误】文件上传失败，请稍后重试";
            } catch (Exception e) {
                System.err.println("[VirtualShip] FILE deliver failed: " + e.getMessage());
                return "【系统错误】文件发货失败，请联系客服";
            }
        }

        return null;
    }

    private void failTask(VirtualShipTask task, String error) {
        task.setStatus("FAILED");
        task.setErrorMessage(error);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setProcessedAt(LocalDateTime.now());
        shipTaskMapper.updateById(task);
    }

    // ======================================================================
    // 定时任务：自动确认收货
    // ======================================================================

    /**
     * 每 30 分钟扫描需要确认收货的订单
     */
    @Scheduled(fixedDelay = 1_800_000)
    public void autoConfirmReceipt() {
        List<XianyuOrder> toConfirm = orderMapper.selectList(
                new LambdaQueryWrapper<XianyuOrder>()
                        .eq(XianyuOrder::getRequireVirtualShip, true)
                        .ne(XianyuOrder::getStatus, "COMPLETED")
                        .le(XianyuOrder::getAutoReceiptAt, LocalDateTime.now())
                        .last("LIMIT 50"));
        for (XianyuOrder order : toConfirm) {
            try {
                order.setStatus("COMPLETED");
                orderMapper.updateById(order);
                // 真实场景：调用闲鱼 sdk 确认收货
            } catch (Exception e) {
                System.err.println("[VirtualShip] auto confirm failed for order " + order.getId() + ": " + e.getMessage());
            }
        }
    }

    // ======================================================================
    // 卡密池管理
    // ======================================================================

    @Transactional
    public int importCards(Long productId, List<String> cards) {
        int count = 0;
        for (String line : cards) {
            if (line == null || line.isBlank()) continue;
            Matcher m = CARD_PATTERN.matcher(line.trim());
            if (!m.matches()) continue;
            VirtualCardPool card = new VirtualCardPool();
            card.setProductId(productId);
            card.setCardCode(m.group(1).trim());
            card.setCardPassword(m.group(2) != null ? m.group(2).trim() : null);
            card.setStatus("AVAILABLE");
            card.setCreatedAt(LocalDateTime.now());
            try {
                cardPoolMapper.insert(card);
                count++;
            } catch (Exception e) {
                System.err.println("[VirtualShip] import card failed: " + e.getMessage());
            }
        }
        return count;
    }

    // ======================================================================
    // 配置管理
    // ======================================================================

    public VirtualShipConfig getConfig(Long accountId) {
        return shipConfigMapper.selectOne(
                new LambdaQueryWrapper<VirtualShipConfig>()
                        .eq(VirtualShipConfig::getAccountId, accountId));
    }

    @Transactional
    public VirtualShipConfig saveConfig(Long accountId, Boolean enabled, Integer delaySeconds,
                                        Integer autoConfirmDays, Boolean notifyAfterShip) {
        VirtualShipConfig config = getConfig(accountId);
        if (config == null) {
            config = new VirtualShipConfig();
            config.setAccountId(accountId);
            config.setEnabled(enabled != null ? enabled : true);
            config.setDelaySeconds(delaySeconds != null ? delaySeconds : 30);
            config.setAutoConfirmDays(autoConfirmDays != null ? autoConfirmDays : 7);
            config.setNotifyAfterShip(notifyAfterShip != null ? notifyAfterShip : true);
            config.setCreatedAt(LocalDateTime.now());
            shipConfigMapper.insert(config);
        } else {
            if (enabled != null) config.setEnabled(enabled);
            if (delaySeconds != null) config.setDelaySeconds(delaySeconds);
            if (autoConfirmDays != null) config.setAutoConfirmDays(autoConfirmDays);
            if (notifyAfterShip != null) config.setNotifyAfterShip(notifyAfterShip);
            shipConfigMapper.updateById(config);
        }
        return config;
    }

    // ======================================================================
    // 查询
    // ======================================================================

    public List<VirtualShipTask> listTasks(String status, int page, int size) {
        LambdaQueryWrapper<VirtualShipTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) wrapper.eq(VirtualShipTask::getStatus, status);
        wrapper.orderByDesc(VirtualShipTask::getCreatedAt);
        // 用 Page 分页
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VirtualShipTask> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        return shipTaskMapper.selectPage(p, wrapper).getRecords();
    }

    /**
     * 手动触发发货任务（从 PENDING 状态重新执行 processShipTask）
     */
    @Transactional
    public void triggerTask(Long taskId) {
        VirtualShipTask task = shipTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new IllegalStateException("Task is not PENDING, current status: " + task.getStatus());
        }
        processShipTask(task);
    }

    public List<VirtualCardPool> listCards(Long productId, String status) {
        LambdaQueryWrapper<VirtualCardPool> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) wrapper.eq(VirtualCardPool::getProductId, productId);
        if (status != null && !status.isBlank()) wrapper.eq(VirtualCardPool::getStatus, status);
        wrapper.orderByDesc(VirtualCardPool::getCreatedAt);
        return cardPoolMapper.selectList(wrapper);
    }

    public int deleteCard(Long id) {
        return cardPoolMapper.deleteById(id);
    }
}
