package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for async order-processing tasks.
 *
 * Why a custom pool instead of the default SimpleAsyncTaskExecutor?
 *   - SimpleAsyncTaskExecutor creates a new thread per task — no reuse, no backpressure.
 *   - ThreadPoolTaskExecutor bounds concurrency, queues excess work, and names threads
 *     so they show up meaningfully in stack dumps and thread monitors.
 *
 * Pool sizing guideline (CPU-bound vs I/O-bound):
 *   - CPU-bound: core = Runtime.availableProcessors()
 *   - I/O-bound (DB calls, HTTP calls): core = availableProcessors * 2 (or more)
 *   We default to 4 cores / 16 max — tune via app.async.* properties per environment.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "orderTaskExecutor")
    public Executor orderTaskExecutor(
            @Value("${app.async.core-pool-size:4}") int corePoolSize,
            @Value("${app.async.max-pool-size:16}") int maxPoolSize,
            @Value("${app.async.queue-capacity:500}") int queueCapacity,
            @Value("${app.async.thread-name-prefix:order-async-}") String prefix) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);

        // CallerRunsPolicy: if the queue is full, the calling thread executes the task.
        // This provides backpressure — slows down producers rather than dropping tasks.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("Order async executor initialized: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
