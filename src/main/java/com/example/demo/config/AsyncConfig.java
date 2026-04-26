package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configures a named thread pool for async order-processing tasks.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW @Async WORKS IN SPRING
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @EnableAsync activates Spring AOP support for @Async.
 * When you annotate a method with @Async("orderTaskExecutor"):
 *
 *   1. The caller invokes the method on the Spring proxy (not the real object).
 *   2. The proxy wraps the method body in a Runnable/Callable.
 *   3. The proxy submits the Runnable to the named Executor ("orderTaskExecutor").
 *   4. The caller's thread returns IMMEDIATELY — it does NOT wait for the result.
 *   5. The Runnable executes later on one of the pool's worker threads.
 *
 * This is why NotificationService.notify*() methods return CompletableFuture<Void>:
 * the caller can chain callbacks or await if needed, but in our case OrderService
 * ignores the future (fire-and-forget).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY A CUSTOM EXECUTOR INSTEAD OF THE DEFAULT?
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Spring's default executor for @Async is SimpleAsyncTaskExecutor:
 *   • Creates a NEW thread for EVERY task — no reuse
 *   • No queue — tasks execute immediately or not at all
 *   • No bound — 10,000 simultaneous orders → 10,000 threads → OutOfMemoryError
 *
 * ThreadPoolTaskExecutor:
 *   • Reuses threads from a pool → much lower thread-creation overhead
 *   • Queue absorbs traffic spikes — surplus tasks wait rather than crash
 *   • Bounded — prevents runaway resource usage
 *   • Named threads (order-async-1, order-async-2, ...) appear in stack dumps
 *     and thread monitors, making debugging much easier
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THREAD POOL SIZING — THEORY
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * For CPU-bound tasks (compression, encryption, sorting):
 *   optimal threads ≈ Runtime.getRuntime().availableProcessors()
 *   Adding more threads causes context-switching overhead without more throughput.
 *
 * For I/O-bound tasks (HTTP calls, DB queries, file I/O):
 *   optimal threads ≈ availableProcessors × (1 + waitTime / cpuTime)
 *   Threads spend most of their time waiting, so more threads keep the CPU busy
 *   while others are blocked on I/O.
 *
 * Our notifications are I/O-bound (email API / message queue):
 *   Defaults: core=4, max=16 (adjust per environment via app.async.* properties).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * REJECTION POLICY — WHAT HAPPENS WHEN THE QUEUE IS FULL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * When all threads are busy AND the queue is full, new tasks are "rejected".
 * Available policies (from java.util.concurrent.ThreadPoolExecutor):
 *
 *   AbortPolicy (default)       → throws RejectedExecutionException — task is lost
 *   DiscardPolicy               → silently drops the task — task is lost
 *   DiscardOldestPolicy         → drops the oldest queued task — task is lost
 *   CallerRunsPolicy  ← WE USE THIS
 *                               → the CALLING thread executes the task itself
 *                               → naturally slows down producers (backpressure)
 *                               → no tasks are lost
 *                               → trade-off: the HTTP thread is blocked during the task
 *
 * CallerRunsPolicy is the right choice for notifications because:
 *   - We must never silently lose a notification
 *   - Temporary slowdown on the HTTP thread is acceptable
 *   - It automatically throttles request rate if the pool saturates
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

        // Threads created eagerly and kept alive indefinitely (core threads stay even when idle)
        executor.setCorePoolSize(corePoolSize);

        // Pool grows beyond corePoolSize only when the queue is full.
        // New threads beyond core are idle-terminated after keepAliveSeconds (default 60s).
        executor.setMaxPoolSize(maxPoolSize);

        // Queue capacity: how many tasks can wait before the pool starts rejecting.
        // 500 tasks at ~200ms each = ~100s of buffered notifications at corePoolSize threads.
        executor.setQueueCapacity(queueCapacity);

        executor.setThreadNamePrefix(prefix);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // On shutdown: wait up to 30 s for in-flight tasks to complete
        // so we don't lose notifications during a graceful restart.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Order async executor ready — core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
