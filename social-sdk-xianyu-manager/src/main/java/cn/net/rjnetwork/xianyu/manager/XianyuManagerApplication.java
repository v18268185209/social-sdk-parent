^package cn.net.rjnetwork.xianyu.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
        "cn.net.rjnetwork.xianyu.manager",
        "cn.net.rjnetwork.xianyu.captcha"
})
@EnableScheduling
@EnableAsync
@EnableCaching
public class XianyuManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(XianyuManagerApplication.class, args);
    }
}
