package com.example.demo.service;

import com.example.demo.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Simulates sending notifications (email / push) when order status changes.
 *
 * Key threading concepts demonstrated here:
 *  - @Async: Spring proxies the call and submits it to the "orderTaskExecutor" pool
 *    defined in AsyncConfig. The HTTP request thread returns immediately.
 *  - CompletableFuture: allows callers to chain callbacks or await if needed.
 *    Returning void would also work, but Future gives more flexibility.
 *
 * In a real system this would call an email provider (SES, SendGrid) or
 * push a message onto a queue (Kafka, SQS). We simulate the I/O with sleep.
 */
@Slf4j
@Service
public class NotificationService {

    @Async("orderTaskExecutor")
    public CompletableFuture<Void> notifyOrderStatusChange(Long orderId, String username,
                                                            OrderStatus newStatus) {
        log.info("[{}] Sending notification to '{}' — order #{} is now {}",
                Thread.currentThread().getName(), username, orderId, newStatus);
        try {
            // Simulate I/O latency (e.g., calling an email API)
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[{}] Notification sent for order #{}", Thread.currentThread().getName(), orderId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("orderTaskExecutor")
    public CompletableFuture<Void> notifyNewOrder(Long orderId, String username) {
        log.info("[{}] New order #{} placed by '{}'", Thread.currentThread().getName(), orderId, username);
        return CompletableFuture.completedFuture(null);
    }
}
