package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication filter — runs once per HTTP request.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY OncePerRequestFilter?
 * ─────────────────────────────────────────────────────────────────────────────
 * In a Servlet container, a request can be forwarded internally (e.g., error pages,
 * async dispatch).  A plain Filter would run again on every forward/include.
 * OncePerRequestFilter adds a request attribute after the first execution and
 * skips subsequent calls within the same request — prevents double-authentication.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FILTER EXECUTION ORDER
 * ─────────────────────────────────────────────────────────────────────────────
 * Registered in SecurityConfig via:
 *   .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
 *
 * So the chain looks like:
 *   ... → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ...
 *
 * Our filter runs first.  If the JWT is valid and not revoked:
 *   → SecurityContextHolder is populated
 *   → The remaining filters and the controller see an authenticated user
 *
 * If there is no JWT (public endpoint) or it is invalid:
 *   → SecurityContextHolder stays empty
 *   → Spring Security's authorization rules reject the request if the endpoint requires auth
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * BLOCKLIST CHECK (LOGOUT SUPPORT)
 * ─────────────────────────────────────────────────────────────────────────────
 * A revoked token (logged-out user) still has a valid cryptographic signature.
 * JwtTokenProvider.validateToken() alone is not enough — it only checks signature + expiry.
 * We additionally check TokenBlocklistService.isRevoked() to reject logged-out tokens.
 * Order: signature check first (cheap), then blocklist lookup (slightly more expensive).
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final TokenBlocklistService tokenBlocklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)
                && tokenProvider.validateToken(token)           // 1. valid signature + not expired
                && !tokenBlocklistService.isRevoked(token)) {   // 2. not revoked via logout

            String username = tokenProvider.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Build a fully authenticated token and set it in the SecurityContext.
            // From this point forward, SecurityContextHolder.getContext().getAuthentication()
            // returns this object — controllers can access the principal via @AuthenticationPrincipal.
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT from the "Authorization: Bearer <token>" header.
     * Returns null if the header is absent or malformed.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
