package com.example.demo.enums;

/**
 * Order lifecycle:  PENDING → PROCESSING → SHIPPED → DELIVERED
 *                                        ↘ CANCELLED  (from PENDING or PROCESSING)
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
