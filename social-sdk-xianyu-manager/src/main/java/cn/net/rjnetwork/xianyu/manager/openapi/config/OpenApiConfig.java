package cn.net.rjnetwork.xianyu.manager.openapi.config;

import cn.net.rjnetwork.xianyu.manager.openapi.security.OpenApiAuthInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 对外 OpenAPI 配置：注册鉴权拦截器 + 生成文档（仅扫描 openapi.controller 包）。
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    private final OpenApiAuthInterceptor openApiAuthInterceptor;

    public OpenApiConfig(OpenApiAuthInterceptor openApiAuthInterceptor) {
        this.openApiAuthInterceptor = openApiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiAuthInterceptor)
                .addPathPatterns("/openapi/v1/**")
                .excludePathPatterns(
                        "/openapi/v1/oauth/token",
                        "/openapi/v1/openapi.json",
                        "/openapi/v1/docs",
                        "/openapi/v1/docs/**");
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI鱼多宝 对外 OpenAPI")
                        .version("v1")
                        .description("覆盖账号、商品、消息、订单、钱包、AI、通知等全部能力的对外接口。"
                                + "调用前请用 appKey/appSecret 在 /openapi/v1/oauth/token 换取 Bearer 令牌。"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
