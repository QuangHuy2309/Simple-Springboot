package com.example.demo.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory blocklist for revoked JWT tokens.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE STATELESS JWT LOGOUT PROBLEM
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * JWT is designed to be stateless — the server stores NO session data.
 * A token is valid as long as its signature is correct and it has not expired.
 * This means you CANNOT "delete" a token on the server side the way you can
 * delete a session entry from a database.
 *
 * The standard solution: a TOKEN BLOCKLIST (also called a denylist).
 *   On logout → add the token (or its JTI claim) to the blocklist with its expiry time.
 *   On every authenticated request → check the blocklist before accepting the token.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * IN-MEMORY vs REDIS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * This implementation uses a ConcurrentHashMap (in-memory).
 *
 * Pros:
 *   • Zero infrastructure dependencies — works out of the box
 *   • Sub-microsecond lookup (just a hash map get)
 *
 * Cons:
 *   • NOT shared across multiple application instances (multi-pod deployments)
 *     → If you log out on pod A, pod B still accepts the token
 *   • Lost on application restart
 *
 * For production with multiple instances, replace this with Redis:
 *   redisTemplate.opsForValue().set(token, "revoked", Duration.ofMillis(expiresIn));
 *   // Redis TTL auto-cleans expired entries — no @Scheduled cleanup needed
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY ConcurrentHashMap AND NOT HashMap?
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Multiple threads (HTTP request threads + the cleanup scheduler) read and write
 * this map concurrently.  HashMap is NOT thread-safe — concurrent modification
 * can cause infinite loops or data corruption.  ConcurrentHashMap uses internal
 * bucket-level locking (striped locking) so reads never block writes and writes
 * only lock one bucket at a time.
 */
@Slf4j
@Service
public class TokenBlocklistService {

    // Maps token string → expiry instant.  We store the expiry so the cleanup
    // job can remove entries that are already expired (they can no longer be used anyway).
    private final ConcurrentHashMap<String, Instant> blocklist = new ConcurrentHashMap<>();

    /**
     * Adds a token to the blocklist until it naturally expires.
     *
     * @param token    raw JWT string from the Authorization header
     * @param expiryMs token's expiry in milliseconds from epoch
     *                 (read from JwtTokenProvider so cleanup aligns with actual expiry)
     */
    public void revoke(String token, long expiryMs) {
        blocklist.put(token, Instant.ofEpochMilli(expiryMs));
        log.debug("Token revoked, blocklist size={}", blocklist.size());
    }

    /**
     * Returns true if the token has been explicitly revoked via logout.
     * Called by JwtAuthenticationFilter on every authenticated request.
     */
    public boolean isRevoked(String token) {
        return blocklist.containsKey(token);
    }

    /**
     * Periodically purges expired tokens from the blocklist.
     *
     * WHY IS THIS NEEDED?
     *   Once a token's natural expiry passes, it will be rejected by JwtTokenProvider.validateToken()
     *   regardless of the blocklist.  But the blocklist entry would accumulate indefinitely
     *   without this cleanup — causing a memory leak in a long-running application.
     *
     * @Scheduled runs this on a fixed interval using Spring's task scheduler.
     * fixedDelay = 3_600_000 ms = 1 hour:  runs 1 hour AFTER the previous run completes.
     * For a 24-hour token lifetime, hourly cleanup means the map holds at most 24× the
     * peak logout rate entries before they start being removed.
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        int before = blocklist.size();
        Instant now = Instant.now();
        blocklist.entrySet().removeIf(e -> e.getValue().isBefore(now));
        int removed = before - blocklist.size();
        if (removed > 0) {
            log.info("Token blocklist purge: removed {} expired entries, {} remain", removed, blocklist.size());
        }
    }
}
