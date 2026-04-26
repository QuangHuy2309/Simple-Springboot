package com.example.demo.repository.impl;

import com.example.demo.entity.Order;
import com.example.demo.repository.OrderDao;
import com.example.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * OrderDao implementation that uses Spring Data JPA (active on the "local" profile).
 *
 * WHAT IS SPRING DATA JPA?
 *   Spring Data JPA is a layer on top of JPA (Java Persistence API), which is itself
 *   a specification that Hibernate implements.  The inheritance chain looks like this:
 *
 *     Your code
 *       └─ Spring Data JPA   (auto-generates repository implementations at startup)
 *            └─ JPA (EntityManager / JPQL)
 *                 └─ Hibernate ORM   (translates JPQL → SQL, manages 1st and 2nd level cache)
 *                      └─ JDBC
 *                           └─ Database
 *
 * WHAT SPRING DATA JPA DOES FOR YOU:
 *   • Generates SQL from method names (findByUserId → SELECT ... WHERE user_id = ?)
 *   • Provides pagination and sorting via Pageable without extra code
 *   • Manages the EntityManager lifecycle per transaction
 *   • Handles flush/clear automatically at transaction boundaries
 *
 * WHY THIS WORKS ON LOCAL AND NOT STAGE:
 *   @Profile("local") tells Spring to only create this bean when the active profile
 *   contains "local".  In stage, HibernateOrderDaoImpl is created instead.
 *   Both implement OrderDao, so OrderService never knows which one it gets.
 *
 * TRADE-OFF vs Hibernate native (see HibernateOrderDaoImpl for the other side):
 *   Pro:  Much less boilerplate; derived queries are auto-validated at startup.
 *   Con:  Less control — you cannot easily plug in Hibernate Interceptors, use
 *         StatelessSession, or access Hibernate-specific cache APIs.
 */
@Repository
@Profile("local")
@RequiredArgsConstructor
public class JpaOrderDaoImpl implements OrderDao {

    // Spring Data JPA generates the implementation of this interface at startup.
    // The actual SQL queries come from method names and the @Query annotation in OrderRepository.
    private final OrderRepository orderRepository;

    @Override
    public Optional<Order> findByIdWithItems(Long id) {
        // Delegates to the @Query(JOIN FETCH) defined in OrderRepository —
        // avoids N+1: loads order + all items + all products in one SQL query.
        return orderRepository.findByIdWithItems(id);
    }

    @Override
    public Page<Order> findByUserId(Long userId, Pageable pageable) {
        // Spring Data translates this to:
        //   SELECT * FROM orders WHERE user_id = ? ORDER BY ... LIMIT ? OFFSET ?
        // plus a separate COUNT(*) query for totalElements.
        return orderRepository.findByUserId(userId, pageable);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Override
    public Order save(Order order) {
        // JPA EntityManager decides INSERT vs UPDATE by checking if id is null.
        // After save, the returned entity has the DB-generated id populated.
        return orderRepository.save(order);
    }
}
