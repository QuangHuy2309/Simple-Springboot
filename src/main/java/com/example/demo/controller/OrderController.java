package com.example.demo.controller;

import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.request.UpdateOrderStatusRequest;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order (authenticated user)")
    public OrderResponse createOrder(@AuthenticationPrincipal UserDetails user,
                                     @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(user.getUsername(), request);
    }

    @GetMapping("/my")
    @Operation(summary = "List my own orders")
    public PageResponse<OrderResponse> myOrders(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listMyOrders(user.getUsername(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all orders (ADMIN only)")
    public PageResponse<OrderResponse> allOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listAllOrders(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order detail — owner or ADMIN")
    public OrderResponse getOrder(@AuthenticationPrincipal UserDetails user,
                                  @PathVariable Long id) {
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return orderService.getOrder(user.getUsername(), id, isAdmin);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status — ADMIN advances; owner can only cancel")
    public OrderResponse updateStatus(@AuthenticationPrincipal UserDetails user,
                                      @PathVariable Long id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return orderService.updateStatus(user.getUsername(), id, request.getStatus(), isAdmin);
    }
}
