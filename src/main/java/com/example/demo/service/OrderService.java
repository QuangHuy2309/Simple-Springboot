package com.example.demo.service;

import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.OrderDao;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Core business logic for Order management.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DEPENDENCY INJECTION — OrderDao, not a concrete class
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This service depends on OrderDao (an interface), not on JpaOrderDaoImpl or
 * HibernateOrderDaoImpl directly.  Spring injects the correct implementation
 * at startup based on the active profile:
 *
 *   --spring.profiles.active=local  →  JpaOrderDaoImpl  (Spring Data JPA)
 *   --spring.profiles.active=stage  →  HibernateOrderDaoImpl  (Hibernate Session)
 *
 * This is the Dependency Inversion Principle: high-level modules (this service)
 * should not depend on low-level modules (a specific DB library).  Both depend
 * on an abstraction (OrderDao).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * @TRANSACTIONAL STRATEGY
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Rule of thumb used throughout this service:
 *   • Read-only methods    → @Transactional(readOnly = true)
 *   • Write methods        → @Transactional  (readOnly defaults to false)
 *
 * Why readOnly = true on queries?
 *   1. Hibernate skips dirty-checking (comparing entity snapshots to detect changes)
 *      → less CPU work and lower memory pressure
 *   2. The DB driver / JDBC pool can route the query to a read replica
 *   3. Spring's JDBC infrastructure may skip flushing the EntityManager before the query
 *
 * Why @Transactional at all on queries (not just writes)?
 *   • Ensures all lazy-loaded associations are fetched within the same DB connection
 *   • Prevents "no session" LazyInitializationException when accessing child collections
 *   • Gives us a consistent snapshot — two reads within the same transaction see
 *     the same data even if another transaction commits between them
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TRANSACTION BOUNDARY AND ASYNC NOTIFICATION
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The @Async notification call in createOrder / updateStatus is deliberately placed
 * AFTER the save, but the async task does NOT join this transaction.  Here is why:
 *
 *   Timeline:
 *     [HTTP thread] ──▶ createOrder() ──▶ orderDao.save() ──▶ tx COMMIT
 *                                                           ──▶ notifyNewOrder() submitted to pool
 *     [order-async-1] ──▶ log / send email  (no DB transaction)
 *
 *   If the notification were inside the same transaction and it failed, the whole
 *   order would roll back — clearly wrong.  Notifications are best-effort I/O;
 *   they must not affect the business transaction's outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    // Only PENDING and PROCESSING orders can be cancelled.
    // SHIPPED/DELIVERED orders are already with the carrier or the customer.
    private static final Set<OrderStatus> CANCELLABLE = Set.of(OrderStatus.PENDING, OrderStatus.PROCESSING);

    // Injected as OrderDao — Spring picks JpaOrderDaoImpl (local) or HibernateOrderDaoImpl (stage)
    private final OrderDao orderDao;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final NotificationService notificationService;

    /**
     * Place a new order for the authenticated user.
     *
     * ATOMICITY:  All DB writes happen in one transaction.
     *   - Stock is decremented for every item (via ProductService.reserveStock)
     *   - The order and its items are inserted
     *   - If any product is out of stock → entire transaction rolls back
     *   → No half-created orders; no phantom stock decrements.
     *
     * NOTE on calling ProductService.reserveStock() from here:
     *   ProductService.reserveStock() is annotated @Transactional (REQUIRED propagation).
     *   Because it is called through a Spring proxy (productService is injected), the
     *   call DOES go through the proxy and Spring sees the @Transactional annotation.
     *   The default PROPAGATION.REQUIRED means "join the existing transaction if one
     *   is active" — which it is (this method's transaction).  So the stock decrement
     *   joins our transaction: one commit for both the order and the stock change.
     */
    @Transactional
    public OrderResponse createOrder(String username, CreateOrderRequest request) {
        User user = loadUser(username);
        Order order = Order.builder().user(user).note(request.getNote()).build();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productService.reserveStock(itemReq.getProductId(), itemReq.getQuantity());
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    // Snapshot the price at order time — never recalculate from product later.
                    // Products can be repriced; the customer paid the price shown at checkout.
                    .unitPrice(product.getPrice())
                    .build();
            order.addItem(item);
        }

        Order saved = orderDao.save(order);
        log.info("Order #{} created for user '{}'", saved.getId(), username);

        // Fire-and-forget — runs on a separate thread from the orderTaskExecutor pool.
        // Does NOT participate in this transaction; if it fails, the order is still saved.
        notificationService.notifyNewOrder(saved.getId(), username);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMyOrders(String username, Pageable pageable) {
        User user = loadUser(username);
        return PageResponse.from(
                orderDao.findByUserId(user.getId(), pageable).map(OrderResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAllOrders(Pageable pageable) {
        return PageResponse.from(orderDao.findAll(pageable).map(OrderResponse::from));
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
     * Advance or cancel an order's status.
     *
     * STATUS MACHINE:
     *   PENDING ──[ADMIN]──▶ PROCESSING ──[ADMIN]──▶ SHIPPED ──[ADMIN]──▶ DELIVERED
     *      │                     │
     *      └──[owner/ADMIN]──▶ CANCELLED   (only from PENDING or PROCESSING)
     *
     * Access control is enforced here in the service, not just in the controller,
     * because business rules should not live in the HTTP layer — they must hold
     * regardless of which entry point (REST, message queue, scheduled job) calls this.
     */
    @Transactional
    public OrderResponse updateStatus(String username, Long id, OrderStatus newStatus, boolean isAdmin) {
        Order order = loadOrderWithItems(id);

        if (newStatus == OrderStatus.CANCELLED) {
            // Non-admin users can cancel, but only their own order
            if (!isAdmin && !order.getUser().getUsername().equals(username)) {
                throw new BusinessException("You can only cancel your own orders", HttpStatus.FORBIDDEN);
            }
            if (!CANCELLABLE.contains(order.getStatus())) {
                throw new BusinessException(
                        "Cannot cancel an order in status: " + order.getStatus(), HttpStatus.CONFLICT);
            }
        } else if (!isAdmin) {
            // All forward transitions (PROCESSING / SHIPPED / DELIVERED) are ADMIN-only
            throw new BusinessException("Only admins can advance order status", HttpStatus.FORBIDDEN);
        }

        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        Order saved = orderDao.save(order);

        log.info("Order #{} status: {} → {} (by '{}')", id, previous, newStatus, username);

        // Notify the order owner asynchronously — does not block the HTTP response
        notificationService.notifyOrderStatusChange(id, order.getUser().getUsername(), newStatus);

        return OrderResponse.from(saved);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private Order loadOrderWithItems(Long id) {
        // JOIN FETCH in the DAO prevents N+1 when OrderResponse.from() accesses items
        return orderDao.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
