package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 闲鱼钱包/资金 API 服务
 * 封装余额查询、账单、银行卡、提现等 MTOP 接口调用。
 *
 * <p><b>重要：接口名是可运行期覆盖的。</b></p>
 * 闲鱼钱包是移动端功能，PC Web 网关（h5api.m.goofish.com / appKey 34839810）未直接暴露，
 * 真实 MTOP 接口名需从已登录浏览器的 DevTools → Network 抓取（或 CDP 抓包）。
 * 因此这里把接口名做成静态可覆盖字段：应用启动时由 manager 的
 * {@code XianyuWalletApiConfig} 从 {@code application.yml} 的 {@code xianyu.wallet.*-api}
 * 注入，无需重新编译 SDK 即可切换真实接口名。
 *
 * <p>当前默认值是占位候选（PC Web 命名规律），在拿到真实接口名前调用会返回
 * {@code FAIL_SYS_API_NOT_FOUNDED}。通过 {@link #configure(String, String, String, String)} 覆盖即可。</p>
 */
public class XianyuWalletApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final XianyuMtopApiClient apiClient;

    // ===== 可覆盖的接口名（运行时由 application.yml 注入） =====
    /** 余额查询 — 占位候选，待替换为真实接口名 */
    public static String API_BALANCE = "mtop.idle.web.wallet.balance.get";
    /** 账单列表 — 占位候选 */
    public static String API_BILL = "mtop.idle.web.wallet.bill.list";
    /** 银行卡/绑定账户 — 占位候选 */
    public static String API_BANKCARD = "mtop.idle.web.wallet.bankcard.list";
    /** 提现 — 占位候选 */
    public static String API_WITHDRAW = "mtop.idle.web.wallet.withdraw";

    /** 各接口的版本号（部分接口需要非 1.0 版本） */
    public static String VERSION_BALANCE = "1.0";
    public static String VERSION_BILL = "1.0";
    public static String VERSION_BANKCARD = "1.0";
    public static String VERSION_WITHDRAW = "1.0";

    /**
     * 运行时覆盖接口名（由 manager 启动配置调用）。传 null 的字段保持原值。
     */
    public static void configure(String balance, String bill, String bankcard, String withdraw) {
        if (balance != null && !balance.isBlank()) API_BALANCE = balance;
        if (bill != null && !bill.isBlank()) API_BILL = bill;
        if (bankcard != null && !bankcard.isBlank()) API_BANKCARD = bankcard;
        if (withdraw != null && !withdraw.isBlank()) API_WITHDRAW = withdraw;
    }

    public XianyuWalletApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 获取余额信息 */
    public JsonNode getBalance() {
        return apiClient.callMtop(API_BALANCE, VERSION_BALANCE, "{}");
    }

    /** 获取交易账单 */
    public JsonNode getBillList(String page, String pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page != null ? page : "1");
        data.put("pageSize", pageSize != null ? pageSize : "50");
        return apiClient.callMtop(API_BILL, VERSION_BILL, toJson(data));
    }

    /** 提现 */
    public JsonNode withdraw(String amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount != null ? amount : "");
        return apiClient.callMtop(API_WITHDRAW, VERSION_WITHDRAW, toJson(data));
    }

    /** 获取绑定的银行卡/支付账户列表 */
    public JsonNode getBankCards() {
        return apiClient.callMtop(API_BANKCARD, VERSION_BANKCARD, "{}");
    }

    private static String toJson(Map<String, ?> map) {
        try { return MAPPER.writeValueAsString(map); } catch (Exception e) { return "{}"; }
    }
}
