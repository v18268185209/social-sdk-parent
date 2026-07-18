package cn.net.rjnetwork.xianyu.manager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 闲鱼多账号管理平台启动类
 */
@SpringBootApplication(scanBasePackages = "cn.net.rjnetwork")
@MapperScan("cn.net.rjnetwork.xianyu.manager.*.mapper")
@EnableScheduling
public class XianyuManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(XianyuManagerApplication.class, args);
    }
}
