package cn.net.rjnetwork.xianyu.manager.collect.service;

import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.collect.mapper.CollectMapper;
import cn.net.rjnetwork.xianyu.manager.collect.model.XianyuCollect;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CollectService {

    private static final Logger log = LoggerFactory.getLogger(CollectService.class);

    private final CollectMapper collectMapper;
    private final AccountMapper accountMapper;

    public CollectService(CollectMapper collectMapper, AccountMapper accountMapper) {
        this.collectMapper = collectMapper;
        this.accountMapper = accountMapper;
    }

    public List<XianyuCollect> list(Long accountId, String targetType) {
        LambdaQueryWrapper<XianyuCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuCollect::getAccountId, accountId);
        if (targetType != null && !targetType.isBlank()) {
            wrapper.eq(XianyuCollect::getTargetType, targetType);
        }
        wrapper.orderByDesc(XianyuCollect::getCollectedAt);
        return collectMapper.selectList(wrapper);
    }

    public XianyuCollect getById(Long id) {
        return collectMapper.selectById(id);
    }

    public void add(XianyuCollect collect) {
        collect.setCreatedAt(LocalDateTime.now());
        collect.setUpdatedAt(LocalDateTime.now());
        collectMapper.insert(collect);
    }

    public void remove(Long id) {
        collectMapper.deleteById(id);
    }

    // ======================== SDK 联动：闲鱼侧收藏同步 ========================

    /**
     * 调 SDK collectItem 把闲鱼侧商品加入收藏，同时落本地记录
     */
    public XianyuCollect collectAndSync(Long accountId, String itemId, String itemName) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.collectItem(itemId);
        if (isRisk(resp)) {
            throw new IllegalStateException("闲鱼收藏失败（可能触发风控）: " + truncate(resp.toString(), 200));
        }
        // 检查业务返回码
        String ret = retStr(resp);
        if (ret != null && ret.contains("SUCCESS")) {
            // 不去重直接插入，让数据库唯一索引或后续 sync 负责去重
            // 但为了安全还是查一下
            if (exists(accountId, itemId)) {
                log.info("[COLLECT] account={} item={} already in local db, skip", accountId, itemId);
                return listByAccountAndTarget(accountId, itemId);
            }
        }
        XianyuCollect c = new XianyuCollect();
        c.setAccountId(accountId);
        c.setTargetType("ITEM");
        c.setTargetId(itemId);
        c.setTargetName(itemName);
        c.setCollectedAt(LocalDateTime.now());
        add(c);
        log.info("[COLLECT] account={} item={} collected via SDK", accountId, itemId);
        return c;
    }

    /**
     * 调 SDK uncollectItem 取消闲鱼侧收藏，同时删本地记录
     */
    public void uncollectAndSync(Long accountId, String itemId) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.uncollectItem(itemId);
        if (isRisk(resp)) {
            throw new IllegalStateException("闲鱼取消收藏失败: " + truncate(resp.toString(), 200));
        }
        // 删本地记录
        LambdaQueryWrapper<XianyuCollect> w = new LambdaQueryWrapper<>();
        w.eq(XianyuCollect::getAccountId, accountId).eq(XianyuCollect::getTargetId, itemId);
        int deleted = collectMapper.delete(w);
        log.info("[COLLECT] account={} item={} uncollected via SDK, deleted={}", accountId, itemId, deleted);
    }

    /**
     * 调 SDK getMyCollectList 拉闲鱼侧收藏列表，回填本地（去重 by accountId+targetId）
     */
    public int syncFromXianyu(Long accountId, int page, int pageSize) throws Exception {
        XianyuAccount acc = requireAccount(accountId);
        XianyuApiFacade api = new XianyuApiFacade(acc.getCookieHeader());
        JsonNode resp = api.getMyCollectList(String.valueOf(page), String.valueOf(pageSize));
        if (isRisk(resp)) {
            throw new IllegalStateException("闲鱼拉收藏列表失败: " + truncate(resp.toString(), 200));
        }
        int synced = 0;
        // 多层兼容响应格式：data.items[] / items[] / data[]
        JsonNode items = resp.path("data").path("items");
        if (items.isMissingNode() || !items.isArray()) {
            items = resp.path("items");
        }
        if (items.isMissingNode() || !items.isArray()) {
            items = resp.path("data");
        }
        if (items.isArray()) {
            for (JsonNode it : items) {
                String itemId = it.path("itemId").asText(it.path("id").asText(it.path("longItemId").asText("")));
                if (itemId.isBlank()) continue;
                if (exists(accountId, itemId)) continue;
                XianyuCollect c = new XianyuCollect();
                c.setAccountId(accountId);
                c.setTargetType("ITEM");
                c.setTargetId(itemId);
                c.setTargetName(it.path("title").asText(it.path("itemTitle").asText("")));
                c.setCollectedAt(LocalDateTime.now());
                add(c);
                synced++;
            }
        }
        log.info("[COLLECT] syncFromXianyu account={} synced={}", accountId, synced);
        return synced;
    }

    /**
     * 同步所有账号的收藏列表（为定时任务准备）
     */
    public int syncAllAccounts() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        int total = 0;
        for (XianyuAccount acc : accounts) {
            if (acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) continue;
            try {
                total += syncFromXianyu(acc.getId(), 1, 50);
            } catch (Exception e) {
                log.warn("[COLLECT] syncFromXianyu failed for account={}: {}", acc.getId(), e.getMessage());
            }
        }
        return total;
    }

    // ======================== 内部工具方法 ========================

    private boolean exists(Long accountId, String targetId) {
        LambdaQueryWrapper<XianyuCollect> w = new LambdaQueryWrapper<>();
        w.eq(XianyuCollect::getAccountId, accountId).eq(XianyuCollect::getTargetId, targetId);
        return collectMapper.selectCount(w) > 0;
    }

    private XianyuCollect listByAccountAndTarget(Long accountId, String targetId) {
        LambdaQueryWrapper<XianyuCollect> w = new LambdaQueryWrapper<>();
        w.eq(XianyuCollect::getAccountId, accountId).eq(XianyuCollect::getTargetId, targetId);
        return collectMapper.selectOne(w);
    }

    private boolean isRisk(JsonNode resp) {
        if (resp == null) return false;
        String s = resp.toString();
        return s.contains("FAIL_SYS_USER_VALIDATE") || s.contains("punish") || s.contains("RGV587");
    }

    private String retStr(JsonNode resp) {
        if (resp == null) return null;
        JsonNode ret = resp.path("ret");
        if (!ret.isArray() || ret.size() == 0) return null;
        return ret.get(0).asText("");
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "..." : s);
    }

    private XianyuAccount requireAccount(Long accountId) {
        XianyuAccount acc = accountMapper.selectById(accountId);
        if (acc == null || acc.getCookieHeader() == null || acc.getCookieHeader().isBlank()) {
            throw new IllegalArgumentException("Account not found or cookie expired, accountId=" + accountId);
        }
        return acc;
    }
}
