package com.example.demo.controller;

import com.example.demo.dto.request.CreateUserRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")   // all endpoints in this controller require ADMIN
@Tag(name = "Users", description = "User management — all endpoints require ADMIN role")
public class UserController {

    private final UserService userService;

    /**
     * Admin creates a user and explicitly assigns a role (USER or ADMIN).
     *
     * Contrast with POST /api/auth/register:
     *   - /auth/register  → public, no token required, role always USER
     *   - /api/users      → ADMIN token required, role chosen by admin
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user with an explicit role (ADMIN only)")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping
    @Operation(summary = "List all users, paginated (ADMIN only)")
    public PageResponse<UserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.listUsers(PageRequest.of(page, size, Sort.by("username")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single user by ID (ADMIN only)")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }
}
