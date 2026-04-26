package com.example.demo.repository.impl;

import com.example.demo.entity.Order;
import com.example.demo.repository.OrderDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * OrderDao implementation that uses Hibernate's native Session API (active on the "stage" profile).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * JPA vs HIBERNATE NATIVE — THE KEY DIFFERENCE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * JPA (EntityManager / Spring Data):
 *   • Standard Java EE / Jakarta EE specification — vendor-neutral
 *   • EntityManager is the JPA abstraction over the underlying ORM
 *   • You write JPQL (Java Persistence Query Language), which is ORM-portable
 *   • Spring Data JPA further wraps EntityManager so you rarely touch it directly
 *
 * Hibernate Session (native):
 *   • Hibernate's own API, richer than JPA's EntityManager
 *   • Session = EntityManager + Hibernate-specific extensions:
 *       - session.createQuery()       → HQL (Hibernate Query Language, superset of JPQL)
 *       - session.createNativeQuery() → raw SQL with mapping back to entities
 *       - session.enableFilter()      → Hibernate Filter (global soft-delete, tenant isolation)
 *       - session.setReadOnly()       → hint to skip dirty-checking for a specific object
 *       - SessionFactory.openStatelessSession() → no 1st-level cache, great for bulk ETL
 *
 * HOW WE GET THE SESSION:
 *   We inject SessionFactory (configured in HibernateConfig.java).
 *   Within an active @Transactional context, sessionFactory.getCurrentSession()
 *   returns the Session already bound to that transaction — no need to open/close manually.
 *   Spring's PlatformTransactionManager handles commit and rollback.
 *
 * HOW PAGINATION WORKS WITH HIBERNATE NATIVE:
 *   Spring Data's Pageable contains offset and limit.  We call:
 *     query.setFirstResult((int) pageable.getOffset())   → OFFSET clause
 *     query.setMaxResults(pageable.getPageSize())         → LIMIT clause
 *   A separate COUNT query gives us totalElements for the Page metadata.
 *
 * WHEN TO USE THIS APPROACH IN REAL PROJECTS:
 *   ✓ You need Hibernate Filters (row-level security, soft-delete)
 *   ✓ You're doing bulk inserts with StatelessSession (no 1st-level cache overhead)
 *   ✓ You need fine-grained cache region control (2nd-level cache)
 *   ✓ You want to use Hibernate Interceptors or EventListeners
 *   ✗ Simple CRUD — Spring Data JPA is faster to write and just as efficient
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Repository
@Profile("stage")
@RequiredArgsConstructor
public class HibernateOrderDaoImpl implements OrderDao {

    // SessionFactory is a heavyweight, thread-safe, application-scoped object.
    // It is created once at startup (configured in HibernateConfig) and reused.
    // Session (individual connection/cache unit) is created per transaction.
    private final SessionFactory sessionFactory;

    /**
     * Load an order together with its items and products in a single SQL query.
     *
     * WHY JOIN FETCH?
     *   Without it, accessing order.getItems() would fire a second SQL (lazy load).
     *   Then accessing item.getProduct() would fire N more SQLs — the classic N+1 problem.
     *   JOIN FETCH collapses all of that into one query at the cost of a larger result set.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithItems(Long id) {
        Session session = sessionFactory.getCurrentSession();

        // HQL (Hibernate Query Language): looks like JPQL but uses entity/field names,
        // not table/column names.  Hibernate translates it to the correct SQL dialect.
        Order order = session.createQuery(
                        "FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id",
                        Order.class)
                .setParameter("id", id)
                // uniqueResult() throws if multiple rows match — safe here because id is PK.
                .uniqueResult();

        return Optional.ofNullable(order);
    }

    /**
     * Paginated query for a specific user's orders.
     *
     * Hibernate does NOT have a built-in equivalent of Spring Data's Page<T>.
     * We build it manually:  data query (with LIMIT/OFFSET)  +  count query.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Order> findByUserId(Long userId, Pageable pageable) {
        Session session = sessionFactory.getCurrentSession();

        // Data query — ORDER BY ensures consistent pagination across pages
        List<Order> orders = session.createQuery(
                        "FROM Order o WHERE o.user.id = :userId ORDER BY o.createdAt DESC",
                        Order.class)
                .setParameter("userId", userId)
                // setFirstResult maps to SQL OFFSET (how many rows to skip)
                .setFirstResult((int) pageable.getOffset())
                // setMaxResults maps to SQL LIMIT (how many rows to return)
                .setMaxResults(pageable.getPageSize())
                .list();

        // Count query — required for Page.getTotalPages() and Page.getTotalElements()
        Long total = session.createQuery(
                        "SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .uniqueResult();

        return new PageImpl<>(orders, pageable, total != null ? total : 0L);
    }

    /**
     * Admin query — all orders, paginated, newest first.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        Session session = sessionFactory.getCurrentSession();

        List<Order> orders = session.createQuery(
                        "FROM Order o ORDER BY o.createdAt DESC", Order.class)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .list();

        Long total = session.createQuery("SELECT COUNT(o) FROM Order o", Long.class)
                .uniqueResult();

        return new PageImpl<>(orders, pageable, total != null ? total : 0L);
    }

    /**
     * Persist or merge an Order.
     *
     * PERSIST vs MERGE (the JPA/Hibernate equivalent of INSERT vs UPDATE):
     *   session.persist(entity) — entity must be NEW (id == null).  Schedules INSERT.
     *   session.merge(entity)   — works for both new and detached entities.
     *                             For a new entity it acts like persist.
     *                             For an existing entity it copies state into the managed copy.
     *
     * We use merge() because it handles both create and update paths uniformly,
     * matching the behaviour of JpaRepository.save().
     *
     * session.flush() forces Hibernate to synchronise its in-memory state with the DB
     * immediately (generates the SQL now rather than waiting for transaction commit).
     * This is necessary so that the returned entity has the DB-generated id populated
     * before the caller maps it to a response DTO.
     */
    @Override
    @Transactional
    public Order save(Order order) {
        Session session = sessionFactory.getCurrentSession();
        Order managed = session.merge(order);
        // Flush so the generated PK is visible on the returned object.
        session.flush();
        log.debug("Saved order id={}, status={}", managed.getId(), managed.getStatus());
        return managed;
    }
}
