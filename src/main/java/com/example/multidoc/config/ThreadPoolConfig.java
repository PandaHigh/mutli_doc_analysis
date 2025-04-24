package com.example.multidoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolTaskExecutor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 获取系统CPU核心数
        int processors = Runtime.getRuntime().availableProcessors();
        
        // 核心线程数：CPU核心数
        executor.setCorePoolSize(processors);
        
        // 最大线程数：CPU核心数的2倍（降低并发）
        executor.setMaxPoolSize(processors * 2);
        
        // 队列容量：1000
        executor.setQueueCapacity(1000);
        
        // 线程空闲时间：60秒
        executor.setKeepAliveSeconds(60);
        
        // 线程名前缀
        executor.setThreadNamePrefix("analysis-task-");
        
        // 拒绝策略：调用者运行策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间：60秒
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
} 