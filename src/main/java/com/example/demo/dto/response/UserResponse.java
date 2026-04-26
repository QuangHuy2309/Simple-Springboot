package com.example.demo.dto.response;

import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static UserResponse from(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .enabled(u.isEnabled())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
