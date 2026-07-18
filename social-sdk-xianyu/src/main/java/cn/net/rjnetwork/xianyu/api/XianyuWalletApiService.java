package cn.net.rjnetwork.xianyu.api;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 闲鱼钱包/资金 API 服务
 * 封装余额查询、提现、账单、充值等 MTOP 接口调用
 *
 * <p>这些接口在用户进入"我的钱包"页面时触发。</p>
 */
public class XianyuWalletApiService {

    private final XianyuMtopApiClient apiClient;

    public XianyuWalletApiService(XianyuMtopApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取余额信息
     * API: mtop.taobao.idlewallet.balance.get
     *
     * @return 余额信息 JSON
     */
    public JsonNode getBalance() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlewallet.balance.get")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取交易账单
     * API: mtop.taobao.idlewallet.bill.list
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @return 账单列表 JSON
     */
    public JsonNode getBillList(String page, String pageSize) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlewallet.bill.list")
                .addParam("page", page != null ? page : "1")
                .addParam("pageSize", pageSize != null ? pageSize : "20")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 提现
     * API: mtop.taobao.idlewallet.withdraw
     *
     * @param amount 提现金额
     * @return 提现结果 JSON
     */
    public JsonNode withdraw(String amount) {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlewallet.withdraw")
                .addParam("amount", amount)
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }

    /**
     * 获取绑定的银行卡列表
     * API: mtop.taobao.idlewallet.bankcard.list
     *
     * @return 银行卡列表 JSON
     */
    public JsonNode getBankCards() {
        String url = new XianyuMtopRequestBuilder("mtop.taobao.idlewallet.bankcard.list")
                .setCookie(apiClient.getCookie())
                .buildUrl();
        return apiClient.post(url, "{}");
    }
}
