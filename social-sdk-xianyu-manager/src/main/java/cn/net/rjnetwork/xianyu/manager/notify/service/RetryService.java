package cn.net.rjnetwork.xianyu.manager.notify.service;

import cn.net.rjnetwork.xianyu.manager.common.CryptoUtil;
import cn.net.rjnetwork.xianyu.manager.notify.NotifyEvent;
import cn.net.rjnetwork.xianyu.manager.notify.adapter.ChannelAdapter;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyChannelMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyLogMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyRetryMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyLog;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyRetry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 通知重试队列。发送失败或触发限频时入队（enqueue），
 * 由定时任务（默认每 30s）扫描到期任务，按指数退避重发，耗尽标记 GIVEN_UP。
 */
@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);

    private final NotifyRetryMapper retryMapper;
    private final NotifyChannelMapper channelMapper;
    private final NotifyLogMapper logMapper;
    private final CryptoUtil cryptoUtil;
    private final List<ChannelAdapter> adapters;

    @Value("${notify.retry.enabled:true}") private boolean enabled;
    @Value("${notify.retry.max-attempts:5}") private int maxAttempts;
    @Value("${notify.retry.initial-delay-seconds:60}") private int initialDelay;
    @Value("${notify.retry.max-delay-seconds:1800}") private int maxDelay;

    public RetryService(NotifyRetryMapper retryMapper,
                        NotifyChannelMapper channelMapper,
                        NotifyLogMapper logMapper,
                        CryptoUtil cryptoUtil,
                        List<ChannelAdapter> adapters) {
        this.retryMapper = retryMapper;
        this.channelMapper = channelMapper;
        this.logMapper = logMapper;
        this.cryptoUtil = cryptoUtil;
        this.adapters = adapters;
    }

    /** 入队一次重试（限频场景可用 initialDelaySeconds 指定更短延时） */
    public void enqueue(NotifyEvent event, NotifyChannel channel, List<String> recipients,
                        String title, String body, String error) {
        enqueue(event, channel, recipients, title, body, error, 0);
    }

    public void enqueue(NotifyEvent event, NotifyChannel channel, List<String> recipients,
                        String title, String body, String error, int initialDelaySeconds) {
        if (!enabled) return;
        try {
            NotifyRetry r = new NotifyRetry();
            r.setScenario(event.getScenario());
            r.setChannelId(channel.getId());
            r.setChannelType(channel.getType());
            r.setRecipient(recipients == null || recipients.isEmpty() ? "(通道默认)" : String.join(",", recipients));
            r.setTitle(title);
            r.setBody(body);
            r.setRetryCount(0);
            r.setMaxRetry(maxAttempts);
            long delay = initialDelaySeconds > 0 ? initialDelaySeconds : initialDelay;
            r.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
            r.setStatus("PENDING");
            r.setLastError(error);
            r.setCreatedAt(LocalDateTime.now());
            retryMapper.insert(r);
        } catch (Exception e) {
            logger.error("写入重试队列失败 scenario={}", event != null ? event.getScenario() : "?", e);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void processDue() {
        if (!enabled) return;
        try {
            List<NotifyRetry> due = retryMapper.selectList(new LambdaQueryWrapper<NotifyRetry>()
                    .eq(NotifyRetry::getStatus, "PENDING")
                    .le(NotifyRetry::getNextRetryAt, LocalDateTime.now())
                    .last("LIMIT 50"));
            for (NotifyRetry r : due) {
                processOne(r);
            }
        } catch (Exception e) {
            logger.error("处理重试队列异常", e);
        }
    }

    private void processOne(NotifyRetry r) {
        r.setStatus("SENDING");
        retryMapper.updateById(r);

        NotifyChannel channel = channelMapper.selectById(r.getChannelId());
        if (channel == null || !Boolean.TRUE.equals(channel.getEnabled())) {
            r.setStatus("GIVEN_UP");
            r.setLastError("通道不存在或已禁用");
            retryMapper.updateById(r);
            writeFinalFailed(r);
            return;
        }
        try {
            channel.setConfigJson(cryptoUtil.decrypt(channel.getConfigJson()));
            List<String> recipients = (r.getRecipient() == null || r.getRecipient().isBlank() || "(通道默认)".equals(r.getRecipient()))
                    ? Collections.emptyList()
                    : Arrays.asList(r.getRecipient().split("[,;\\s]+"));
            ChannelAdapter adapter = adapters.stream()
                    .filter(a -> a.type().equals(channel.getType())).findFirst().orElse(null);
            if (adapter == null) {
                throw new IllegalStateException("无对应通道适配器： " + channel.getType());
            }
            adapter.send(channel, r.getTitle(), r.getBody(), recipients);
            r.setStatus("DONE");
            retryMapper.updateById(r);
            writeLog(r, "SENT", null);
        } catch (Exception e) {
            int cnt = (r.getRetryCount() == null ? 0 : r.getRetryCount()) + 1;
            r.setRetryCount(cnt);
            r.setLastError(e.getMessage());
            if (cnt >= (r.getMaxRetry() == null ? maxAttempts : r.getMaxRetry())) {
                r.setStatus("GIVEN_UP");
                retryMapper.updateById(r);
                writeFinalFailed(r);
            } else {
                long delay = Math.min(maxDelay, (long) initialDelay * (1L << cnt));
                r.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
                r.setStatus("PENDING");
                retryMapper.updateById(r);
            }
        }
    }

    private void writeLog(NotifyRetry r, String status, String error) {
        try {
            NotifyLog log = new NotifyLog();
            log.setScenario(r.getScenario());
            log.setChannelId(r.getChannelId());
            log.setChannelType(r.getChannelType());
            log.setRecipient(r.getRecipient());
            log.setStatus(status);
            log.setPayload("RETRY -> " + r.getChannelType());
            log.setError(error);
            log.setCreatedAt(LocalDateTime.now());
            log.setSentAt("SENT".equals(status) ? LocalDateTime.now() : null);
            logMapper.insert(log);
        } catch (Exception ignored) {}
    }

    private void writeFinalFailed(NotifyRetry r) {
        writeLog(r, "FAILED", "重试耗尽：" + r.getLastError());
    }
}
