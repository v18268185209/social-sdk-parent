package cn.net.rjnetwork.xianyu.manager.buyer.service;

import cn.net.rjnetwork.xianyu.manager.buyer.mapper.BuyerProfileMapper;
import cn.net.rjnetwork.xianyu.manager.buyer.model.BuyerProfile;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsMessageMapper;
import cn.net.rjnetwork.xianyu.manager.cs.mapper.AiCsSessionMapper;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsMessage;
import cn.net.rjnetwork.xianyu.manager.cs.model.AiCsSession;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 买家画像服务 — 跨会话聚合买家行为、成交、议价、可信度评分
 */
@Service
public class BuyerProfileService {

    private static final Logger logger = LoggerFactory.getLogger(BuyerProfileService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BuyerProfileMapper profileMapper;
    private final AiCsSessionMapper sessionMapper;
    private final AiCsMessageMapper messageMapper;

    public BuyerProfileService(BuyerProfileMapper profileMapper, AiCsSessionMapper sessionMapper, AiCsMessageMapper messageMapper) {
        this.profileMapper = profileMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    /** 查找或创建 */
    public BuyerProfile findOrCreate(String buyerId, String nickname) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p != null) return p;
        p = new BuyerProfile();
        p.setBuyerId(buyerId);
        p.setNickname(nickname);
        p.setTotalSessions(0L);
        p.setTotalMessages(0L);
        p.setTotalOrders(0L);
        p.setTotalSpent(0.0);
        p.setBargainCount(0L);
        p.setCredibilityScore(50.0);
        p.setTags("[]");
        p.setDeleted(0);
        profileMapper.insert(p);
        return p;
    }

    /** 有消息（来自买家）到达时更新画像 */
    public void onIncomingMessage(String buyerId, String nickname) {
        BuyerProfile p = findOrCreate(buyerId, nickname);
        p.setNickname(nickname);
        p.setTotalMessages(p.getTotalMessages() + 1);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 新会话时更新画像 */
    public void onNewSession(String buyerId, String nickname, Long accountId) {
        BuyerProfile p = findOrCreate(buyerId, nickname);
        if (p.getFirstAccountId() == null) p.setFirstAccountId(accountId);
        p.setTotalSessions(p.getTotalSessions() + 1);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 标记一次议价 */
    public void onBargaining(String buyerId) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p == null) return;
        p.setBargainCount(p.getBargainCount() + 1);
        // 议价次数多 → 可信度微降
        if (p.getBargainCount() > 10) {
            p.setCredibilityScore(Math.max(10, p.getCredibilityScore() - 5));
        }
        autoRefreshTags(p);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 成交回调 */
    public void onDeal(String buyerId, double amount) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p == null) return;
        p.setTotalOrders(p.getTotalOrders() + 1);
        p.setTotalSpent(p.getTotalSpent() + amount);
        // 成交次数多 → 可信度上升
        p.setCredibilityScore(Math.min(100, p.getCredibilityScore() + 10));
        autoRefreshTags(p);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 丢单回调 */
    public void onDealLost(String buyerId) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p == null) return;
        p.setCredibilityScore(Math.max(0, p.getCredibilityScore() - 3));
        autoRefreshTags(p);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 设置运营标签（追加） */
    @Transactional
    public void addTag(String buyerId, String newTag) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p == null) return;
        Set<String> tags = parseTags(p.getTags());
        tags.add(newTag);
        try {
            p.setTags(MAPPER.writeValueAsString(new ArrayList<>(tags)));
        } catch (JsonProcessingException e) {
            logger.warn("addTag failed", e);
        }
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 移除运营标签 */
    @Transactional
    public void removeTag(String buyerId, String tag) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p == null) return;
        Set<String> tags = parseTags(p.getTags());
        tags.remove(tag);
        try {
            p.setTags(MAPPER.writeValueAsString(new ArrayList<>(tags)));
        } catch (JsonProcessingException e) {
            logger.warn("removeTag failed", e);
        }
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    public void setNotes(String buyerId, String notes) {
        BuyerProfile p = profileMapper.selectByBuyerId(buyerId);
        if (p == null) return;
        p.setNotes(notes);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
    }

    /** 自动根据数据打标签 */
    private void autoRefreshTags(BuyerProfile p) {
        Set<String> tags = parseTags(p.getTags());
        if (p.getCredibilityScore() >= 80) tags.add("高可信度");
        if (p.getCredibilityScore() < 30) tags.add("低可信度");
        if (p.getBargainCount() > 5) tags.add("高频议价");
        if (p.getTotalOrders() > 0) tags.add("成交过");
        if (p.getTotalSpent() > 500) tags.add("高消费");
        try {
            p.setTags(MAPPER.writeValueAsString(new ArrayList<>(tags)));
        } catch (JsonProcessingException ignored) {}
    }

    private Set<String> parseTags(String json) {
        if (json == null || json.isBlank()) return new LinkedHashSet<>();
        try {
            return new LinkedHashSet<>(Arrays.asList(MAPPER.readValue(json, String[].class)));
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    public List<BuyerProfile> list(int page, int size, String keyword) {
        LambdaQueryWrapper<BuyerProfile> w = new LambdaQueryWrapper<BuyerProfile>()
                .eq(BuyerProfile::getDeleted, 0)
                .orderByDesc(BuyerProfile::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(BuyerProfile::getNickname, keyword)
                    .or().like(BuyerProfile::getBuyerId, keyword));
        }
        w.last("LIMIT " + size + " OFFSET " + ((long) page * size));
        return profileMapper.selectList(w);
    }

    public BuyerProfile get(String buyerId) {
        return profileMapper.selectByBuyerId(buyerId);
    }

    public long count(String keyword) {
        LambdaQueryWrapper<BuyerProfile> w = new LambdaQueryWrapper<BuyerProfile>()
                .eq(BuyerProfile::getDeleted, 0);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(BuyerProfile::getNickname, keyword)
                    .or().like(BuyerProfile::getBuyerId, keyword));
        }
        return profileMapper.selectCount(w);
    }
}
