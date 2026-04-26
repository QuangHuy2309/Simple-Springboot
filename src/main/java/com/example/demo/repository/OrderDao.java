package com.example.demo.repository;

import com.example.demo.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Data-access abstraction for Order persistence.
 *
 * WHY an interface here instead of injecting OrderRepository (JpaRepository) directly?
 *
 *   Spring Boot auto-configures Spring Data JPA repositories by default, which is
 *   perfect for most use-cases.  However, for the *stage* profile we want to
 *   demonstrate Hibernate's native Session API — lower-level control over the SQL
 *   dialect, batch fetching, and cache regions.
 *
 *   By programming to this interface, OrderService stays identical across profiles.
 *   Spring injects the correct implementation based on which profile is active:
 *
 *     local  →  JpaOrderDaoImpl   (delegates to Spring Data JPA / OrderRepository)
 *     stage  →  HibernateOrderDaoImpl  (uses SessionFactory directly)
 *
 *   This is the Strategy pattern applied to the persistence layer, and it also
 *   demonstrates how @Profile enables runtime-swappable implementations.
 *
 * JPA vs Hibernate native — when to choose each:
 *   • JPA (Spring Data):  fast to write, covers 90 % of CRUD, portable across ORM vendors.
 *   • Hibernate native:  needed when you want StatelessSession (no 1st-level cache),
 *     multi-tenancy, custom Interceptors, native SQL with result-set mapping, or
 *     Hibernate-specific batch-insert optimisations.
 */
public interface OrderDao {

    Optional<Order> findByIdWithItems(Long id);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findAll(Pageable pageable);

    Order save(Order order);
}
