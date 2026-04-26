package com.example.demo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates, signs, and validates JSON Web Tokens (JWT).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * JWT ANATOMY
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A JWT has three Base64Url-encoded parts separated by dots:
 *
 *   HEADER . PAYLOAD . SIGNATURE
 *
 * Header (algorithm declaration):
 *   {"alg":"HS256","typ":"JWT"}
 *
 * Payload (claims — NOT encrypted, just encoded):
 *   {
 *     "sub": "alice",        ← subject (username)
 *     "iat": 1714000000,     ← issued at (epoch seconds)
 *     "exp": 1714086400      ← expiration (epoch seconds)
 *   }
 *   ⚠ Anyone can decode the payload with base64.  Never put passwords or PII here.
 *
 * Signature:
 *   HMAC-SHA256(base64Url(header) + "." + base64Url(payload), secretKey)
 *   The signature proves the token was issued by us and was not tampered with.
 *   An attacker who changes the payload (e.g., username: "admin") cannot
 *   produce a valid signature without knowing the secret key.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ALGORITHM: HS256
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * HS256 = HMAC + SHA-256 (symmetric — same key signs and verifies).
 *   Requires: key ≥ 256 bits (32 bytes).  Our secret is configured in application.yml.
 *
 * Alternative: RS256 (asymmetric — private key signs, public key verifies).
 *   Used when the token issuer and the verifier are different services.
 *   HS256 is simpler and sufficient when a single service both issues and verifies tokens.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * SECRET KEY MANAGEMENT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The secret is read from app.jwt.secret in application.yml.
 * For production (stage), override it with an environment variable:
 *   export APP_JWT_SECRET=<random 32+ char string>
 * Spring Boot's relaxed binding maps APP_JWT_SECRET → app.jwt.secret automatically.
 *
 * Keys.hmacShaKeyFor() converts the raw bytes into a SecretKey object and
 * validates that the key length meets the HS256 minimum (256 bits).
 * This check happens at startup — a too-short key causes a fatal error, not silent failure.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Convert the configured string to a cryptographic key.
        // This also validates the minimum key length at startup.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Generate a token for an authenticated principal (called after login). */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return buildToken(userDetails.getUsername());
    }

    /** Generate a token directly from a username string (called after registration). */
    public String generateToken(String username) {
        return buildToken(username);
    }

    private String buildToken(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)           // "sub" claim — who this token is for
                .issuedAt(now)              // "iat" claim — when it was created
                .expiration(new Date(now.getTime() + expirationMs))  // "exp" claim
                .signWith(secretKey)        // signs with HS256 using our secret key
                .compact();                 // serialises to header.payload.signature string
    }

    /** Extracts the username ("sub" claim) from a token. */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getPayload().getSubject();
    }

    /**
     * Returns true if the token has a valid signature and is not expired.
     *
     * Does NOT check the blocklist — that check happens in JwtAuthenticationFilter
     * because the filter has access to the TokenBlocklistService.
     * Keeping validation and blocklist checks separate means this method
     * remains independently testable and reusable.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // JwtException covers: ExpiredJwtException, MalformedJwtException,
            // SignatureException, UnsupportedJwtException — all mean "reject this token"
            log.warn("Invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    /** Returns the configured token lifetime in milliseconds. */
    public long getExpirationMs() {
        return expirationMs;
    }

    /** Returns the absolute expiry timestamp (epoch millis) embedded inside the token. */
    public long getExpirationTimeFromToken(String token) {
        return parseClaims(token).getPayload().getExpiration().getTime();
    }

    private Jws<Claims> parseClaims(String token) {
        // Jwts.parser() uses the provided key to verify the signature.
        // If the signature is wrong, the key is different, or the token is expired,
        // a JwtException is thrown — caught in validateToken().
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
    }
}
