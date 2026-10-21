package com.qh.ai_agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * 设计要点：
 * 1. 独立线程池隔离：落库 / 统计不能和检索/生成抢线程
 * 2. 有界队列 + CallerRunsPolicy：宁可让调用方慢一点，也不丢任务
 * 3. 优雅停机：等在途任务执行完再退出（waitForTasksToCompleteOnShutdown）
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 落库 / 统计 / 评测采样等旁路任务 */
    @Bean("persistExecutor")
    public ThreadPoolTaskExecutor persistExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("persist-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(20);
        ex.initialize();
        log.info("[AsyncConfig] persistExecutor 初始化完成 core=4 max=8 queue=500");
        return ex;
    }

    /** 知识库重建 / 图谱构建等长任务 */
    @Bean("buildExecutor")
    public ThreadPoolTaskExecutor buildExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("build-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(60);
        ex.initialize();
        return ex;
    }
}
