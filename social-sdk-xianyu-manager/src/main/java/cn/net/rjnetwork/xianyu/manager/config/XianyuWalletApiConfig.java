package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.api.XianyuWalletApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 将 application.yml 中的 xianyu.wallet.*-api 注入到 SDK 的
 * {@link XianyuWalletApiService} 静态接口名字段。
 *
 * <p>这样拿到真实钱包 MTOP 接口名后，只需改 yml 并重启，无需重新编译 SDK。</p>
 */
@Configuration
public class XianyuWalletApiConfig {

    @Value("${xianyu.wallet.balance-api:}")
    private String balanceApi;

    @Value("${xianyu.wallet.bill-api:}")
    private String billApi;

    @Value("${xianyu.wallet.bankcard-api:}")
    private String bankcardApi;

    @Value("${xianyu.wallet.withdraw-api:}")
    private String withdrawApi;

    @PostConstruct
    public void apply() {
        XianyuWalletApiService.configure(balanceApi, billApi, bankcardApi, withdrawApi);
    }
}
