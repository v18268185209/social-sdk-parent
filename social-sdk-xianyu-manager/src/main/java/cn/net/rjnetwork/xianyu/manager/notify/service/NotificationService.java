package cn.net.rjnetwork.xianyu.manager.notify.service;

import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.message.websocket.MessageBroadcaster;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyScenario;
import cn.net.rjnetwork.xianyu.manager.notify.TemplateRenderer;
import cn.net.rjnetwork.xianyu.manager.notify.adapter.ChannelAdapter;
import cn.net.rjnetwork.xianyu.manager.notify.model.*;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.*;
import cn.net.rjnetwork.xianyu.manager.notify.service.SendRateLimiter;
import cn.net.rjnetwork.xianyu.manager.notify.service.RetryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通知编排核心。监听业务发布的 NotifyEvent，按订阅规则分发到各通道（异步），
 * 写投递日志，并落站内收件箱（NotifyMessage）。带场景级去重冷却，防骚扰。
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final NotifyChannelMapper channelMapper;
    private final NotifyTemplateMapper templateMapper;
    private final NotifySubscriptionMapper subscriptionMapper;
    private final NotifyLogMapper logMapper;
    private final NotifyMessageMapper messageMapper;
    private final CryptoUtil cryptoUtil;
    private final MessageBroadcaster broadcaster;
    private final List<ChannelAdapter> adapters;
    private final SendRateLimiter rateLimiter;
    private final RetryService retryService;

    @org.springframework.beans.factory.annotation.Value("${notify.rate-limit-retry-delay-seconds:30}")
    private int rateLimitRetryDelay;

    /** 去重缓存：scenario::accountId -> 上次发送时间；冷却内不重复发 */
    private final Cache<String, Long> dedup = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .build();

    public NotificationService(NotifyChannelMapper channelMapper,
                              NotifyTemplateMapper templateMapper,
                              NotifySubscriptionMapper subscriptionMapper,
                              NotifyLogMapper logMapper,
                              NotifyMessageMapper messageMapper,
                              CryptoUtil cryptoUtil,
                              MessageBroadcaster broadcaster,
                              List<ChannelAdapter> adapters,
                              SendRateLimiter rateLimiter,
                              RetryService retryService) {
        this.channelMapper = channelMapper;
        this.templateMapper = templateMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.logMapper = logMapper;
        this.messageMapper = messageMapper;
        this.cryptoUtil = cryptoUtil;
        this.broadcaster = broadcaster;
        this.adapters = adapters;
        this.rateLimiter = rateLimiter;
        this.retryService = retryService;
    }

    @Async
    @EventListener
    public void onEvent(NotifyEvent event) {
        NotifyScenario scenario = NotifyScenario.fromName(event.getScenario());
        if (scenario == null) {
            logger.warn("未知通知场景：{}", event.getScenario());
            return;
        }

        // 去重冷却
        String dedupKey = event.getScenario() + "::" + (event.getAccountId() != null ? event.getAccountId() : "global");
        Long last = dedup.getIfPresent(dedupKey);
        long now = System.currentTimeMillis();
        if (last != null && (now - last) < scenario.getCooldownSeconds() * 1000L) {
            logger.debug("场景 {} 冷却中，跳过", event.getScenario());
            return;
        }
        dedup.put(dedupKey, now);

        // 渲染模板
        NotifyTemplate tpl = templateMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotifyTemplate>()
                        .eq(NotifyTemplate::getScenario, event.getScenario())
                        .eq(NotifyTemplate::getEnabled, true));
        String title = TemplateRenderer.render(
                tpl != null && tpl.getTitleTpl() != null ? tpl.getTitleTpl() : scenario.getDefaultTitle(),
                event.getVars());
        String body = TemplateRenderer.render(
                tpl != null && tpl.getBodyTpl() != null ? tpl.getBodyTpl() : scenario.getDefaultBody(),
                event.getVars());

        // 落站内收件箱（站内 + 外部统一）
        saveInApp(event, title, body);
        // 站内实时广播（前端若接 STOMP 即可即时收到）
        try {
            broadcaster.broadcastAll(mapper.writeValueAsString(Map.of(
                    "type", "notification", "scenario", event.getScenario(),
                    "title", title, "body", body, "accountId", event.getAccountId())));
        } catch (Exception ignored) {}

        // 按订阅规则分发外部通道
        List<NotifySubscription> subs = subscriptionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotifySubscription>()
                        .eq(NotifySubscription::getScenario, event.getScenario())
                        .eq(NotifySubscription::getEnabled, true));
        if (subs.isEmpty()) {
            logger.debug("场景 {} 无启用订阅，仅站内通知", event.getScenario());
            return;
        }

        for (NotifySubscription sub : subs) {
            NotifyChannel channel = channelMapper.selectById(sub.getChannelId());
            if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) continue;
            // 账号范围过滤
            if ("CUSTOM".equals(sub.getAccountScope())
                    && event.getAccountId() != null
                    && !accountInList(sub.getAccountIds(), event.getAccountId())) {
                continue;
            }
            // 解密配置并分发
            String decrypted = cryptoUtil.decrypt(channel.getConfigJson());
            channel.setConfigJson(decrypted);
            List<String> recipients = resolveRecipients(sub, channel);

            // 通道限频：超限则转入重试队列短延时重发，不直接丢弃
            int channelRate = 0;
            try {
                JsonNode cfgNode = mapper.readTree(channel.getConfigJson());
                if (cfgNode != null && cfgNode.has("rateLimitPerMinute")) {
                    channelRate = cfgNode.get("rateLimitPerMinute").asInt(0);
                }
            } catch (Exception ignored) {}
            if (!rateLimiter.tryAcquire(channel.getId(), channelRate)) {
                logger.warn("通道 {} 触发限频，转入重试队列", channel.getName());
                retryService.enqueue(event, channel, recipients, title, body, "触发限频，延迟重试", rateLimitRetryDelay);
                continue;
            }

            try {
                ChannelAdapter adapter = adapterFor(channel.getType());
                if (adapter == null) {
                    throw new IllegalStateException("无对应通道适配器： " + channel.getType());
                }
                adapter.send(channel, title, body, recipients, event.getVars());
                writeLog(event, channel, recipients, "SENT", null);
            } catch (Exception e) {
                logger.error("通知发送失败 scenario={} channel={}", event.getScenario(), channel.getName(), e);
                writeLog(event, channel, recipients, "FAILED", e.getMessage());
                retryService.enqueue(event, channel, recipients, title, body, e.getMessage());
            }
        }
    }

    private void saveInApp(NotifyEvent event, String title, String body) {
        try {
            NotifyMessage msg = new NotifyMessage();
            msg.setAccountId(event.getAccountId());
            msg.setScenario(event.getScenario());
            msg.setTitle(title);
            msg.setContent(body);
            msg.setIsRead(false);
            msg.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(msg);
        } catch (Exception e) {
            logger.error("保存站内通知失败", e);
        }
    }

    /**
     * 直接发送测试消息（供通道“测试”按钮调用）。config_json 应为已解密的明文。
     */
    public void sendTest(NotifyChannel channel, String title, String body) {
        if (channel == null || channel.getConfigJson() == null) {
            throw new IllegalArgumentException("通道配置为空");
        }
        ChannelAdapter adapter = adapterFor(channel.getType());
        if (adapter == null) throw new IllegalStateException("无对应通道适配器： " + channel.getType());
        try {
            adapter.send(channel, title, body, Collections.emptyList(), Map.of("body", body, "title", title));
            writeLog(new NotifyEvent("TEST", null, null, Map.of()), channel, List.of(), "SENT", null);
        } catch (Exception e) {
            writeLog(new NotifyEvent("TEST", null, null, Map.of()), channel, List.of(), "FAILED", e.getMessage());
            throw new IllegalStateException("发送失败： " + e.getMessage(), e);
        }
    }

    /**
     * 经指定通道直接发送一条消息（供每日摘要等合成场景调用）。
     * config_json 自动解密，发送结果写入投递日志。
     */
    public void dispatchViaChannel(Long channelId, List<String> recipients, String title, String body) {
        NotifyChannel channel = channelMapper.selectById(channelId);
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            throw new IllegalStateException("通道不存在或已禁用");
        }
        String decrypted = cryptoUtil.decrypt(channel.getConfigJson());
        channel.setConfigJson(decrypted);
        try {
            ChannelAdapter adapter = adapterFor(channel.getType());
            if (adapter == null) {
                throw new IllegalStateException("无对应通道适配器： " + channel.getType());
            }
            adapter.send(channel, title, body, recipients == null ? Collections.emptyList() : recipients, Map.of("body", body, "title", title));
            writeLog(new NotifyEvent("DIGEST", null, null, Map.of()), channel,
                    recipients == null ? Collections.emptyList() : recipients, "SENT", null);
        } catch (Exception e) {
            writeLog(new NotifyEvent("DIGEST", null, null, Map.of()), channel,
                    recipients == null ? Collections.emptyList() : recipients, "FAILED", e.getMessage());
            throw new IllegalStateException("摘要发送失败： " + e.getMessage(), e);
        }
    }

    private List<String> resolveRecipients(NotifySubscription sub, NotifyChannel channel) {
        if ("CUSTOM".equals(sub.getRecipientScope()) && sub.getRecipients() != null && !sub.getRecipients().isBlank()) {
            return Arrays.asList(sub.getRecipients().split("[,;\\s]+"));
        }
        return Collections.emptyList(); // EMAIL 会回退到通道 defaultTo；WEBHOOK 忽略
    }

    private boolean accountInList(String json, Long accountId) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonNode arr = mapper.readTree(json);
            if (arr.isArray()) {
                for (JsonNode n : arr) if (n.asLong() == accountId) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private ChannelAdapter adapterFor(String type) {
        return adapters.stream().filter(a -> a.type().equals(type)).findFirst().orElse(null);
    }

    private void writeLog(NotifyEvent event, NotifyChannel channel, List<String> recipients, String status, String error) {
        try {
            NotifyLog log = new NotifyLog();
            log.setScenario(event.getScenario());
            log.setChannelId(channel.getId());
            log.setChannelType(channel.getType());
            log.setRecipient(recipients.isEmpty() ? "(通道默认)" : String.join(",", recipients));
            log.setStatus(status);
            log.setPayload(channel.getType() + " -> " + channel.getName());
            log.setError(error);
            log.setCreatedAt(LocalDateTime.now());
            log.setSentAt(status.equals("SENT") ? LocalDateTime.now() : null);
            logMapper.insert(log);
        } catch (Exception ignored) {}
    }
}
