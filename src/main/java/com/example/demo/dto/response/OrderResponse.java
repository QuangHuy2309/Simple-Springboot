package com.example.demo.dto.response;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Long userId;
    private String username;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String note;
    private List<ItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class ItemResponse {
        private Long productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    public static OrderResponse from(Order o) {
        List<ItemResponse> items = o.getItems().stream()
                .map(OrderResponse::toItemResponse)
                .toList();

        return OrderResponse.builder()
                .id(o.getId())
                .userId(o.getUser().getId())
                .username(o.getUser().getUsername())
                .status(o.getStatus())
                .totalAmount(o.getTotalAmount())
                .note(o.getNote())
                .items(items)
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private static ItemResponse toItemResponse(OrderItem i) {
        return ItemResponse.builder()
                .productId(i.getProduct().getId())
                .productName(i.getProduct().getName())
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .subtotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .build();
    }
}
