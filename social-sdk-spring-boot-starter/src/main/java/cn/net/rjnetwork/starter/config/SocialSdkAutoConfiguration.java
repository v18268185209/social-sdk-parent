package cn.net.rjnetwork.starter.config;

import cn.net.rjnetwork.core.config.SocialConfig;
import cn.net.rjnetwork.core.provider.SocialProvider;
import cn.net.rjnetwork.starter.properties.SocialSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Social SDK自动配置类
 */
@Configuration
@ConditionalOnClass(SocialProvider.class)
@EnableConfigurationProperties(SocialSdkProperties.class)
public class SocialSdkAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SocialSdkAutoConfiguration.class);

    private final SocialSdkProperties properties;

    public SocialSdkAutoConfiguration(SocialSdkProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建SocialConfig Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SocialConfig socialConfig() {
        SocialConfig config = new SocialConfig();
        config.setEnabled(properties.isEnabled());
        config.setDefaultPlatform(properties.getDefaultPlatform());
        config.setTimeout(properties.getTimeout());
        config.setConnectTimeout(properties.getConnectTimeout());
        config.setReadTimeout(properties.getReadTimeout());

        // Proxy configuration
        if (properties.getProxy() != null && properties.getProxy().isEnabled()) {
            config.setProxyHost(properties.getProxy().getHost());
            config.setProxyPort(properties.getProxy().getPort());
            config.setProxyUsername(properties.getProxy().getUsername());
            config.setProxyPassword(properties.getProxy().getPassword());
        }

        logger.info("Social SDK configuration initialized: {}", config);
        return config;
    }

    /**
     * SocialProvider管理Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SocialProviderManager socialProviderManager(ObjectProvider<SocialProvider> socialProviders) {
        SocialProviderManager manager = new SocialProviderManager();
        socialProviders.orderedStream().forEach(manager::registerProvider);
        logger.info("SocialProviderManager initialized with {} providers", manager.getProviderCount());
        return manager;
    }

    /**
     * SocialProvider管理器
     * 用于管理和获取不同平台的Provider实例
     */
    public static class SocialProviderManager {

        private final Map<String, SocialProvider> providers = new HashMap<>();

        /**
         * 注册Provider
         */
        public void registerProvider(SocialProvider provider) {
            providers.put(provider.getPlatform().getCode(), provider);
            logger.info("Registered provider for platform: {}", provider.getPlatform());
        }

        /**
         * 获取指定平台的Provider
         */
        public SocialProvider getProvider(String platformCode) {
            return providers.get(platformCode);
        }

        /**
         * 获取指定平台的Provider
         */
        public SocialProvider getProvider(cn.net.rjnetwork.core.constant.SocialPlatform platform) {
            if (platform == null) {
                return null;
            }
            return providers.get(platform.getCode());
        }

        /**
         * 检查是否支持指定平台
         */
        public boolean supports(String platformCode) {
            return providers.containsKey(platformCode);
        }

        /**
         * 获取所有已注册的Provider
         */
        public Map<String, SocialProvider> getAllProviders() {
            return new HashMap<>(providers);
        }

        /**
         * 获取Provider数量
         */
        public int getProviderCount() {
            return providers.size();
        }
    }
}
