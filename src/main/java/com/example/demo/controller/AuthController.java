package com.example.demo.controller;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.request.RegisterRequest;
import com.example.demo.dto.response.AuthResponse;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, and logout — login/register require no token")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login with username + password, returns JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user (role = USER)")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Logout — adds the current token to the blocklist.
     *
     * The endpoint requires authentication (the token must be present and valid).
     * After a successful call, the same token will be rejected on all subsequent requests.
     *
     * CLIENT RESPONSIBILITY:
     *   After calling this endpoint, the client should discard the token from storage
     *   (localStorage, memory, etc.).  The server-side blocklist is a safety net —
     *   not a substitute for the client cleaning up its own state.
     *
     * HTTP 204 No Content — "the action was performed; there is no body to return".
     * This is the idiomatic response for a successful logout.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout — revokes the current JWT (requires a valid token)")
    public void logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            authService.logout(token);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
