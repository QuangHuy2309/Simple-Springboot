package com.example.demo.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Exposes Hibernate's native SessionFactory as a Spring bean (stage profile only).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * HOW SPRING BOOT SETS UP HIBERNATE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * When spring-boot-starter-data-jpa is on the classpath, Spring Boot auto-configures:
 *
 *   1. DataSource (HikariCP connection pool)
 *   2. LocalContainerEntityManagerFactoryBean
 *        → scans @Entity classes and builds the JPA metamodel
 *        → internally creates a Hibernate SessionFactory
 *   3. JpaTransactionManager
 *        → integrates with @Transactional — commits/rolls back at method boundaries
 *   4. Spring Data JPA repositories (proxy beans for each JpaRepository interface)
 *
 * Step 2 is the key: the JPA EntityManagerFactory is *backed by* a Hibernate
 * SessionFactory.  We can unwrap it to get the underlying Hibernate object.
 *
 * WHY UNWRAP INSTEAD OF CONFIGURING SEPARATELY?
 *   If we created a SessionFactory ourselves (via new Configuration().buildSessionFactory()),
 *   it would be disconnected from Spring's transaction management — @Transactional
 *   would stop working for Hibernate sessions.
 *
 *   By unwrapping the one Spring Boot already created, we get:
 *     • The same connection pool (HikariCP)
 *     • The same transaction manager — @Transactional works on Hibernate sessions too
 *     • The same entity mappings (no duplication)
 *     • All Spring Boot properties (hibernate.dialect, etc.) still apply
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * EntityManagerFactory vs SessionFactory — BOTH POINT TO THE SAME THING
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   EntityManagerFactory  (JPA interface)
 *         │
 *         └── implemented by ──▶  SessionFactoryImpl  (Hibernate class)
 *                                       │
 *                                       └── is-a ──▶  SessionFactory  (Hibernate interface)
 *
 *   So emf.unwrap(SessionFactory.class) is a safe downcast — no data is copied;
 *   we just get the same object through a narrower type that exposes Hibernate-specific methods.
 */
@Configuration
@Profile("stage")   // Only active when running with -Dspring.profiles.active=stage
public class HibernateConfig {

    /**
     * Exposes the Hibernate SessionFactory that Spring Boot already created internally.
     *
     * The EntityManagerFactory parameter is injected by Spring from the
     * LocalContainerEntityManagerFactoryBean configured by spring-boot-starter-data-jpa.
     */
    @Bean
    public SessionFactory sessionFactory(EntityManagerFactory emf) {
        return emf.unwrap(SessionFactory.class);
    }
}
