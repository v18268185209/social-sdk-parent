package cn.net.rjnetwork.xianyu.manager.wallet.service;

import cn.net.rjnetwork.xianyu.api.XianyuMtopApiClient;
import cn.net.rjnetwork.xianyu.api.XianyuWalletApiService;
import cn.net.rjnetwork.xianyu.manager.account.mapper.AccountMapper;
import cn.net.rjnetwork.xianyu.manager.account.model.XianyuAccount;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.mapper.WalletTransactionMapper;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWallet;
import cn.net.rjnetwork.xianyu.manager.wallet.model.XianyuWalletTransaction;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钱包同步服务 - 按账号从闲鱼 API 拉取钱包（余额/账单/绑定账户）并同步到本地 DB
 *
 * <p>与 OrderSyncService 同范式：accountMapper 取账号 → 校验 cookie → 构造
 * XianyuMtopApiClient → XianyuWalletApiService → 解析 JSON → 按唯一键 upsert。</p>
 *
 * <p>由于闲鱼钱包真实 MTOP 接口名尚未在 PC Web 网关抓到（钱包为移动端功能），
 * 这里的解析做了<b>强容错</b>：遍历多种常见字段名，命中即落库；未命中也不报错。
 * 配合 {@link #debugRawResponse(Long)} 与 {@link #probe(Long, String, String)} 可在线排查真实结构。</p>
 */
@Service
public class WalletSyncService {

    private final AccountMapper accountMapper;
    private final WalletMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;

    public WalletSyncService(AccountMapper accountMapper, WalletMapper walletMapper,
                             WalletTransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.walletMapper = walletMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 同步指定账号的钱包（余额 + 账单 + 绑定账户）
     */
    @Transactional
    public SyncResult syncWallet(Long accountId) {
        SyncResult result = new SyncResult();
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            result.success = false;
            result.message = "账号不存在";
            return result;
        }
        if (account.getCookieHeader() == null || account.getCookieHeader().isBlank()) {
            result.success = false;
            result.message = "账号未设置 Cookie";
            return result;
        }

        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuWalletApiService walletApi = new XianyuWalletApiService(mtopClient);

            // 1) 余额
            JsonNode balResp = walletApi.getBalance();
            result.balanceRet = retOf(balResp);
            XianyuWallet wallet = parseBalance(balResp, accountId);
            if (wallet != null) {
                upsertWallet(wallet);
                result.balanceSynced = true;
            }

            // 2) 绑定账户（支付宝 / 银行卡）
            JsonNode cardResp = walletApi.getBankCards();
            result.bankRet = retOf(cardResp);
            if (wallet != null) {
                applyBankCards(cardResp, wallet);
                upsertWallet(wallet);
            }

            // 3) 账单流水
            JsonNode billResp = walletApi.getBillList("1", "50");
            result.billRet = retOf(billResp);
            List<XianyuWalletTransaction> txns = parseBills(billResp, accountId);
            upsertTransactions(txns, accountId);
            result.billCount = txns.size();

            boolean anyOk = result.balanceSynced || result.billCount > 0
                    || (result.bankRet != null && result.bankRet.startsWith("SUCCESS"));
            result.success = anyOk;
            if (!result.success) {
                result.message = "接口未返回有效数据（多半是接口名不正确）：balanceRet="
                        + result.balanceRet + ", billRet=" + result.billRet + ", bankRet=" + result.bankRet;
            } else {
                result.message = "同步完成";
            }
            result.syncedAt = LocalDateTime.now();
            return result;
        } catch (Exception e) {
            result.success = false;
            result.message = "同步钱包失败: " + e.getMessage();
            return result;
        }
    }

    /**
     * 调试：返回余额/账单/绑定账户三个 API 的原始返回，便于确认真实结构。
     */
    public Map<String, Object> debugRawResponse(Long accountId) {
        Map<String, Object> debug = new HashMap<>();
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            debug.put("error", "账号不存在");
            return debug;
        }
        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            XianyuWalletApiService walletApi = new XianyuWalletApiService(mtopClient);
            JsonNode bal = walletApi.getBalance();
            JsonNode bills = walletApi.getBillList("1", "50");
            JsonNode cards = walletApi.getBankCards();
            debug.put("balance_ret", retOf(bal));
            debug.put("balance_raw", bal);
            debug.put("bill_ret", retOf(bills));
            debug.put("bill_raw", bills);
            debug.put("bank_ret", retOf(cards));
            debug.put("bank_raw", cards);
        } catch (Exception e) {
            debug.put("exception", e.getMessage());
        }
        return debug;
    }

    /**
     * 探测：用任意 (api, version) 直接打 MTOP，返回原始响应。
     * 用于在不重新编译的情况下验证候选接口名（拿到真实名后填进 application.yml 即可）。
     */
    public JsonNode probe(Long accountId, String api, String version) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getCookieHeader() == null) return null;
        try {
            XianyuMtopApiClient mtopClient = new XianyuMtopApiClient(account.getCookieHeader());
            return mtopClient.callMtop(api, version, "{}");
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== 解析（强容错） =====================

    private XianyuWallet parseBalance(JsonNode resp, Long accountId) {
        if (!isSuccess(resp)) return null;
        JsonNode data = resp.get("data");
        if (data == null || data.isNull()) return null;

        XianyuWallet w = new XianyuWallet();
        w.setAccountId(accountId);
        // 余额相关字段：尝试多种常见命名
        w.setBalance(big(data, "balance", "availableAmount", "cash", "amount", "totalAmount", "usableAmount"));
        w.setFrozenAmount(big(data, "frozenAmount", "freezeAmount", "frozen", "freeze"));
        w.setAvailableBalance(big(data, "availableBalance", "availableAmount", "usableAmount", "balance"));
        w.setTotalAssets(big(data, "totalAssets", "totalAmount", "assets", "totalBalance"));
        w.setWithdrawableAmount(big(data, "withdrawableAmount", "withdrawAmount", "canWithdraw"));
        w.setAlipayAccount(text(data, "alipayAccount", "alipay", "aliPayAccount"));
        w.setAlipayRealName(text(data, "alipayRealName", "realName", "alipayName"));
        return w;
    }

    private void applyBankCards(JsonNode resp, XianyuWallet wallet) {
        if (!isSuccess(resp)) return;
        JsonNode data = resp.get("data");
        if (data == null || data.isNull()) return;

        // 绑定的支付宝账号/实名
        if (wallet.getAlipayAccount() == null)
            wallet.setAlipayAccount(text(data, "alipayAccount", "alipay", "aliPayAccount", "accountNo"));
        if (wallet.getAlipayRealName() == null)
            wallet.setAlipayRealName(text(data, "alipayRealName", "realName", "alipayName"));

        // 银行卡列表
        JsonNode list = data.path("bankCardList").path("list");
        if (!list.isArray()) list = data.path("bankCardList");
        if (!list.isArray()) list = data.path("cards");
        if (!list.isArray()) list = data.path("list");
        if (list.isArray() && list.size() > 0) {
            JsonNode card = list.get(0);
            wallet.setBankCard(text(card, "bankName", "bank", "name") + maskNum(card));
        }
    }

    private List<XianyuWalletTransaction> parseBills(JsonNode resp, Long accountId) {
        List<XianyuWalletTransaction> list = new ArrayList<>();
        if (!isSuccess(resp)) return list;
        JsonNode data = resp.get("data");
        if (data == null || data.isNull()) return list;

        JsonNode items = data.path("items");
        if (!items.isArray()) items = data.path("list");
        if (!items.isArray()) items = data.path("records");
        if (!items.isArray()) return list;

        for (JsonNode it : items) {
            XianyuWalletTransaction t = new XianyuWalletTransaction();
            t.setAccountId(accountId);
            t.setTransactionId(text(it, "bizOrderId", "orderId", "id", "tradeId", "billId"));
            t.setTradeNo(text(it, "tradeNo", "tradeId", "serialNo", "flowNo"));
            t.setBizType(text(it, "bizType", "type", "bizTypeName", "category"));
            String biz = t.getBizType();
            // 兜底大类映射
            if (biz != null) {
                if (biz.contains("收入") || biz.contains("收款") || biz.contains("退款")) t.setType("INCOME");
                else if (biz.contains("提现") || biz.contains("支出") || biz.contains("消费")) t.setType("EXPENSE");
                else t.setType("TRANSFER");
            } else {
                t.setType("EXPENSE");
            }
            t.setAmount(big(it, "amount", "fee", "money", "transAmount", "tradeAmount"));
            t.setBalanceAfter(big(it, "balance", "balanceAfter", "afterBalance"));
            t.setDescription(text(it, "desc", "description", "title", "memo", "remark"));
            t.setStatus(text(it, "status", "statusDesc", "state"));
            t.setTransactionTime(parseTime(text(it, "gmtCreate", "createTime", "time", "transTime", "bizTime")));
            if (t.getTransactionId() != null || t.getTradeNo() != null) list.add(t);
        }
        return list;
    }

    // ===================== 持久化 =====================

    private void upsertWallet(XianyuWallet w) {
        LambdaQueryWrapper<XianyuWallet> qw = new LambdaQueryWrapper<>();
        qw.eq(XianyuWallet::getAccountId, w.getAccountId());
        XianyuWallet existing = walletMapper.selectOne(qw);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            w.setCreatedAt(now);
            w.setUpdatedAt(now);
            walletMapper.insert(w);
        } else {
            w.setId(existing.getId());
            w.setCreatedAt(existing.getCreatedAt());
            w.setUpdatedAt(now);
            walletMapper.updateById(w);
        }
    }

    private void upsertTransactions(List<XianyuWalletTransaction> txns, Long accountId) {
        for (XianyuWalletTransaction t : txns) {
            LambdaQueryWrapper<XianyuWalletTransaction> qw = new LambdaQueryWrapper<>();
            qw.eq(XianyuWalletTransaction::getAccountId, accountId);
            if (t.getTransactionId() != null) qw.eq(XianyuWalletTransaction::getTransactionId, t.getTransactionId());
            else if (t.getTradeNo() != null) qw.eq(XianyuWalletTransaction::getTradeNo, t.getTradeNo());
            else continue;
            qw.last("LIMIT 1");
            XianyuWalletTransaction existing = transactionMapper.selectOne(qw);
            LocalDateTime now = LocalDateTime.now();
            if (existing != null) {
                existing.setBizType(t.getBizType());
                existing.setType(t.getType());
                existing.setAmount(t.getAmount());
                existing.setBalanceAfter(t.getBalanceAfter());
                existing.setDescription(t.getDescription());
                existing.setStatus(t.getStatus());
                existing.setTransactionTime(t.getTransactionTime());
                existing.setUpdatedAt(now);
                transactionMapper.updateById(existing);
            } else {
                t.setCreatedAt(now);
                t.setUpdatedAt(now);
                transactionMapper.insert(t);
            }
        }
    }

    // ===================== 工具 =====================

    private boolean isSuccess(JsonNode resp) {
        if (resp == null) return false;
        String r0 = retOf(resp);
        return r0 != null && r0.startsWith("SUCCESS");
    }

    private String retOf(JsonNode resp) {
        if (resp == null) return "NULL_RESPONSE";
        JsonNode ret = resp.path("ret");
        if (ret.isArray() && ret.size() > 0) return ret.get(0).asText();
        if (resp.has("ret") && resp.get("ret").isTextual()) return resp.get("ret").asText();
        return null;
    }

    private String maskNum(JsonNode card) {
        String num = text(card, "bankCardNo", "cardNo", "cardNumber", "no");
        if (num == null || num.isEmpty()) return "";
        if (num.length() > 4) return "(" + num.substring(num.length() - 4) + ")";
        return "(" + num + ")";
    }

    private BigDecimal big(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                try {
                    if (v.isNumber()) return v.decimalValue();
                    String s = v.asText();
                    if (s != null && !s.isEmpty()) return new BigDecimal(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String text(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                String s = v.asText();
                if (s != null && !s.isEmpty()) return s;
            }
        }
        return null;
    }

    private LocalDateTime parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        for (java.time.format.DateTimeFormatter fmt : new java.time.format.DateTimeFormatter[]{
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        }) {
            try { return LocalDateTime.parse(s, fmt); } catch (Exception ignored) {}
        }
        try { return LocalDateTime.parse(s.substring(0, Math.min(s.length(), 19)).replace("T", " "),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); } catch (Exception ignored) {}
        return null;
    }

    /**
     * 同步结果
     */
    public static class SyncResult {
        public boolean success;
        public String message;
        public boolean balanceSynced;
        public int billCount;
        public String balanceRet;
        public String billRet;
        public String bankRet;
        public LocalDateTime syncedAt;

        public static SyncResult error(String message) {
            SyncResult r = new SyncResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }
}
