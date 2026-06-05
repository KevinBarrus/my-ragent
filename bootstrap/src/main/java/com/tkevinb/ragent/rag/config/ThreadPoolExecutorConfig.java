package com.tkevinb.ragent.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池配置
 */
@Configuration
public class ThreadPoolExecutorConfig {

    @Bean
    public Executor modelStreamExecutor() {
        return new ThreadPoolExecutor(
                4,
                8,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
