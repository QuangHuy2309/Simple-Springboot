package com.example.demo.config;

import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.JwtTokenProvider;
import com.example.demo.security.TokenBlocklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central security configuration for the application.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * AUTHENTICATION vs AUTHORISATION — TWO SEPARATE CONCERNS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Authentication ("who are you?"):
 *   • /api/auth/login:   user submits username + password → we return a JWT
 *   • Every subsequent request: JwtAuthenticationFilter reads the JWT from the
 *     Authorization header, validates its signature and expiry, and sets the
 *     authenticated principal in SecurityContextHolder
 *
 * Authorisation ("what are you allowed to do?"):
 *   • URL-level rules:  defined in securityFilterChain() below
 *   • Method-level rules: @PreAuthorize("hasRole('ADMIN')") annotations on
 *     service/controller methods, enabled by @EnableMethodSecurity
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY JWT AND NOT SESSIONS?
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Traditional session-based auth:
 *   Browser sends session cookie → server looks up session in memory/Redis
 *   Problem: horizontal scaling requires shared session storage (Redis cluster, sticky sessions)
 *
 * JWT (JSON Web Token) — stateless auth:
 *   Client stores the token (usually localStorage or memory).
 *   Every request carries the token in the Authorization header.
 *   The server validates the token's cryptographic signature — NO database lookup needed.
 *
 *   JWT structure:   HEADER.PAYLOAD.SIGNATURE
 *     Header:  {"alg":"HS256","typ":"JWT"}
 *     Payload: {"sub":"alice","iat":..., "exp":...}    ← claims (not encrypted — base64 only)
 *     Signature: HMAC-SHA256(base64(header)+"."+base64(payload), secretKey)
 *
 *   The server re-computes the signature on every request.  If it matches and the
 *   token is not expired, the user is authentic — no session table, no shared memory.
 *
 * Trade-off:
 *   Con: A JWT cannot be invalidated before it expires (no logout in true stateless mode).
 *   Mitigation: short expiry (24 h here) + a token blocklist in Redis for logout if needed.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SPRING SECURITY FILTER CHAIN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Every HTTP request passes through a chain of filters in order:
 *   ... → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ...
 *                                       (we slot our filter BEFORE this one)
 *
 * Our filter runs first, extracts the JWT, and populates SecurityContextHolder.
 * By the time the request reaches the controller, Spring Security already knows who the user is.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * @EnableMethodSecurity — SECOND LAYER OF DEFENCE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * URL rules in securityFilterChain() provide coarse-grained access control.
 * @PreAuthorize on individual methods adds fine-grained control:
 *
 *   @PreAuthorize("hasRole('ADMIN')")
 *   public ProductResponse create(...) { ... }
 *
 * Spring AOP wraps the method in a proxy.  If the caller lacks ROLE_ADMIN,
 * an AccessDeniedException is thrown before the method body executes.
 * This works even if the method is called from a message queue consumer,
 * a scheduled job, or any other entry point — not just REST controllers.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final TokenBlocklistService tokenBlocklistService;

    /**
     * BCrypt is the recommended password-hashing algorithm.
     *
     * WHY NOT MD5 OR SHA-256?
     *   MD5/SHA-256 are fast hash functions — an attacker can compute billions per second.
     *   BCrypt is intentionally slow (cost factor = 10 by default → ~100ms per hash).
     *   It also includes a random salt, so two users with the same password get different hashes.
     *   This makes rainbow-table and brute-force attacks impractical.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService, tokenBlocklistService);
    }

    /**
     * Wires together UserDetailsService and PasswordEncoder.
     *
     * DaoAuthenticationProvider is the component that Spring Security calls during login:
     *   1. Load UserDetails from the DB (via UserDetailsServiceImpl)
     *   2. Compare the submitted password to the stored BCrypt hash (via PasswordEncoder)
     *   3. If they match, return an authenticated token → AuthService creates the JWT
     *
     * Spring Security 7 (Boot 4) changed the constructor to require UserDetailsService
     * as an argument instead of using setUserDetailsService() — it enforces that the
     * dependency is always provided (fail-fast).
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager is the entry point for programmatic authentication.
     * AuthService calls authenticationManager.authenticate(token) during login.
     * Spring Boot's AuthenticationConfiguration auto-wires it from the registered providers.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * The main security policy — defines which URLs are public and which require auth.
     *
     * Request matching is evaluated TOP TO BOTTOM; the first matching rule wins.
     * Order matters: more specific rules must come before more general ones.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection is for browser-based session cookies.
            // Our API is stateless (JWT in Authorization header), so CSRF is irrelevant.
            .csrf(AbstractHttpConfigurer::disable)

            // Tell Spring Security not to create or use HTTP sessions.
            // Every request is self-contained — authentication comes from the JWT only.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Auth endpoints are always public — users need to log in first
                .requestMatchers("/api/auth/**").permitAll()

                // Swagger UI is public so developers can explore the API without logging in
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                // H2 console is only reachable in local profile (no DB listener in stage)
                .requestMatchers("/h2-console/**").permitAll()

                // Health check for load balancers / Kubernetes liveness probes
                .requestMatchers("/actuator/health").permitAll()

                // Catalog browsing is public; catalog management requires ADMIN
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers("/api/products/**").hasRole("ADMIN")

                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // Allow H2's web console to render inside an <iframe> from the same origin.
            // By default Spring Security sends X-Frame-Options: DENY, which blocks iframes.
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            .authenticationProvider(authenticationProvider())

            // Place our JWT filter BEFORE the built-in username/password filter.
            // This ensures JWT authentication runs on every request, even those
            // that would normally bypass the username/password form-login flow.
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
