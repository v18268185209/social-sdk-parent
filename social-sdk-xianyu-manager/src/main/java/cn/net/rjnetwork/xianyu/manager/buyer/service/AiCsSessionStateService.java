package cn.net.rjnetwork.xianyu.manager.buyer.service;

import cn.net.rjnetwork.xianyu.manager.buyer.mapper.AiCsSessionStateMapper;
import cn.net.rjnetwork.xianyu.manager.buyer.model.AiCsSessionState;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 客服会话议价状态机
 */
@Service
public class AiCsSessionStateService {

    private final AiCsSessionStateMapper mapper;

    public AiCsSessionStateService(AiCsSessionStateMapper mapper) {
        this.mapper = mapper;
    }

    public AiCsSessionState getOrCreate(Long sessionId) {
        AiCsSessionState s = mapper.selectBySessionId(sessionId);
        if (s != null) return s;
        s = new AiCsSessionState();
        s.setSessionId(sessionId);
        s.setBargainRound(0);
        s.setDealClosed(false);
        s.setDeleted(0);
        mapper.insert(s);
        return s;
    }

    public AiCsSessionState get(Long sessionId) {
        return mapper.selectBySessionId(sessionId);
    }

    /** 买家议价一次 */
    public void incrementBargainRound(Long sessionId) {
        AiCsSessionState s = getOrCreate(sessionId);
        s.setBargainRound(s.getBargainRound() + 1);
        s.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(s);
    }

    public void setOriginalPrice(Long sessionId, double price) {
        AiCsSessionState s = getOrCreate(sessionId);
        if (s.getOriginalPrice() == null) {
            s.setOriginalPrice(price);
            s.setCurrentOffer(price);
            s.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(s);
        }
    }

    public void setCurrentOffer(Long sessionId, double price) {
        AiCsSessionState s = getOrCreate(sessionId);
        s.setCurrentOffer(price);
        s.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(s);
    }

    public void updateLowestOffer(Long sessionId, double price) {
        AiCsSessionState s = getOrCreate(sessionId);
        if (s.getLowestOffer() == null || price < s.getLowestOffer()) {
            s.setLowestOffer(price);
            s.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(s);
        }
    }

    public void closeDeal(Long sessionId, String reason) {
        AiCsSessionState s = getOrCreate(sessionId);
        s.setDealClosed(true);
        s.setClosedReason(reason);
        s.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(s);
    }

    public void save(AiCsSessionState state) {
        state.setUpdatedAt(LocalDateTime.now());
        if (state.getId() == null) {
            mapper.insert(state);
        } else {
            mapper.updateById(state);
        }
    }
}
