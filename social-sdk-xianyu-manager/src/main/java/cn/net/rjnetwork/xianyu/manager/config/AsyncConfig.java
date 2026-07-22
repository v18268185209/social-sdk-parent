package cn.net.rjnetwork.xianyu.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 * 用于 {@link org.springframework.scheduling.annotation.Async} 注解，给同步商品等长耗时后台任务提供独立线程池，
 * 避免挤占 Tomcat 请求线程。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String SYNC_EXECUTOR = "syncTaskExecutor";

    @Bean(name = SYNC_EXECUTOR)
    public Executor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：保持常驻的线程
        executor.setCorePoolSize(4);
        // 最大线程数：高峰期可扩容到的上限
        executor.setMaxPoolSize(8);
        // 任务排队队列容量，超过队列+maxPoolSize 会走拒绝策略
        executor.setQueueCapacity(50);
        // 线程名前缀，方便日志里区分
        executor.setThreadNamePrefix("sync-task-");
        // 非核心线程空闲存活秒数
        executor.setKeepAliveSeconds(120);
        // 拒绝策略：由调用方线程跑（降级同步），保证任务一定被执行，不会丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待正在跑的任务跑完再关
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
