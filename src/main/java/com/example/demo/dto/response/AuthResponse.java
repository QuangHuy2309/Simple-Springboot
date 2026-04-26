package com.example.demo.dto.response;

import com.example.demo.enums.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String tokenType;
    private String username;
    private UserRole role;
    private long expiresIn;   // millis
}
