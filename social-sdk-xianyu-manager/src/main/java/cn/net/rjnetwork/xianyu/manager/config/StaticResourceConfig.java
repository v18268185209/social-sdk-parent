package cn.net.rjnetwork.xianyu.manager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射：把 /uploads/** 请求指向本地文件 ./data/uploads/。
 *
 * <p>spring.mvc.static-path-pattern 已被设为 /static/**（配合 SPA 兜底），
 * 所以默认的 /** 静态资源映射对 /uploads/** 不生效，需要显式注册一个 ResourceHandler。
 * 这样前端拿到的 /uploads/xxx.jpg 才能正常加载。</p>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./data/uploads/")
                .setCachePeriod(86400);
    }
}
