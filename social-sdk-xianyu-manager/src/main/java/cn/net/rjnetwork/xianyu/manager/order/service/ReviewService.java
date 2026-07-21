package cn.net.rjnetwork.xianyu.manager.order.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评价与信用服务 — 调 SDK {@code reviewOrder / getReviewList / getUserCredit}。
 * <p>闭环链路：订单完成→评价(买家/卖家互评)→信用画像→影响后续交易决策。
 * 含退款评价（applyRefund/getRefundList/getRefundDetail）。</p>
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final AccountMapper accountMapper;

    public ReviewService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** 对指定订单发表评价（rating + content） */
    public Map<String, Object> reviewOrder(Long accountId, String orderId, String rating, String content) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.reviewOrder(orderId, rating, content);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("orderId", orderId);
        ret.put("success", !isRisk(resp));
        ret.put("response", resp);
        log.info("[REVIEW] account={} order={} rating={} success={}", accountId, orderId, rating, ret.get("success"));
        return ret;
    }

    /** 拉买家评价列表（buyerId=null 时拉自己收到的评价） */
    public JsonNode getReviewList(Long accountId, String buyerId, int page, int pageSize) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getReviewList(buyerId, String.valueOf(page), String.valueOf(pageSize));
    }

    /** 拉用户信用画像（ userId=null 时取自己） */
    public JsonNode getUserCredit(Long accountId, String userId) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getUserCredit(userId != null ? userId : acc.getUserId());
    }

    /** 申请退款 */
    public Map<String, Object> applyRefund(Long accountId, String orderId, String reason, String amount) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.applyRefund(orderId, reason, amount);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("orderId", orderId);
        ret.put("success", !isRisk(resp));
        ret.put("response", resp);
        return ret;
    }

    /** 退款列表 */
    public JsonNode getRefundList(Long accountId, String disputeStatus, int page, int pageSize) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getRefundList(disputeStatus, String.valueOf(page), String.valueOf(pageSize));
    }

    /** 退款详情 */
    public JsonNode getRefundDetail(Long accountId, String refundId) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api.getRefundDetail(refundId);
    }

    private boolean isRisk(JsonNode resp) {
        if (resp == null) return false;
        String s = resp.toString();
        return s.contains("FAIL_SYS_USER_VALIDATE") || s.contains("punish") || s.contains("RGV587");
    }

    private XianyuAccount requireAccount(Long accountId) {
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null || acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
            throw new IllegalArgumentException("Account not found or cookie expired, accountId=" + accountId);
        }
        return acc;
    }
}
