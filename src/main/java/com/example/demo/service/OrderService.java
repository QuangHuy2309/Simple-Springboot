package com.example.demo.service;

import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> CANCELLABLE = Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final NotificationService notificationService;

    /**
     * Create a new order for the currently authenticated user.
     *
     * @Transactional boundary:
     *   All DB writes (order insert, stock decrement) happen in one transaction.
     *   If any product is out of stock the whole thing rolls back — no partial orders.
     */
    @Transactional
    public OrderResponse createOrder(String username, CreateOrderRequest request) {
        User user = loadUser(username);
        Order order = Order.builder().user(user).note(request.getNote()).build();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            // reserveStock is also @Transactional, but because it is called from within
            // the same proxy-unaware instance, Spring does NOT create a nested transaction.
            // The stock decrement joins this outer transaction — correct behaviour here.
            Product product = productService.reserveStock(itemReq.getProductId(), itemReq.getQuantity());
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        log.info("Order #{} created for user '{}'", saved.getId(), username);

        // Fire-and-forget async notification — does NOT participate in this transaction
        notificationService.notifyNewOrder(saved.getId(), username);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMyOrders(String username, Pageable pageable) {
        User user = loadUser(username);
        return PageResponse.from(
                orderRepository.findByUserId(user.getId(), pageable).map(OrderResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAllOrders(Pageable pageable) {
        return PageResponse.from(orderRepository.findAll(pageable).map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String username, Long id, boolean isAdmin) {
        Order order = loadOrderWithItems(id);
        if (!isAdmin && !order.getUser().getUsername().equals(username)) {
            throw new BusinessException("Access denied to order #" + id, HttpStatus.FORBIDDEN);
        }
        return OrderResponse.from(order);
    }

    /**
     * Update order status.
     *
     * Business rules:
     *   - Only ADMIN can push status forward (PENDING → PROCESSING → SHIPPED → DELIVERED).
     *   - The owner (USER) may only cancel their own order when it is still PENDING or PROCESSING.
     *   - Any other transition is rejected.
     */
    @Transactional
    public OrderResponse updateStatus(String username, Long id, OrderStatus newStatus, boolean isAdmin) {
        Order order = loadOrderWithItems(id);

        if (newStatus == OrderStatus.CANCELLED) {
            if (!isAdmin && !order.getUser().getUsername().equals(username)) {
                throw new BusinessException("You can only cancel your own orders", HttpStatus.FORBIDDEN);
            }
            if (!CANCELLABLE.contains(order.getStatus())) {
                throw new BusinessException(
                        "Cannot cancel order in status: " + order.getStatus(), HttpStatus.CONFLICT);
            }
        } else if (!isAdmin) {
            throw new BusinessException("Only admins can advance order status", HttpStatus.FORBIDDEN);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        log.info("Order #{} status: {} → {} (by '{}')", id, previous, newStatus, username);
        notificationService.notifyOrderStatusChange(id, order.getUser().getUsername(), newStatus);

        return OrderResponse.from(saved);
    }

    private Order loadOrderWithItems(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
