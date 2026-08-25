package com.example.compliance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.concurrent.*;

/**
 * 线程池配置
 * 为文档审查的并行处理提供线程池：
 * - 5 个审查器可以并行执行
 * - 使用 CompletableFuture 编排任务
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfig.class);

    @Bean("reviewExecutor")
    public ExecutorService reviewExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        Thread t = new Thread(r, "review-pool-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("审查线程池初始化完成，核心线程数: {}，最大线程数: {}", corePoolSize, maxPoolSize);
        return executor;
    }

    @Bean("ocrExecutor")
    public ExecutorService ocrExecutor() {
        int poolSize = 2;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(@NonNull Runnable r) {
                        Thread t = new Thread(r, "ocr-pool-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("OCR线程池初始化完成，线程数: {}", poolSize);
        return executor;
    }
}