package cn.net.rjnetwork.xianyu.manager.product.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.audit.annotation.Audit;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品擦亮服务 — 调 SDK {@code polishItem} 提升商品曝光排名。
 * <p>支持单擦、批量擦、按账号擦亮其全部在架商品。规则引擎的「擦亮」动作最终也走这里。</p>
 * <p>「超级擦亮」= 同一商品在短时间内连续多次 polish（间隔 ≥ 60s 防风控），
 * 用于顶到搜索结果前列。本服务提供 {@link #superPolish} 实现。</p>
 */
@Service
public class PolishService {

    private static final Logger log = LoggerFactory.getLogger(PolishService.class);

    /** 超级擦亮默认次数（间隔 60s，防止触发风控） */
    private static final int SUPER_POLISH_TIMES = 3;
    private static final long SUPER_POLISH_INTERVAL_MS = 60_000L;

    private final AccountMapper accountMapper;

    public PolishService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** 单擦：擦亮一个商品 */
    @Audit("擦亮商品")
    public Map<String, Object> polish(Long accountId, String itemId) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = newApiFacade(acc);
        JsonNode resp = api.polishItem(itemId);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("itemId", itemId);
        ret.put("success", !isRisk(resp));
        ret.put("response", resp);
        ret.put("polishedAt", LocalDateTime.now().toString());
        log.info("[POLISH] account={} item={} success={}", accountId, itemId, ret.get("success"));
        return ret;
    }

    /** 批量擦亮多个商品 */
    @Audit("批量擦亮")
    public Map<String, Object> batchPolish(Long accountId, java.util.List<String> itemIds) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = newApiFacade(acc);
        int ok = 0, fail = 0;
        for (String itemId : itemIds) {
            try {
                JsonNode resp = api.polishItem(itemId);
                if (!isRisk(resp)) ok++; else fail++;
            } catch (Exception e) {
                fail++;
                log.warn("[POLISH] batch item {} failed: {}", itemId, e.getMessage());
            }
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("total", itemIds.size());
        ret.put("success", ok);
        ret.put("failed", fail);
        log.info("[POLISH] batch account={} ok={} fail={}", accountId, ok, fail);
        return ret;
    }

    /**
     * 超级擦亮：同一商品连续多次 polish，顶到搜索前列。
     * <p>注意间隔 60s 防风控；总耗时 = times * 60s，调用方应放异步线程。</p>
     */
    @Audit("超级擦亮")
    public Map<String, Object> superPolish(Long accountId, String itemId, Integer times) throws Exception {
        int n = times != null && times > 0 ? times : SUPER_POLISH_TIMES;
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = newApiFacade(acc);
        int ok = 0, fail = 0;
        for (int i = 0; i < n; i++) {
            try {
                JsonNode resp = api.polishItem(itemId);
                if (!isRisk(resp)) ok++; else fail++;
            } catch (Exception e) {
                fail++;
                log.warn("[POLISH] super round {} failed: {}", i + 1, e.getMessage());
            }
            if (i < n - 1) {
                try { Thread.sleep(SUPER_POLISH_INTERVAL_MS); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("accountId", accountId);
        ret.put("itemId", itemId);
        ret.put("times", n);
        ret.put("success", ok);
        ret.put("failed", fail);
        log.info("[POLISH] super account={} item={} ok={}/{}", accountId, itemId, ok, n);
        return ret;
    }

    /** 风控判定：MTOP 返回 FAIL_SYS_USER_VALIDATE / punish 即为被拦截 */
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

    private XianyuApiFacade newApiFacade(XianyuAccount acc) {
        // XianyuApiFacade 构造器只接受 String cookie，内部自建 MtopApiClient；
        // imCookieHeader 需在构造后用 updateCookie 注入（facade 暂未暴露 setImCookieHeader，
        // 擦亮走 mtop.taobao.idle.item.polish，滑块 cookie 走 MtopApiClient 内部合并）
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        return api;
    }
}
