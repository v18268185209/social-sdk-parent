package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼钱包/资金 API 服务
 * 封装余额查询、提现、账单、充值等 MTOP 接口调用
 */
public class XianyuWalletApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    public XianyuWalletApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 获取余额信息 — mtop.taobao.idlewallet.balance.get */
    public JsonNode getBalance() {
        return apiClient.callMtop("mtop.taobao.idlewallet.balance.get", "{}");
    }

    /** 获取交易账单 — mtop.taobao.idlewallet.bill.list */
    public JsonNode getBillList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "20");
        return apiClient.callMtop("mtop.taobao.idlewallet.bill.list", toJson(data));
    }

    /** 提现 — mtop.taobao.idlewallet.withdraw */
    public JsonNode withdraw(String amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount != null ? amount : "");
        return apiClient.callMtop("mtop.taobao.idlewallet.withdraw", toJson(data));
    }

    /** 获取绑定的银行卡列表 — mtop.taobao.idlewallet.bankcard.list */
    public JsonNode getBankCards() {
        return apiClient.callMtop("mtop.taobao.idlewallet.bankcard.list", "{}");
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
