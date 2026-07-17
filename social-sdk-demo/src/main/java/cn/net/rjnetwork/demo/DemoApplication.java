package cn.net.rjnetwork.demo;

import cn.net.rjnetwork.xianyu.service.XianyuCdpSessionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

    @Value("${cdp.endpoint:http://192.168.1.127:9333}")
    private String cdpEndpoint;

    @Value("${cdp.xianyu-url:https://www.goofish.com}")
    private String xianyuUrl;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public XianyuCdpSessionManager xianyuCdpSessionManager() {
        return new XianyuCdpSessionManager(cdpEndpoint, xianyuUrl);
    }
}
