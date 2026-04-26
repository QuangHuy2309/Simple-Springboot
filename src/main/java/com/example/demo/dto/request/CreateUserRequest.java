package com.example.demo.dto.request;

import com.example.demo.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Used exclusively by ADMIN to create a user with an explicit role.
 * Self-registration (public) uses RegisterRequest instead, which always assigns USER.
 */
@Data
public class CreateUserRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    // ADMIN must explicitly choose the role — no default, so a missing value is a validation error.
    @NotNull(message = "role is required (USER or ADMIN)")
    private UserRole role;
}
