package com.example.demo.service;

import com.example.demo.dto.request.CreateUserRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.UserResponse;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Admin-only user creation — role is set to whatever the admin specifies.
     *
     * Separated from AuthService.register() intentionally:
     *   register() is the public self-sign-up path and hardcodes role = USER.
     *   createUser() is the admin management path and requires an explicit role.
     * Keeping them apart means there is no branching on "who is the caller" —
     * the distinction is enforced at the routing level (different endpoints,
     * different @PreAuthorize rules).
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already taken", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())   // role comes from the admin's request body
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }
}
