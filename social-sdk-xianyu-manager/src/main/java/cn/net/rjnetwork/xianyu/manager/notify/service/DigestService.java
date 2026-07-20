package cn.net.rjnetwork.xianyu.manager.notify.service;

import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyChannelMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyDigestConfigMapper;
import cn.net.rjnetwork.xianyu.manager.notify.mapper.NotifyMessageMapper;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyChannel;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyDigestConfig;
import cn.net.rjnetwork.xianyu.manager.notify.model.NotifyMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 每日通知摘要。每天定时（默认 09:00）聚合窗口内（昨日）的站内通知，
 * 按场景汇总成单条摘要，经配置通道（邮件/短信）发送，并可在站内收件箱留痕。
 */
@Service
public class DigestService {

    private static final Logger logger = LoggerFactory.getLogger(DigestService.class);

    private final NotifyDigestConfigMapper digestMapper;
    private final NotifyMessageMapper messageMapper;
    private final NotificationService notificationService;
    private final NotifyChannelMapper channelMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${notify.digest.cron:0 0 9 * * ?}")
    private String cronExpression;

    public DigestService(NotifyDigestConfigMapper digestMapper,
                         NotifyMessageMapper messageMapper,
                         NotificationService notificationService,
                         NotifyChannelMapper channelMapper) {
        this.digestMapper = digestMapper;
        this.messageMapper = messageMapper;
        this.notificationService = notificationService;
        this.channelMapper = channelMapper;
    }

    @Scheduled(cron = "${notify.digest.cron:0 0 9 * * ?}")
    public void scheduledRun() {
        runDigest(false);
    }

    /** 立即发送一次摘要（窗口为最近 24 小时，供测试/补发），返回真实结果供前端展示 */
    public Map<String, Object> sendNow() {
        return runDigest(true);
    }

    public Map<String, Object> runDigest(boolean force) {
        NotifyDigestConfig cfg = digestMapper.selectById(1L);
        if (cfg == null) {
            return result(false, "未配置每日摘要", 0, null, null);
        }
        if (!force && !Boolean.TRUE.equals(cfg.getEnabled())) {
            return result(false, "每日摘要未启用", 0, null, null);
        }
        if (cfg.getChannelId() == null) {
            return result(false, "未配置发送通道", 0, null, null);
        }

        // 取通道信息（用于结果展示 + 判断是否邮件正文格式）
        NotifyChannel channel = null;
        try { channel = channelMapper.selectById(cfg.getChannelId()); } catch (Exception ignored) {}
        String channelName = channel != null ? channel.getName() : null;
        String channelType = channel != null ? channel.getType() : null;
        boolean isEmail = "EMAIL".equals(channelType);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = force ? now : now.toLocalDate().atStartOfDay();
        LocalDateTime start = force ? now.minusHours(24) : end.minusDays(1);

        List<NotifyMessage> msgs = messageMapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .ge(NotifyMessage::getCreatedAt, start)
                .lt(NotifyMessage::getCreatedAt, end));

        // 场景过滤
        Set<String> allow = new HashSet<>();
        if (cfg.getScenarios() != null && !cfg.getScenarios().isBlank()) {
            try {
                JsonNode arr = mapper.readTree(cfg.getScenarios());
                if (arr.isArray()) arr.forEach(n -> allow.add(n.asText()));
            } catch (Exception ignored) {}
        }
        final Set<String> filter = allow;
        List<NotifyMessage> included = msgs.stream()
                .filter(m -> filter.isEmpty() || filter.contains(m.getScenario()))
                .collect(Collectors.toList());

        // 按场景聚合计数
        Map<String, AtomicInteger> counts = new LinkedHashMap<>();
        for (NotifyMessage m : included) {
            counts.computeIfAbsent(m.getScenario() == null ? "OTHER" : m.getScenario(), k -> new AtomicInteger(0)).incrementAndGet();
        }

        String dateLabel = start.toLocalDate().toString();
        String title = "AI鱼多宝 每日通知摘要 (" + dateLabel + ")";
        // 即使窗口内无通知，force 模式也发一条“暂无通知”的测试摘要，让用户能验证通道是否通
        String body = buildBody(isEmail, dateLabel, counts, included.size());

        List<String> recipients = (cfg.getRecipients() == null || cfg.getRecipients().isBlank())
                ? Collections.emptyList()
                : Arrays.asList(cfg.getRecipients().split("[,;\\s]+"));

        try {
            notificationService.dispatchViaChannel(cfg.getChannelId(), recipients, title, body);
            logger.info("每日摘要已发送（{} 条通知）", included.size());
            // 站内留痕
            if (Boolean.TRUE.equals(cfg.getIncludeInApp())) {
                try {
                    NotifyMessage m = new NotifyMessage();
                    m.setAccountId(null);
                    m.setScenario("DAILY_DIGEST");
                    m.setTitle(title);
                    m.setContent(isEmail ? body.replaceAll("<[^>]+>", "") : body);
                    m.setIsRead(false);
                    m.setCreatedAt(LocalDateTime.now());
                    messageMapper.insert(m);
                } catch (Exception ignored) {}
            }
            String reason = included.isEmpty()
                    ? "窗口内无通知，已发送测试摘要以验证通道"
                    : "已发送 " + included.size() + " 条通知的摘要";
            return result(true, reason, included.size(), channelName, channelType);
        } catch (Exception e) {
            logger.error("每日摘要发送失败", e);
            return result(false, "发送失败：" + e.getMessage(), included.size(), channelName, channelType);
        }
    }

    private Map<String, Object> result(boolean sent, String reason, int total, String channelName, String channelType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sent", sent);
        m.put("reason", reason);
        m.put("total", total);
        m.put("channelName", channelName);
        m.put("channelType", channelType);
        return m;
    }

    private String buildBody(boolean isEmail, String dateLabel, Map<String, AtomicInteger> counts, int total) {
        StringBuilder sb = new StringBuilder();
        if (isEmail) {
            sb.append("<h3>AI鱼多宝 每日通知摘要（").append(dateLabel).append("）</h3>");
            if (total == 0) {
                sb.append("<p>窗口内暂无通知。</p>");
            } else {
                sb.append("<p>窗口内共产生 <b>").append(total).append("</b> 条通知：</p><ul>");
                counts.forEach((s, c) -> sb.append("<li>").append(escape(s)).append("：").append(c.get()).append(" 条</li>"));
                sb.append("</ul>");
            }
        } else {
            sb.append("AI鱼多宝每日通知摘要(").append(dateLabel).append(")\n");
            if (total == 0) {
                sb.append("窗口内暂无通知。\n");
            } else {
                sb.append("窗口内共 ").append(total).append(" 条通知：\n");
                counts.forEach((s, c) -> sb.append("- ").append(s).append("：").append(c.get()).append(" 条\n"));
            }
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
